/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.text.SimpleDateFormat;
import java.util.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.extension.MediaExtensionProvider;
import org.jitsi.jirecon.task.*;
import org.jitsi.jirecon.utils.JireconConfigurationKey;
import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.util.Logger;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.provider.ProviderManager;

/**
 * An implementation of <tt>Jirecon</tt>. The manager of <tt>JireconTask</tt>,
 * each <tt>JireconTask</tt> represents a recording task for specified
 * Jitsi-meeting. <tt>JireconImpl</tt> is responsible for create and stop those
 * tasks.
 * 
 * @author lishunyang
 * @see Jirecon
 * @see JireconTask
 * 
 */
public class JireconImpl
    implements Jirecon, JireconEventListener
{
    /**
     * List of <tt>JireconEventListener</tt>, if something important happen,
     * they will be notified.
     */
    private List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    /**
     * An instance of <tt>XMPPConnection</tt>, it is shared with every
     * <tt>JireconTask</tt>
     */
    private XMPPConnection connection;

    /**
     * Active <tt>JireconTask</tt>, map between Jitsi-meeting jid and task.
     */
    private Map<String, JireconTask> jireconTasks =
        new HashMap<String, JireconTask>();

    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger.getLogger(JireconImpl.class);

    /**
     * The base directory to save recording files. <tt>JireconImpl</tt> will add
     * date suffix to it as a final output directory.
     */
    private String base_output_dir;

    public JireconImpl()
    {
        logger.setLevelDebug();
    }

    /**
     * {@inheritDoc}
     * 
     * Start Libjitsi, load configuration file and create connection with XMPP
     * server.
     */
    @Override
    public void init(String configurationPath) 
        throws OperationFailedException
    {
        logger.debug(this.getClass() + "init");

        initiatePacketProviders();

        LibJitsi.start();

        System.setProperty(ConfigurationService.PNAME_CONFIGURATION_FILE_NAME,
            configurationPath);
        ConfigurationService configuration = LibJitsi.getConfigurationService();
        base_output_dir =
            configuration.getString(JireconConfigurationKey.SAVING_DIR_KEY);
        if (base_output_dir.isEmpty())
        {
            logger.fatal("Failed to initialize Jirecon, output directory was not set.");
            throw new OperationFailedException(
                "Failed to initialize Jirecon, ouput directory was not set.",
                OperationFailedException.GENERAL_ERROR);
        }
        // Remove the suffix '/' in SAVE_DIR
        if ('/' == base_output_dir.charAt(base_output_dir.length() - 1))
        {
            base_output_dir =
                base_output_dir.substring(0, base_output_dir.length() - 1);
        }

        final String xmppHost =
            configuration.getString(JireconConfigurationKey.XMPP_HOST_KEY);
        final int xmppPort =
            configuration.getInt(JireconConfigurationKey.XMPP_PORT_KEY, -1);
        try
        {
            connect(xmppHost, xmppPort);
            loginAnonymously();
        }
        catch (XMPPException e)
        {
            logger.fatal("Failed to initialize Jirecon, " + e.getXMPPError());
            uninit();
            throw new OperationFailedException(
                "Failed to initialize Jirecon, " + e.getMessage(),
                OperationFailedException.GENERAL_ERROR);
        }
    }

    /**
     * {@inheritDoc}
     * 
     * Stop Libjitsi and close connection with XMPP server.
     */
    @Override
    public void uninit()
    {
        logger.debug(this.getClass() + "uninit");
        for (JireconTask task : jireconTasks.values())
        {
            task.uninit(true);
        }
        LibJitsi.stop();
        closeConnection();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean startJireconTask(String mucJid)
    {
        logger.debug(this.getClass() + "startJireconTask: " + mucJid);

        JireconTask j = null;
        synchronized (jireconTasks)
        {
            if (jireconTasks.containsKey(mucJid))
            {
                logger.info("Failed to start Jirecon by mucJid: " + mucJid
                    + ". Duplicate mucJid.");
                return false;
            }
            j = new JireconTaskImpl();
            jireconTasks.put(mucJid, j);
        }

        String outputDir =
            base_output_dir + "/" + mucJid
                + new SimpleDateFormat("-yyMMdd-HHmmss").format(new Date());

        j.addEventListener(this);
        j.init(mucJid, connection, outputDir);

        j.start();
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean stopJireconTask(String mucJid, boolean keepData)
    {
        logger.debug(this.getClass() + "stopJireconTask: " + mucJid);
        JireconTask j = null;
        synchronized (jireconTasks)
        {
            j = jireconTasks.remove(mucJid);
        }
        if (null == j)
        {
            logger.info("Failed to stop Jirecon by mucJid: " + mucJid
                + ". Jid was not found.");
            return false;
        }
        else
        {
            j.stop();
            j.uninit(keepData);
        }
        return true;
    }

    /**
     * Build XMPP connection.
     * 
     * @param xmppHost is the host name of XMPP server.
     * @param xmppPort is the port of XMPP server.
     * @throws XMPPException if failed to build connection.
     */
    private void connect(String xmppHost, int xmppPort) throws XMPPException
    {
        logger.debug(this.getClass() + "connect");
        ConnectionConfiguration conf =
            new ConnectionConfiguration(xmppHost, xmppPort);
        connection = new XMPPConnection(conf);
        connection.connect();
    }

    /**
     * Close XMPP connection.
     */
    private void closeConnection()
    {
        logger.debug(this.getClass() + "closeConnection");
        connection.disconnect();
    }

    /**
     * Login XMPP server anonymously.
     * 
     * @throws XMPPException
     */
    private void loginAnonymously() throws XMPPException
    {
        logger.debug(this.getClass() + "login");
        connection.loginAnonymously();
    }

    /**
     * Add packet provider to connection.
     */
    private void initiatePacketProviders()
    {
        logger.debug(this.getClass() + "initiatePacketProviders");
        ProviderManager providerManager = ProviderManager.getInstance();

        providerManager.addIQProvider(JingleIQ.ELEMENT_NAME,
            JingleIQ.NAMESPACE, new JingleIQProvider());
        providerManager.addExtensionProvider("media", "http://estos.de/ns/mjs",
            new MediaExtensionProvider());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addEventListener(JireconEventListener listener)
    {
        logger.debug(this.getClass() + " addEventListener");
        listeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeEventListener(JireconEventListener listener)
    {
        logger.debug(this.getClass() + " removeEventListener");
        listeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleEvent(JireconEvent evt)
    {
        String mucJid = evt.getMucJid();

        switch (evt.getType())
        {
        case TASK_ABORTED:
            stopJireconTask(mucJid, false);
            logger.fatal("Recording task of MUC " + mucJid + " failed.");
            fireEvent(evt);
            break;
        case TASK_FINISED:
            stopJireconTask(mucJid, true);
            logger.info("Recording task of MUC: " + mucJid
                + " finished successfully.");
            fireEvent(evt);
            break;
        default:
            break;
        }
    }

    /**
     * Notify the listeners.
     * 
     * @param evt is the event that you want to send.
     */
    private void fireEvent(JireconEvent evt)
    {
        for (JireconEventListener l : listeners)
        {
            l.handleEvent(evt);
        }
    }
}
