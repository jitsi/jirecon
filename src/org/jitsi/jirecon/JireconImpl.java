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
import org.jitsi.jirecon.utils.JireconConfigurationImpl;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.util.Logger;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.provider.ProviderManager;

public class JireconImpl
    implements Jirecon, JireconEventListener
{
    private List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    private JireconConfigurationImpl configuration;

    private XMPPConnection connection;

    private Map<String, JireconTask> jireconTasks =
        new HashMap<String, JireconTask>();

    private static final Logger logger = Logger.getLogger(JireconImpl.class);

    private static final String XMPP_HOST_KEY = "XMPP_HOST";

    private static final String XMPP_PORT_KEY = "XMPP_PORT";

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
        configuration = new JireconConfigurationImpl();
        configuration.loadConfiguration(configurationPath);

        final String xmppHost = configuration.getProperty(XMPP_HOST_KEY);
        final int xmppPort =
            Integer.valueOf(configuration.getProperty(XMPP_PORT_KEY));
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
        configuration = null;
    }

    @Override
    public void startJireconTask(String conferenceJid) throws XMPPException
    {
        logger.debug(this.getClass() + "startJireconTask: " + conferenceJid);

        synchronized (jireconTasks)
        {
            if (jireconTasks.containsKey(conferenceJid))
            {
                logger.info("Failed to start Jirecon by conferenceJid: "
                    + conferenceJid + ". Duplicate conferenceJid.");
                return;
            }
            JireconTask j = new JireconTaskImpl();
            jireconTasks.put(conferenceJid, j);
            j.addEventListener(this);
            j.init(configuration, conferenceJid, connection);
            j.start();
        }
    }

    @Override
    public void stopJireconTask(String conferenceJid)
    {
        logger.debug(this.getClass() + "stopJireconTask: " + conferenceJid);
        if (!jireconTasks.containsKey(conferenceJid))
        {
            logger.info("Failed to stop Jirecon by conferenceJid: "
                + conferenceJid + ". Nonexisted Jid.");
        }
        JireconTask j = jireconTasks.remove(conferenceJid);
        j.stop();
        j.uninit();
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
                String conferenceJid =
                    ((JireconTask) evt.getSource()).getTaskInfo()
                        .getConferenceJid();
                stopJireconTask(conferenceJid);
                logger.fatal("Failed to start task of conferenceJid "
                    + conferenceJid + ".");
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
