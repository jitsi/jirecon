package jirecon.session;

import java.util.HashMap;
import java.util.Map;

import jirecon.utils.JinglePacketParser;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQProvider;

import org.ice4j.ice.Agent;
import org.jitsi.util.Logger;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.provider.ProviderManager;

import test.JitMeetConnector;
import test.MediaExtensionProvider;

public class JingleSessionManagerImpl
    implements JingleSessionManager
{
    private String username;

    private String hostname;

    private int port;

    private XMPPConnection connection;

    private Map<String, JingleSession> sessions;

    private Logger logger;

    private boolean isInitiated = false;

    public JingleSessionManagerImpl(String hostname, int port)
    {
        this.hostname = hostname;
        this.port = port;
        logger = Logger.getLogger(JingleSessionManagerImpl.class);
    }

    public boolean init()
    {
        try
        {
            connect();
            login();
            
            initiatePacketProviders();
            initiatePacketListeners();
        }
        catch (XMPPException e)
        {
            disconnect();
            return false;
        }
        
        return true;
    }

    private void connect() throws XMPPException
    {
        ConnectionConfiguration conf =
            new ConnectionConfiguration(hostname, port);
        connection = new XMPPConnection(conf);
        connection.connect();
    }
    
    private void disconnect()
    {
        connection.disconnect();
    }

    private void login() throws XMPPException
    {
        connection.loginAnonymously();
    }

    private void initiatePacketProviders()
    {
        ProviderManager providerManager = ProviderManager.getInstance();

        providerManager.addIQProvider(JingleIQ.ELEMENT_NAME,
            JingleIQ.NAMESPACE, new JingleIQProvider());
        providerManager.addExtensionProvider("media", "http://estos.de/ns/mjs",
            new MediaExtensionProvider());
    }

    private void initiatePacketListeners()
    {
        addPacketSendingListener();
        addPacketReceivingListener();
    }
    
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
    
    private void addPacketReceivingListener()
    {
        connection.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                System.out.println("<---: " + packet.toXML());
                if (packet instanceof JingleIQ)
                {
                    JingleSession js = sessions.get(JinglePacketParser.getTo((JingleIQ)packet));
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
    
    public boolean uninit()
    {
        if (sessions != null && sessions.isEmpty())
        {
            for (Map.Entry<String, JingleSession> e : sessions.entrySet())
            {
                e.getValue().close();
            }
        }
        
        disconnect();
        
        return true;
    }

    @Override
    public void openAJingleSession(String conferenceId)
    {
        if (null == sessions)
            sessions = new HashMap<String, JingleSession>();
        if (sessions.containsKey(conferenceId))
            return;
        
        JingleSession js = new JingleSession(conferenceId, connection);
        sessions.put(conferenceId, js);
        
        // TODO: Do some initiate work according to activity graph
    }

    @Override
    public void closeAJingleSession(String conferenceId)
    {
        if (null == sessions || !sessions.containsKey(conferenceId))
            return;
        
        JingleSession js = sessions.get(conferenceId);
        sessions.remove(conferenceId);
        
        // TODO: Do some ending work according activity graph
    }

}
