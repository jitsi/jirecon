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
 * An implementation of <tt>JireconTask</tt>. It is designed in Mediator
 * pattern, <tt>JireconTaskImpl</tt> is the mediator, others like
 * <tt>JireconSession</tt>, <tt>JireconRecorder</tt> are the colleagues.
 * 
 * @author lishunyang
 * @see JireconTask
 * 
 */
public class JireconTaskImpl
    implements JireconTask, JireconEventListener, Runnable
{
    /**
     * The <tt>JireconEvent</tt> listeners, they will be notified when some
     * important things happen.
     */
    private List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    /**
     * The instance of <tt>JireconSession</tt>.
     */
    private JireconSession session;

    /**
     * The instance of <tt>JireconTransportManager</tt>.
     */
    private JireconTransportManager transport;

    /**
     * The instance of <tt>SrtpControlManager</tt>.
     */
    private SrtpControlManager srtpControl;

    /**
     * The instance of <tt>JireconRecorder</tt>.
     */
    private JireconRecorder recorder;

    /**
     * The sharing information of this task, it is used for simplifying the
     * information exchange between <tt>JireconSession</tt> and
     * <tt>JireconRecorder</tt>.
     */
    private JireconTaskSharingInfo sharingInfo;

    /**
     * The thread pool to make the method "start" to be asynchronous.
     */
    private ExecutorService executorService;

    /**
     * Indicate whether this task has stopped or not.
     */
    private boolean isStopped = false;

    // TODO: JireconTaskInfo doesn't have much content to record, so I should
    // delete it.
    /**
     * Record the task info.
     */
    private JireconTaskInfo info = new JireconTaskInfo();

    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger
        .getLogger(JireconTaskImpl.class);

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void uninit()
    {
        // Stop the task in case of something hasn't been released correctly.
        stop();
        info = new JireconTaskInfo();
        listeners.clear();
        transport.free();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start()
    {
        executorService.execute(this);
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * This is actually the main part of method "start", in order to make the
     * method "start" to be asynchronous.
     * <p>
     * 1. Harvest local candidates.
     * <p>
     * 2. Connect with MUC.
     * <p>
     * 3. Build ICE connectivity.
     * <p>
     * 4. Start recording.
     * <p>
     * <strong>Warning:</strong> In execution flow above, step 2, 3, 4 are
     * actually overlapped instead of sequential.
     */
    @Override
    public void run()
    {
        try
        {
            // Harvest local candidates. This should be done first because we
            // need those information when build Jingle session.
            transport.harvestLocalCandidates();

            // Build the Jingle session with specified MUC.
            JingleIQ initIq = session.connect(transport, srtpControl);

            // Parse remote fingerprint from Jingle session-init packet and
            // setup srtp control manager.
            Map<MediaType, String> fingerprints =
                JinglePacketParser.getFingerprint(initIq);
            for (Entry<MediaType, String> f : fingerprints.entrySet())
            {
                srtpControl.addRemoteFingerprint(f.getKey(), f.getValue());
            }

            // Parse remote candidates information from Jingle session-init
            // packet and setup transport manager.
            Map<MediaType, IceUdpTransportPacketExtension> transportPEs =
                JinglePacketParser.getTransportPacketExts(initIq);
            transport.harvestRemoteCandidates(transportPEs);

            // Start establishing ICE connectivity. Notice that this method is
            // asynchronous method.
            transport.startConnectivityEstablishment();

            // Once transport manager has selected candidates pairs, get stream
            // connectors. Notice that we have to wait for at least one
            // candidate pair being selected.
            // TODO: At present, if ICE connectivity establishment hangs, then
            // the task will hang at here. So maybe I should set a max wait
            // time, if time out, just break the task.
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

            // Gather some information and start recording.
            Map<MediaFormat, Byte> formatAndDynamicPTs =
                sharingInfo.getFormatAndPayloadTypes();
            recorder.startRecording(formatAndDynamicPTs, streamConnectors,
                mediaStreamTargets);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fireEvent(new JireconEvent(this,
                JireconEvent.Type.TASK_ABORTED));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addEventListener(JireconEventListener listener)
    {
        listeners.add(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeEventListener(JireconEventListener listener)
    {
        listeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JireconTaskInfo getTaskInfo()
    {
        return info;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleEvent(JireconEvent evt)
    {
        // TODO Auto-generated method stub
    }

    /**
     * Fire the event if the task has finished or break.
     * 
     * @param evt is the <tt>JireconEvent</tt> you want to notify the listeners.
     */
    private void fireEvent(JireconEvent evt)
    {
        for (JireconEventListener l : listeners)
        {
            l.handleEvent(evt);
        }
    }

    /**
     * Thread exception handler, in order to catch exceptions of the task
     * thread.
     * 
     * @author lishunyang
     * 
     */
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
                    JireconEvent.Type.TASK_ABORTED));
            }
        }
    }

    /**
     * Handler factory, in order to create thread with
     * <tt>ThreadExceptionHandler</tt>
     * 
     * @author lishunyang
     * 
     */
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
