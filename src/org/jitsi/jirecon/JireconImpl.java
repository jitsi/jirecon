/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.text.SimpleDateFormat;
import java.util.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.*;
import org.jitsi.jirecon.extension.*;
import org.jitsi.jirecon.task.*;
import org.jitsi.jirecon.utils.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.provider.*;

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
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger.getLogger(JireconImpl.class);
    
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
     * The base directory to save recording files. <tt>JireconImpl</tt> will add
     * date suffix to it as a final output directory.
     */
    private String baseOutputDir;

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
        baseOutputDir =
            configuration.getString(JireconConfigurationKey.SAVING_DIR_KEY);
        if (baseOutputDir.isEmpty())
        {
            logger.fatal("Failed to initialize Jirecon, output directory was not set.");
            throw new OperationFailedException(
                "Failed to initialize Jirecon, ouput directory was not set.",
                OperationFailedException.GENERAL_ERROR);
        }
        // Remove the suffix '/' in SAVE_DIR
        if ('/' == baseOutputDir.charAt(baseOutputDir.length() - 1))
        {
            baseOutputDir =
                baseOutputDir.substring(0, baseOutputDir.length() - 1);
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
        synchronized (jireconTasks)
        {
            for (JireconTask task : jireconTasks.values())
            {
                task.uninit(true);
            }
        }
        closeConnection();
        LibJitsi.stop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean startJireconTask(String mucJid)
    {
        logger.debug(this.getClass() + "startJireconTask: " + mucJid);

        JireconTask task = null;
        synchronized (jireconTasks)
        {
            if (jireconTasks.containsKey(mucJid))
            {
                logger.info("Failed to start Jirecon by mucJid: " + mucJid
                    + ". Duplicate mucJid.");
                return false;
            }
            task = new JireconTaskImpl();
            jireconTasks.put(mucJid, task);
        }

        String outputDir =
            baseOutputDir + "/" + mucJid
                + new SimpleDateFormat("-yyMMdd-HHmmss").format(new Date());

        task.addEventListener(this);
        task.init(mucJid, connection, outputDir);

        task.start();
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean stopJireconTask(String mucJid, boolean keepData)
    {
        logger.debug(this.getClass() + "stopJireconTask: " + mucJid);
        JireconTask task = null;
        synchronized (jireconTasks)
        {
            task = jireconTasks.remove(mucJid);
        }
        if (null == task)
        {
            logger.info("Failed to stop Jirecon by mucJid: " + mucJid
                + ". Jid was not found.");
            return false;
        }
        else
        {
            task.stop();
            task.uninit(keepData);
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
        if (connection.isConnected())
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
        providerManager.addExtensionProvider(MediaExtension.ELEMENT_NAME,
            MediaExtension.NAMESPACE, new MediaExtensionProvider());
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
