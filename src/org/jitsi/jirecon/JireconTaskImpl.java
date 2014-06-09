/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.io.IOException;
import java.net.BindException;
import java.util.*;
import java.util.Map.Entry;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.dtlscontrol.*;
import org.jitsi.jirecon.recorder.*;
import org.jitsi.jirecon.session.*;
import org.jitsi.jirecon.transport.*;
import org.jitsi.jirecon.utils.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jitsi.util.Logger;
import org.jivesoftware.smack.*;

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

    private JireconSrtpControlManager srtpControl;

    private JireconRecorder recorder;

    private JireconTaskInfo info = new JireconTaskInfo();

    private Logger logger;

    public JireconTaskImpl()
    {
        session = new JireconSessionImpl();
        transport = new JireconIceUdpTransportManagerImpl();
        srtpControl = new JireconDtlsControlManagerImpl();
        recorder = new JireconRecorderImpl();
        logger = Logger.getLogger(JireconTaskImpl.class);
    }

    @Override
    public void init(JireconConfiguration configuration, String conferenceJid,
        XMPPConnection connection, MediaService mediaService)
    {
        logger.setLevelAll();
        logger.debug(this.getClass() + " init");

        transport.init(configuration);
        srtpControl.init(mediaService, configuration);
        session.init(configuration, connection, conferenceJid);
        recorder.init(configuration, mediaService, srtpControl);
        updateState(JireconTaskState.INITIATED);
    }

    @Override
    public void uninit()
    {
        info = new JireconTaskInfo();
        listeners.clear();
        recorder.uninit();
        session.uninit();
        transport.uninit();
        srtpControl.uinit();
    }

    @Override
    public void start()
    {
        try
        {
            transport.harvestLocalCandidates();

            session.joinConference();

            JingleIQ initPacket = session.waitForInitPacket();

            session.recordSessionInfo(initPacket);

            session.sendAck(initPacket);

            recorder.prepareMediaStreams();

            Map<MediaType, String> fingerprints =
                JinglePacketParser.getFingerprint(initPacket);
            for (Entry<MediaType, String> f : fingerprints.entrySet())
            {
                srtpControl.addRemoteFingerprint(f.getKey(), f.getValue());
            }

            Map<MediaType, IceUdpTransportPacketExtension> transportPEs =
                JinglePacketParser.getTransportPacketExts(initPacket);
            transport.harvestRemoteCandidates(transportPEs);

            JireconSessionInfo sessionInfo = session.getSessionInfo();
            JireconRecorderInfo recorderInfo = recorder.getRecorderInfo();
            session.sendAccpetPacket(sessionInfo, recorderInfo, transport,
                srtpControl);

            session.waitForAckPacket();

            transport.startConnectivityCheck();

            Map<MediaType, StreamConnector> streamConnectors =
                new HashMap<MediaType, StreamConnector>();
            Map<MediaType, MediaStreamTarget> mediaStreamTargets =
                new HashMap<MediaType, MediaStreamTarget>();
            for (MediaType mediaType : MediaType.values())
            {
                if (mediaType != MediaType.AUDIO
                    && mediaType != MediaType.VIDEO)
                    continue;

                StreamConnector streamConnector =
                    transport.getStreamConnector(mediaType);
                streamConnectors.put(mediaType, streamConnector);

                MediaStreamTarget mediaStreamTarget =
                    transport.getStreamTarget(mediaType);
                mediaStreamTargets.put(mediaType, mediaStreamTarget);
            }
            Map<MediaFormat, Byte> formatAndDynamicPTs =
                JinglePacketParser.getFormatAndDynamicPTs(initPacket);
            recorder.completeMediaStreams(formatAndDynamicPTs,
                streamConnectors, mediaStreamTargets);

            recorder.prepareRecorders();

            recorder.startReceiving();

            recorder.startRecording();
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
        catch (XMPPException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (OperationFailedException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (MediaException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void stop()
    {
        logger.info(this.getClass() + " stop.");
        recorder.stopRecording();
        recorder.stopReceiving();
        session.sendByePacket(Reason.SUCCESS, "OK, gotta go.");
        session.leaveConference();
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

    @Override
    public void handleEvent(JireconEvent evt)
    {
        // TODO Auto-generated method stub
    }

    private void fireEvent(JireconEvent evt)
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

}
