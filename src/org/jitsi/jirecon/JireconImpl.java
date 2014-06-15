/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.io.IOException;
import java.util.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;

import org.jitsi.jirecon.extension.MediaExtensionProvider;
import org.jitsi.jirecon.task.JireconTask;
import org.jitsi.jirecon.task.JireconTaskImpl;
import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.util.Logger;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.provider.ProviderManager;

public class JireconImpl
    implements Jirecon, JireconEventListener
{
    private List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    private XMPPConnection connection;

    private Map<String, JireconTask> jireconTasks =
        new HashMap<String, JireconTask>();

    private static final Logger logger = Logger.getLogger(JireconImpl.class);

    private static final String CONFIGURATION_FILE_PATH = "jirecon.properties";

    private static final String XMPP_HOST_KEY = "XMPP_HOST";

    private static final String XMPP_PORT_KEY = "XMPP_PORT";

    private static final String SAVING_DIR_KEY = "OUTPUT_DIR";

    private String SAVING_DIR;

    public JireconImpl()
    {
        logger.setLevelDebug();
    }

    @Override
    public void init(String configurationPath)
        throws IOException,
        XMPPException
    {
        logger.debug(this.getClass() + "init");

        initiatePacketProviders();

        LibJitsi.start();
        System.setProperty(ConfigurationService.PNAME_CONFIGURATION_FILE_NAME,
            CONFIGURATION_FILE_PATH);
        ConfigurationService configuration = LibJitsi.getConfigurationService();
        SAVING_DIR = configuration.getString(SAVING_DIR_KEY);
        // Remove the suffix '/' in SAVE_DIR
        if ('/' == SAVING_DIR.charAt(SAVING_DIR.length() - 1))
        {
            SAVING_DIR = SAVING_DIR.substring(0, SAVING_DIR.length() - 1);
        }

        final String xmppHost = configuration.getString(XMPP_HOST_KEY);
        final int xmppPort = configuration.getInt(XMPP_PORT_KEY, -1);
        try
        {
            connect(xmppHost, xmppPort);
            login();
        }
        catch (XMPPException e)
        {
            logger.fatal(e.getXMPPError() + "\nDisconnect XMPP connection.");
            uninit();
            throw e;
        }
    }

    @Override
    public void uninit()
    {
        logger.debug(this.getClass() + "uninit");
        for (JireconTask task : jireconTasks.values())
        {
            task.uninit();
        }
        LibJitsi.stop();
        closeConnection();
    }

    @Override
    public void startJireconTask(String mucJid) throws XMPPException
    {
        logger.debug(this.getClass() + "startJireconTask: " + mucJid);

        synchronized (jireconTasks)
        {
            if (jireconTasks.containsKey(mucJid))
            {
                logger.info("Failed to start Jirecon by mucJid: "
                    + mucJid + ". Duplicate mucJid.");
                return;
            }
            JireconTask j = new JireconTaskImpl();
            jireconTasks.put(mucJid, j);
            j.addEventListener(this);
            j.init(mucJid, connection, SAVING_DIR + "/" + mucJid);
            j.start();
        }
    }

    @Override
    public void stopJireconTask(String mucJid)
    {
        logger.debug(this.getClass() + "stopJireconTask: " + mucJid);
        synchronized (jireconTasks)
        {
            if (!jireconTasks.containsKey(mucJid))
            {
                logger.info("Failed to stop Jirecon by mucJid: "
                    + mucJid + ". Nonexisted Jid.");
                return;
            }
            JireconTask j = jireconTasks.remove(mucJid);
            j.stop();
            j.uninit();
        }
    }

    private void connect(String xmppHost, int xmppPort) throws XMPPException
    {
        logger.debug(this.getClass() + "connect");
        ConnectionConfiguration conf =
            new ConnectionConfiguration(xmppHost, xmppPort);
        connection = new XMPPConnection(conf);
        connection.connect();
    }

    private void closeConnection()
    {
        logger.debug(this.getClass() + "closeConnection");
        connection.disconnect();
    }

    private void login() throws XMPPException
    {
        logger.debug(this.getClass() + "login");
        connection.loginAnonymously();
    }

    private void initiatePacketProviders()
    {
        logger.debug(this.getClass() + "initiatePacketProviders");
        ProviderManager providerManager = ProviderManager.getInstance();

        providerManager.addIQProvider(JingleIQ.ELEMENT_NAME,
            JingleIQ.NAMESPACE, new JingleIQProvider());
        providerManager.addExtensionProvider("media", "http://estos.de/ns/mjs",
            new MediaExtensionProvider());
    }

    @Override
    public void addEventListener(JireconEventListener listener)
    {
        logger.debug(this.getClass() + " addEventListener");
        listeners.add(listener);
    }

    @Override
    public void removeEventListener(JireconEventListener listener)
    {
        logger.debug(this.getClass() + " removeEventListener");
        listeners.remove(listener);
    }

    @Override
    public void handleEvent(JireconEvent evt)
    {
        switch (evt.getEventId())
        {
        case TASK_ABORTED:
            if (evt.getSource() instanceof JireconTask)
            {
                String mucJid =
                    ((JireconTask) evt.getSource()).getTaskInfo()
                        .getMucJid();
                stopJireconTask(mucJid);
                logger.fatal("Failed to start task of mucJid "
                    + mucJid + ".");
            }
            break;
        default:
            break;
        }
    }

    private void fireEvent(JireconEvent evt)
    {
        for (JireconEventListener l : listeners)
        {
            l.handleEvent(evt);
        }
    }
}
