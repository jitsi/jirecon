/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

// TODO: Rewrite those import statements to package import statement.
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQProvider;

import org.jitsi.jirecon.JireconEvent;
import org.jitsi.jirecon.JireconEventListener;
import org.jitsi.jirecon.extension.MediaExtensionProvider;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.jirecon.utils.JireconFactory;
import org.jitsi.jirecon.utils.JireconFactoryImpl;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.util.Logger;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.provider.ProviderManager;

// TODO: Add configuration file support.
/**
 * An implement of JingleSessoinManager
 * 
 * @author lishunyang
 * 
 */
public class JireconSessionManagerImpl
    implements JireconSessionManager, JireconEventListener
{
    List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    /**
     * The XMPP connection, it will be shared with all Jingle session.
     */
    private XMPPConnection connection;

    /**
     * The Jingle sessions to be managed.
     */
    private Map<String, JireconSession> sessions =
        new HashMap<String, JireconSession>();

    private JireconFactory factory;

    /**
     * The laborious logger
     */
    private Logger logger;

    private final String XMPP_HOST_KEY = "XMPP_HOST";

    private final String XMPP_PORT_KEY = "XMPP_PORT";

    /**
     * Constructor
     * 
     * @param hostname The hostname of XMPP server
     * @param port The port of XMPP connection
     */
    public JireconSessionManagerImpl()
    {
        this.factory = new JireconFactoryImpl();
        logger = Logger.getLogger(this.getClass());
        logger.setLevelAll();
    }

    /**
     * Initialize the manager. This method should be called before open any
     * Jingle session.
     * 
     * @throws XMPPException Throws XMPPException if can't construct XMPP
     *             connection.
     */
    @Override
    public void init(JireconConfiguration configuration) throws XMPPException
    {
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
            disconnect();
            throw e;
        }

        initiatePacketProviders();
        initiatePacketListeners();
    }

    /**
     * Connect with XMPP server.
     * 
     * @throws XMPPException If connect failed, throw XMPP exception.
     */
    private void connect(String xmppHost, int xmppPort) throws XMPPException
    {
        ConnectionConfiguration conf =
            new ConnectionConfiguration(xmppHost, xmppPort);
        connection = new XMPPConnection(conf);
        connection.connect();
    }

    /**
     * Disconnect with XMPP server.
     */
    private void disconnect()
    {
        connection.disconnect();
    }

    /**
     * Login the XMPP server.
     * 
     * @throws XMPPException If login failed, throw XMPP exception.
     */
    private void login() throws XMPPException
    {
        connection.loginAnonymously();
    }

    /**
     * Initialize packet providers.
     */
    private void initiatePacketProviders()
    {
        ProviderManager providerManager = ProviderManager.getInstance();

        providerManager.addIQProvider(JingleIQ.ELEMENT_NAME,
            JingleIQ.NAMESPACE, new JingleIQProvider());
        providerManager.addExtensionProvider("media", "http://estos.de/ns/mjs",
            new MediaExtensionProvider());
    }

    /**
     * initialize packet listeners.
     */
    private void initiatePacketListeners()
    {
        addPacketSendingListener();
        addPacketReceivingListener();
    }

    /**
     * Add sending packet listener.
     */
    private void addPacketSendingListener()
    {
        connection.addPacketSendingListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                System.out.println("--->: " + packet.toXML());
            }
        }, new PacketFilter()
        {
            @Override
            public boolean accept(Packet packet)
            {
                return true;
            }
        });
    }

    /**
     * Add receiving packet listener.
     */
    private void addPacketReceivingListener()
    {
        connection.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                System.out.println(packet.getClass() + "<---: " + packet.toXML());
                String conferenceJid = parseConferenceJid(packet.getFrom());
                if (sessions.containsKey(conferenceJid))
                {
                    sessions.get(conferenceJid).handleSessionPacket(packet);
                }
            }
        }, new PacketFilter()
        {
            @Override
            public boolean accept(Packet packet)
            {
                return true;
            }
        });
    }

    /**
     * Uninitialize this Jingle session manager.
     */
    @Override
    public void uninit()
    {
        if (sessions != null && sessions.isEmpty())
        {
            for (Map.Entry<String, JireconSession> e : sessions.entrySet())
            {
                e.getValue().terminateSession();
            }
        }
        disconnect();
    }

    /**
     * Open an new Jingle session with specified conference id.
     * 
     * @param conferenceJid The conference name which you want to join.
     * @return True if succeeded, false if failed.
     * @throws XMPPException
     */
    @Override
    public void openJireconSession(String conferenceJid) throws XMPPException
    {
        if (sessions.containsKey(conferenceJid))
        {
            String err =
                "You have already join the conference " + conferenceJid + ".";
            logger.fatal("JireconSessionManager: " + err);
            throw new XMPPException(err);
        }

        JireconSession js = factory.createSession(connection);
        js.addEventListener(this);
        try
        {
            js.startSession(conferenceJid);
        }
        catch (XMPPException e)
        {
            logger.fatal("JireconSessionManager: Failed to join conference: "
                + conferenceJid + ".");
            e.printStackTrace();
            throw e;
        }
        sessions.put(conferenceJid, js);
    }

    /**
     * Close an existed Jirecon session with specified conference id.
     * 
     * @param conferenceJid
     * @return True if succeeded, false if failed.
     */
    @Override
    public void closeJireconSession(String conferenceJid)
    {
        if (!sessions.containsKey(conferenceJid))
        {
            return;
        }
        JireconSession js = sessions.remove(conferenceJid);
        if (null != js)
        {
            js.terminateSession();
        }
    }

    @Override
    public JireconSessionInfo getSessionInfo(String conferenceJid)
    {
        final JireconSession session = sessions.get(conferenceJid);
        if (null != session)
        {
            return session.getSessionInfo();
        }
        else
        {
            return null;
        }
    }

    // TODO: If we plan to use full jid, then this method should be changed.
    private String parseConferenceJid(String conferenceFullJid)
    {
        return conferenceFullJid.split("/")[0];
    }

    @Override
    public void addStateChangeListener(PropertyChangeListener listener)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void removeStateChangeListener(PropertyChangeListener listener)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void handleEvent(JireconEvent evt)
    {
        if (evt instanceof JireconSessionEvent)
        {
            JireconSessionEvent jsEvt = (JireconSessionEvent) evt;
            JireconSessionInfo info = jsEvt.getJireconSessionInfo();
            JireconSessionEvent newEvent = new JireconSessionEvent(this, info);
            for (JireconEventListener listener : listeners)
            {
                listener.handleEvent(newEvent);
            }

            switch (info.getSessionStatus())
            {
            case CONSTRUCTED:
                logger.info("JireconSessionManager: session "
                    + info.getConferenceJid() + " constructed.");
                break;
            case ABORTED:
                logger.fatal("JireconSessionManager: session "
                    + info.getConferenceJid() + " aborted.");
                closeJireconSession(info.getConferenceJid());
                break;
            default:
                break;
            }
        }
    }
}
