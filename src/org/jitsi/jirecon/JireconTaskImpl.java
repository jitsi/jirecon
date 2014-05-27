/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

// TODO: Rewrite those import statements to package import statement.
import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ParameterPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.PayloadTypePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.RtpDescriptionPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;

import org.jitsi.jirecon.recorder.JireconRecorder;
import org.jitsi.jirecon.recorder.JireconRecorderImpl;
import org.jitsi.jirecon.session.JireconSession;
import org.jitsi.jirecon.session.JireconSessionImpl;
import org.jitsi.jirecon.session.JireconSessionInfo;
import org.jitsi.jirecon.transport.JireconIceUdpTransportManagerImpl;
import org.jitsi.jirecon.transport.JireconTransportManager;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.format.AudioMediaFormat;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jitsi.util.Logger;
import org.jivesoftware.smack.XMPPConnection;

/**
 * This is an implementation of Jirecon
 * 
 * @author lishunyang
 * 
 */
public class JireconTaskImpl
    implements JireconTask, JireconEventListener
{
    private List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    private JireconSession session;

    private JireconTransportManager transport;

    private JireconRecorder recorder;

    private JireconTaskInfo info = new JireconTaskInfo();

    private Logger logger;

    public JireconTaskImpl()
    {
        session = new JireconSessionImpl();
        transport = new JireconIceUdpTransportManagerImpl();
        recorder = new JireconRecorderImpl();
        session.addEventListener(this);
        recorder.addEventListener(this);
        logger = Logger.getLogger(JireconTaskImpl.class);
        logger.setLevelDebug();
    }

    @Override
    public void init(JireconConfiguration configuration, String conferenceJid,
        XMPPConnection connection, MediaService service)
    {
        logger.debug(this.getClass() + " init");
        session.init(configuration, connection, conferenceJid, transport);
        transport.init(configuration);
        recorder.init(configuration, service);
        updateState(JireconTaskState.INITIATING);
    }

    @Override
    public void uninit()
    {
        session.uninit();
        transport.uninit();
        recorder.uninit();
    }

    @Override
    public void start()
    {
        try
        {
            transport.harvestLocalCandidates();
            session.start();
        }
        catch (BindException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IllegalArgumentException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void stop()
    {
        recorder.stop();
        session.stop();
    }

    @Override
    public void handleEvent(JireconEvent evt)
    {
        switch (evt.getState())
        {
        case ABORTED:
            fireEvent(new JireconEvent(this, JireconEvent.State.ABORTED));
            break;
        case SESSION_BUILDING:
            updateState(JireconTaskState.SESSION_INITIATING);
        case SESSION_CONSTRUCTED:
            updateState(JireconTaskState.SESSION_CONSTRUCTED);
            // TODO: Start recording
            break;
        default:
            break;
        }
    }

    public void fireEvent(JireconEvent evt)
    {
        for (JireconEventListener l : listeners)
        {
            l.handleEvent(evt);
        }
    }

    private void updateState(JireconTaskState state)
    {
        info.setState(state);
    }

    @Override
    public void addEventListener(JireconEventListener listener)
    {
        listeners.add(listener);
    }

    @Override
    public void removeEventListener(JireconEventListener listener)
    {
        listeners.remove(listener);
    }

    @Override
    public JireconTaskInfo getTaskInfo()
    {
        return info;
    }

}
