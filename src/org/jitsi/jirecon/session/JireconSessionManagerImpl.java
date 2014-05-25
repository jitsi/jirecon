/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

// TODO: Rewrite those import statements to package import statement.
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQProvider;

import org.jitsi.jirecon.extension.MediaExtensionProvider;
import org.jitsi.jirecon.recorder.JireconRecorderManager;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.jirecon.utils.JireconConfigurationImpl;
import org.jitsi.jirecon.utils.JireconFactory;
import org.jitsi.jirecon.utils.JireconFactoryImpl;
import org.jitsi.jirecon.utils.JireconMessageReceiver;
import org.jitsi.jirecon.utils.JireconMessageSender;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.util.Logger;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.provider.ProviderManager;

// TODO: Add configuration file support.
/**
 * An implement of JingleSessoinManager
 * 
 * @author lishunyang
 * 
 */
public class JireconSessionManagerImpl
    implements JireconSessionManager, JireconMessageSender,
    JireconMessageReceiver
{
    Set<JireconMessageReceiver> msgReceivers;

    /**
     * The XMPP connection, it will be shared with all Jingle session.
     */
    private XMPPConnection connection;

    /**
     * The Jingle sessions to be managed.
     */
    private Map<String, JireconSession> sessions;

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
        msgReceivers = new HashSet<JireconMessageReceiver>();
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
        LibJitsi.start();
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
                System.out.println("<---: " + packet.toXML());
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
        
        LibJitsi.stop();
    }

    /**
     * Open an new Jingle session with specified conference id.
     * 
     * @param conferenceJid The conference name which you want to join.
     * @return True if succeeded, false if failed.
     * @throws XMPPException
     */
    @Override
    public void openJingleSession(String conferenceJid) throws XMPPException
    {
        if (null == sessions)
            sessions = new HashMap<String, JireconSession>();
        if (sessions.containsKey(conferenceJid))
        {
            String err =
                "You have already join the conference " + conferenceJid + ".";
            logger.fatal("JireconSessionManager: " + err);
            throw new XMPPException(err);
        }

        JireconSession js = factory.createSession(connection);
        ((JireconMessageSender) js).addReceiver(this);
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
     * Close an existed Jingle session with specified conference id.
     * 
     * @param conferenceJid
     * @return True if succeeded, false if failed.
     */
    @Override
    public void closeJingleSession(String conferenceJid)
    {
        if (null == sessions || !sessions.containsKey(conferenceJid))
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
    public void addReceiver(JireconMessageReceiver receiver)
    {
        msgReceivers.add(receiver);
    }

    @Override
    public void removeReceiver(JireconMessageReceiver receiver)
    {
        msgReceivers.remove(receiver);
    }

    @Override
    public SessionInfo getSessionInfo(String conferenceJid)
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

    @Override
    public void receiveMsg(JireconMessageSender sender, String msg)
    {
        System.out.println("JireconSessionManager receive a message " + msg);
        if (sender instanceof JireconSessionImpl)
        {
            final JireconSession session = (JireconSession) sender;
            // Send message to JireconRecorderManager
            switch (session.getSessionInfo().getSessionStatus())
            {
            case CONSTRUCTED:
                sendMsg(session.getSessionInfo().getConferenceJid());
                break;
            case ABORTED:
                sendMsg(session.getSessionInfo().getConferenceJid());
                closeJingleSession(session.getSessionInfo().getConferenceJid());
                break;
            default:
                break;
            }
        }
        else if (sender instanceof JireconRecorderManager)
        {
            logger
                .info("Oh? JireconSessionManager receive a message from JireconRecorderManager");
        }
        else
        {
            logger
                .info("JingleSessionManager receive a message from unknown sender.");
        }
    }

    @Override
    public void sendMsg(String msg)
    {
        for (JireconMessageReceiver r : msgReceivers)
        {
            r.receiveMsg(this, msg);
        }
    }

    // TODO: If we plan to use full jid, then this method should be changed.
    private String parseConferenceJid(String conferenceFullJid)
    {
        return conferenceFullJid.split("/")[0];
    }
}
