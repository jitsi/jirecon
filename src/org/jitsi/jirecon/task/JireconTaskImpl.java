/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;

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
    implements JireconTask, JireconEventListener, Runnable
{
    private List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    private JireconSession session;

    private JireconTransportManager transport;

    private SrtpControlManager srtpControl;

    private JireconRecorder recorder;

    private JireconTaskSharingInfo sharingInfo;

    private ExecutorService executorService;

    private boolean isStopped = false;

    private JireconTaskInfo info = new JireconTaskInfo();

    private static final Logger logger = Logger
        .getLogger(JireconTaskImpl.class);

    @Override
    public void init(String mucJid, XMPPConnection connection, String savingDir)
    {
        logger.setLevelAll();
        logger.debug(this.getClass() + " init");
        info.setMucJid(mucJid);
        executorService =
            Executors.newSingleThreadExecutor(new HandlerThreadFactory());

        File dir = new File(savingDir);
        if (!dir.exists())
        {
            dir.mkdirs();
        }

        transport = new JireconIceUdpTransportManagerImpl();
        srtpControl = new DtlsControlManagerImpl();
        sharingInfo = new JireconTaskSharingInfo();
        session = new JireconSessionImpl(connection, mucJid, sharingInfo);
        recorder =
            new JireconRecorderImpl(savingDir, sharingInfo,
                srtpControl.getAllSrtpControl());
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
        executorService.execute(this);
    }

    @Override
    public void stop()
    {
        if (!isStopped)
        {
            logger.info(this.getClass() + " stop.");
            recorder.stopRecording();
            session.disconnect(Reason.SUCCESS, "OK, gotta go.");
            isStopped = true;
        }
    }

    @Override
    public void run()
    {
        try
        {
            transport.harvestLocalCandidates();

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

            transport.startConnectivityEstablishment();

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
        catch (Exception e)
        {
            e.printStackTrace();
            fireEvent(new JireconEvent(this,
                JireconEvent.JireconEventId.TASK_ABORTED));
        }
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

    private class ThreadExceptionHandler
        implements Thread.UncaughtExceptionHandler
    {
        @Override
        public void uncaughtException(Thread t, Throwable e)
        {
            if (t instanceof JireconTask)
            {
                ((JireconTask) e).stop();
                fireEvent(new JireconEvent(JireconTaskImpl.this,
                    JireconEvent.JireconEventId.TASK_ABORTED));
            }
        }
    }

    private class HandlerThreadFactory
        implements ThreadFactory
    {
        @Override
        public Thread newThread(Runnable r)
        {
            System.out.println(this + " creating new Thread");
            Thread t = new Thread(r);
            t.setUncaughtExceptionHandler(new ThreadExceptionHandler());
            return t;
        }
    }
}
