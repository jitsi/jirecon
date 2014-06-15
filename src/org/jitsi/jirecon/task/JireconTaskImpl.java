/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.util.*;
import java.util.Map.Entry;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.JireconEvent;
import org.jitsi.jirecon.JireconEventListener;
import org.jitsi.jirecon.dtlscontrol.*;
import org.jitsi.jirecon.task.recorder.*;
import org.jitsi.jirecon.task.session.*;
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

    private JireconTaskSharingInfo sharingInfo;

    private JireconTaskInfo info = new JireconTaskInfo();

    private static final Logger logger = Logger
        .getLogger(JireconTaskImpl.class);

    @Override
    public void init(String conferenceJid, XMPPConnection connection,
        String savingDir)
    {
        logger.setLevelAll();
        logger.debug(this.getClass() + " init");

        new File(savingDir).mkdir();

        transport = new JireconIceUdpTransportManagerImpl();
        srtpControl = new JireconDtlsControlManagerImpl();
        sharingInfo = new JireconTaskSharingInfo();
        // JireconSessionInfo sessionInfo = new JireconSessionInfo();
        // JireconRecorderInfo recorderInfo = new JireconRecorderInfo();
        session =
            new JireconSessionImpl(connection, conferenceJid, savingDir,
                sharingInfo);
        recorder = new JireconRecorderImpl(savingDir, sharingInfo);
        updateState(JireconTaskState.INITIATED);
    }

    @Override
    public void uninit()
    {
        // Stop the task in case of something hasn't been released correctly.
        stop();
        info = new JireconTaskInfo();
        listeners.clear();
        transport.free();
    }

    @Override
    public void start()
    {
        try
        {
            transport.harvestLocalCandidates();

            // JireconSessionInfo sessionInfo = session.getSessionInfo();
            // JireconRecorderInfo recorderInfo = recorder.getRecorderInfo();
            JingleIQ initIq = session.connect(transport, srtpControl);

            Map<MediaType, String> fingerprints =
                JinglePacketParser.getFingerprint(initIq);
            for (Entry<MediaType, String> f : fingerprints.entrySet())
            {
                srtpControl.addRemoteFingerprint(f.getKey(), f.getValue());
            }

            Map<MediaType, IceUdpTransportPacketExtension> transportPEs =
                JinglePacketParser.getTransportPacketExts(initIq);
            transport.harvestRemoteCandidates(transportPEs);

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
                sharingInfo.getFormatAndPayloadTypes();
            recorder.startRecording(formatAndDynamicPTs, streamConnectors,
                mediaStreamTargets);
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
        session.disconnect(Reason.SUCCESS, "OK, gotta go.");
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
