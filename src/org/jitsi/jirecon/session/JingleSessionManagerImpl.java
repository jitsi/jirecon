package org.jitsi.jirecon.session;

// TODO: Rewrite those import statements to package import statement.
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQProvider;

import org.jitsi.jirecon.extension.MediaExtensionProvider;
import org.jitsi.jirecon.utils.JinglePacketParser;
import org.jitsi.jirecon.utils.JireconMessageReceiver;
import org.jitsi.jirecon.utils.JireconMessageSender;
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
public class JingleSessionManagerImpl
    implements JingleSessionManager, JireconMessageSender,
    JireconMessageReceiver
{
    Set<JireconMessageReceiver> msgReceivers;

    /**
     * The hostname of XMPP server. This properties should be set in configure
     * file.
     */
    private String hostname;

    /**
     * The port of XMPP server. This properties should be set in configure file.
     */
    private int port;

    /**
     * The XMPP connection, it will be shared with all Jingle session.
     */
    private XMPPConnection connection;

    /**
     * The Jingle sessions to be managed.
     */
    private Map<String, JingleSession> sessions;

    /**
     * The laborious logger
     */
    private Logger logger;

    /**
     * Constructor
     * 
     * @param hostname The hostname of XMPP server
     * @param port The port of XMPP connection
     */
    public JingleSessionManagerImpl(String hostname, int port)
    {
        msgReceivers = new HashSet<JireconMessageReceiver>();
        this.hostname = hostname;
        this.port = port;
        logger = Logger.getLogger(this.getClass());
    }

    /**
     * Initialize the manager. This method should be called before open any
     * Jingle session.
     * 
     * @throws XMPPException Throws XMPPException if can't construct XMPP
     *             connection.
     */
    @Override
    public void init() throws XMPPException
    {
        try
        {
            connect();
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
    private void connect() throws XMPPException
    {
        ConnectionConfiguration conf =
            new ConnectionConfiguration(hostname, port);
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
                if (packet.getClass() == JingleIQ.class)
                {
                    JingleSession js =
                        sessions.get(JinglePacketParser.getConferenceId(
                            (JingleIQ) packet, true));
                    if (null != js)
                        js.handleJinglePacket((JingleIQ) packet);
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
    public boolean uninit()
    {
        if (sessions != null && sessions.isEmpty())
        {
            for (Map.Entry<String, JingleSession> e : sessions.entrySet())
            {
                e.getValue().leave();
            }
        }

        disconnect();

        return true;
    }

    /**
     * Open an new Jingle session with specified conference id.
     * 
     * @param conferenceId The conference id which you want to join.
     * @return True if succeeded, false if failed.
     * @throws XMPPException
     */
    @Override
    public void openJingleSession(String conferenceId) throws XMPPException
    {
        if (null == sessions)
            sessions = new HashMap<String, JingleSession>();
        if (sessions.containsKey(conferenceId))
            return;

        JingleSession js = new JingleSession(connection);
        js.addReceiver(this);
        try
        {
            js.join(conferenceId);
        }
        catch (XMPPException e)
        {
            logger
                .fatal(e.getXMPPError()
                    + "\nOpen Jingle session failed, can not join the JitMeet conference: "
                    + conferenceId + ".");
            throw e;
        }
        sessions.put(conferenceId, js);
    }

    /**
     * Close an existed Jingle session with specified conference id.
     * 
     * @param conferenceId
     * @return True if succeeded, false if failed.
     */
    @Override
    public void closeJingleSession(String conferenceId)
    {
        if (null == sessions || !sessions.containsKey(conferenceId))
            return;

        JingleSession js = sessions.get(conferenceId);
        sessions.remove(conferenceId);
        js.leave();
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
    public JingleSessionInfo getJingleSessionInfo(String conferenceId)
    {
        return sessions.get(conferenceId).getJingleSessionInfo();
    }

    @Override
    public void receiveMsg(JireconMessageSender sender, String msg)
    {
        // JingleSession send session status message
        if (sender instanceof JingleSession)
        {
            final JingleSession session = (JingleSession) sender;
            switch (session.getStatus())
            {
            case ABORTED:
                // TODO
                break;
            case CONSTRUCTED:
                sendMsg(session.getConferenceId());
                break;
            default:
                break;
            }
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

}
