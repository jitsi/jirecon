/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQProvider;

import org.jitsi.jirecon.extension.MediaExtensionProvider;
import org.jitsi.jirecon.utils.JireconConfigurationImpl;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.DtlsControl;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.SrtpControlType;
import org.jitsi.util.Logger;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.provider.ProviderManager;

public class JireconImpl
    implements Jirecon, JireconEventListener
{
    private List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    private JireconConfigurationImpl configuration;

    private XMPPConnection connection;

    private MediaService mediaService;

    private Map<String, JireconTask> jirecons =
        new HashMap<String, JireconTask>();

    private Logger logger;

    private final String XMPP_HOST_KEY = "XMPP_HOST";

    private final String XMPP_PORT_KEY = "XMPP_PORT";

    public JireconImpl()
    {
        logger = Logger.getLogger(this.getClass());
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

        mediaService = LibJitsi.getMediaService();
    }

    @Override
    public void uninit()
    {
        logger.debug(this.getClass() + "uninit");
        LibJitsi.stop();
        disconnect();
    }

    @Override
    public void startJireconTask(String conferenceJid) throws XMPPException
    {
        logger.debug(this.getClass() + "startJireconTask: " + conferenceJid);

        if (jirecons.containsKey(conferenceJid))
        {
            logger.info("Failed to start Jirecon by conferenceJid: "
                + conferenceJid + ". Duplicate conferenceJid.");
            return;
        }
        JireconTask j = new JireconTaskImpl();
        jirecons.put(conferenceJid, j);
        j.addEventListener(this);
        j.init(configuration, conferenceJid, connection, mediaService);
        j.start();
    }

    @Override
    public void stopJireconTask(String conferenceJid)
    {
        logger.debug(this.getClass() + "stopJireconTask: " + conferenceJid);
        if (!jirecons.containsKey(conferenceJid))
        {
            logger.info("Failed to stop Jirecon by conferenceJid: "
                + conferenceJid + ". Nonexisted Jid.");
        }
        JireconTask j = jirecons.remove(conferenceJid);
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

    private void disconnect()
    {
        logger.debug(this.getClass() + "disconnect");
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

    private void fireEvent(JireconEvent evt)
    {
        for (JireconEventListener l : listeners)
        {
            l.handleEvent(evt);
        }
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
}
