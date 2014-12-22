/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;

import org.jitsi.jirecon.*;
import org.jitsi.jirecon.TaskEvent.*;
import org.jitsi.jirecon.TaskManagerEvent.*;
import org.jitsi.jirecon.utils.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.util.*;
import org.jivesoftware.smack.*;

/**
 * The individual task to record specified Jitsi-meeting. It is designed in Mediator
 * pattern, <tt>JireconTaskImpl</tt> is the mediator, others like
 * <tt>JireconSession</tt>, <tt>JireconRecorder</tt> are the colleagues.
 * 
 * @author lishunyang
 * @author Boris Grozev
 * 
 */
public class Task
    implements JireconEventListener, 
               TaskEventListener,
               Runnable
{
    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger
        .getLogger(Task.class.getName());
    
    /**
     * The <tt>JireconEvent</tt> listeners, they will be notified when some
     * important things happen.
     */
    private List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    /**
     * The instance of <tt>JireconSession</tt>.
     */
    private JingleSessionManager jingleSessionMgr;

    /**
     * The instance of <tt>JireconTransportManager</tt>.
     */
    private IceUdpTransportManager transportMgr;

    /**
     * The instance of <tt>DtlsControlManager</tt>.
     */
    private DtlsControlManager dtlsControlMgr;
    
    /**
     * The instance of <tt>RecorderManager</tt>.
     */
    private StreamRecorderManager recorderMgr;

    /**
     * The thread pool to make the method "start" to be asynchronous.
     */
    private ExecutorService taskExecutor;

    /**
     * Indicate whether this task has stopped.
     */
    private boolean isStopped = false;
    
    /**
     * Indicate whether this task has aborted. We need this to identify the
     * situation when method "stop" is called. In case to fire appropriate
     * FINISHED or ABORTED event.
     */
    private boolean isAborted = false;
    
    /**
     * Record the task info. <tt>JireconTaskInfo</tt> can be accessed by outside
     * system.
     */
    private TaskInfo info = new TaskInfo();
    
    /**
     * Initialize a <tt>JireconTask</tt>. Specify which Jitsi-meet you want to
     * record and where we should output the media files.
     * 
     * @param mucJid indicates which meet you want to record.
     * @param connection is an existed <tt>XMPPConnection</tt> which will be
     *            used to send/receive Jingle packet.
     * @param savingDir indicates where we should output the media files.
     */
    public void init(String mucJid, XMPPConnection connection, String savingDir)
    {
        logger.info(this.getClass() + " init");
        
        info.setOutputDir(savingDir);
        File dir = new File(savingDir);
        if (!dir.exists())
            dir.mkdirs();

        ConfigurationService configuration = LibJitsi.getConfigurationService();

        info.setMucJid(mucJid);
        info.setNickname(configuration
            .getString(ConfigurationKey.NICK_KEY));

        taskExecutor =
            Executors.newSingleThreadExecutor(new HandlerThreadFactory());

        transportMgr = new IceUdpTransportManager();

        dtlsControlMgr = new DtlsControlManager();

        jingleSessionMgr = new JingleSessionManager();
        jingleSessionMgr.addTaskEventListener(this);
        jingleSessionMgr.init(connection);
        addEventListener(jingleSessionMgr);

        recorderMgr = new StreamRecorderManager();
        recorderMgr.addTaskEventListener(this);
        recorderMgr.init(savingDir, dtlsControlMgr.getAllDtlsControl());
    }

    /**
     * Uninitialize the <tt>JireconTask</tt> and get ready to be recycled by GC.
     * 
     * @param keepData Whether we should keep data. Keep the data if it is true,
     *            other wise remove them.
     */
    public void uninit(boolean keepData)
    {
        // Stop the task in case of something hasn't been released correctly.
        stop();
        
        listeners.clear();
//        transport.free();

        if (!keepData)
        {
            logger.info("Delete output files " + info.getOutputDir());
            try
            {
                Runtime.getRuntime().exec("rm -fr " + info.getOutputDir());
            }
            catch (IOException e)
            {
                logger.info("Failed to remove output files, " + e.getMessage());
            }
        }
        
        info = new TaskInfo();
    }

    /**
     * Start the <tt>JireconTask</tt>.
     * <p>
     * <strong>Warning:</strong> This is a asynchronous method, so it will
     * return quickly, but it doesn't mean that the task has been successfully
     * started. It will notify event listeners if the task is failed.
     */
    public void start()
    {
        taskExecutor.execute(this);
    }

    /**
     * Stop the <tt>JireconTask</tt>.
     */
    public void stop()
    {
        if (!isStopped)
        {
            logger.info(this.getClass() + " stop.");
            transportMgr.free();
            recorderMgr.stopRecording();
            jingleSessionMgr.disconnect(Reason.SUCCESS, "OK, gotta go.");
            isStopped = true;
            
            /*
             * We should only fire TASK_FINISHED event when the task has really
             * finished, because when task is aborted, this "stop" method will
             * also be called, and in this scene we shouldn't fire TASK_FINISHED
             * event.
             */
            if (!isAborted)
                fireEvent(new TaskManagerEvent(info.getMucJid(),
                    TaskManagerEvent.Type.TASK_FINISED));
        }
    }

    /**
     * This is actually the main part of method "start", in order to make the
     * method "start" to be asynchronous.
     */
    @Override
    public void run()
    {
        try
        {
            /* 1. Join MUC. */
            jingleSessionMgr.connect(info.getMucJid(), info.getNickname());

            /* 2. Wait for session-init packet. */
            JingleIQ initIq = jingleSessionMgr.waitForInitPacket();
            MediaType[] supportedMediaTypes =
                JinglePacketParser.getSupportedMediaTypes(initIq);

            /*
             * 3.1 Prepare for sending session-accept packet.
             */
            // Media format and payload type id.
            Map<MediaType, Map<MediaFormat, Byte>> formatAndPTs = new HashMap<MediaType, Map<MediaFormat, Byte>>();
            for (MediaType mediaType : new MediaType[] {MediaType.AUDIO, MediaType.VIDEO})
            {
                formatAndPTs.put(mediaType, JinglePacketParser
                    .getFormatAndDynamicPTs(initIq, mediaType));
            }

            Map<MediaType, Long> localSsrcs = recorderMgr.getLocalSsrcs();
            
            // Transport packet extension.
            for (MediaType mediaType : supportedMediaTypes)
            {
                transportMgr.harvestLocalCandidates(mediaType);
            }

            Map<MediaType, AbstractPacketExtension> transportPEs =
                new HashMap<MediaType, AbstractPacketExtension>();
            for (MediaType mediaType : supportedMediaTypes)
            {
                transportPEs.put(mediaType,
                    transportMgr.createTransportPacketExt(mediaType));
            }

            // Fingerprint packet extension.
            for (MediaType mediaType : supportedMediaTypes)
            {
                dtlsControlMgr.setRemoteFingerprint(mediaType,
                    JinglePacketParser.getFingerprintPacketExt(initIq, mediaType));
            }
            Map<MediaType, AbstractPacketExtension> fingerprintPEs =
                new HashMap<MediaType, AbstractPacketExtension>();
            for (MediaType mediaType : supportedMediaTypes)
            {
                fingerprintPEs.put(mediaType,
                    dtlsControlMgr.createFingerprintPacketExt(mediaType));
            }

            /* 3.2 Send session-accept packet. */
            jingleSessionMgr.sendAcceptPacket(formatAndPTs, localSsrcs, transportPEs,
                fingerprintPEs);

            /* 3.3 Wait for session-ack packet. */
            // Go on with ICE, no need to waste an RTT here.
            //jingleSessionMgr.waitForResultPacket();

            /*
             * 4.1 Prepare for ICE connectivity establishment. Harvest remote
             * candidates.
             */
            Map<MediaType, IceUdpTransportPacketExtension> remoteTransportPEs = new HashMap<MediaType, IceUdpTransportPacketExtension>();
            for (MediaType mediaType : supportedMediaTypes)
            {
                remoteTransportPEs.put(mediaType, JinglePacketParser.getTransportPacketExt(initIq, mediaType));
            }
            transportMgr.addRemoteCandidates(remoteTransportPEs);

            /*
             * 4.2 Start establishing ICE connectivity. Warning: that this
             * method is asynchronous method.
             */
            transportMgr.startConnectivityEstablishment();

            /*
             * 4.3 Wait for ICE to complete (or fail).
             */
            if(!transportMgr.wrapupConnectivityEstablishment())
            {
                logger.error("Failed to establish an ICE session.");
                fireEvent(
                    new TaskManagerEvent(info.getMucJid(),
                                         TaskManagerEvent.Type.TASK_ABORTED));
                return;
            }
            logger.info("ICE connection established (" + info.getMucJid() + ")");

            /*
             * 5.1 Prepare for recording. Once transport manager has selected
             * candidates pairs, we can get stream connectors from it, otherwise
             * we have to wait. Notice that if ICE connectivity establishment
             * doesn't get selected pairs for a specified time(MAX_WAIT_TIME),
             * we must break the task.
             */
            Map<MediaType, StreamConnector> streamConnectors =
                new HashMap<MediaType, StreamConnector>();
            Map<MediaType, MediaStreamTarget> mediaStreamTargets =
                new HashMap<MediaType, MediaStreamTarget>();
            for (MediaType mediaType : supportedMediaTypes)
            {
                StreamConnector streamConnector =
                    transportMgr.getStreamConnector(mediaType);
                streamConnectors.put(mediaType, streamConnector);

                MediaStreamTarget mediaStreamTarget =
                    transportMgr.getStreamTarget(mediaType);
                mediaStreamTargets.put(mediaType, mediaStreamTarget);
            }
            
            /* 5.2 Start recording. */
            recorderMgr.startRecording(formatAndPTs, streamConnectors,
                mediaStreamTargets);

            fireEvent(new TaskManagerEvent(info.getMucJid(),
                TaskManagerEvent.Type.TASK_STARTED));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fireEvent(new TaskManagerEvent(info.getMucJid(),
                TaskManagerEvent.Type.TASK_ABORTED));
        }
    }

    /**
     * Register an event listener to this <tt>JireconTask</tt>.
     * 
     * @param listener
     */
    public void addEventListener(JireconEventListener listener)
    {
        listeners.add(listener);
    }

    /**
     * Remove and event listener from this <tt>JireconTask</tt>.
     * 
     * @param listener
     */
    public void removeEventListener(JireconEventListener listener)
    {
        listeners.remove(listener);
    }

    /**
     * Get the task information.
     * 
     * @return The task information.
     */
    public TaskInfo getTaskInfo()
    {
        return info;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleEvent(TaskManagerEvent evt)
    {
        for (JireconEventListener l : listeners)
        {
            l.handleEvent(evt);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleTaskEvent(TaskEvent event)
    {
        logger.info("JireconTask event: " + event.getType());

        if (event.getType() == TaskEvent.Type.PARTICIPANT_CAME)
        {
            List<EndpointInfo> endpoints =
                jingleSessionMgr.getEndpoints();
            recorderMgr.setEndpoints(endpoints);
        }

        else if (event.getType() == TaskEvent.Type.PARTICIPANT_LEFT)
        {
            List<EndpointInfo> endpoints =
                jingleSessionMgr.getEndpoints();
            // Oh, it seems that all participants have left the MUC(except Jirecon
            // or other participants which only receive data). It's time to
            // finish the recording.
            if (endpoints.isEmpty())
            {
                stop();
                fireEvent(new TaskManagerEvent(info.getMucJid(),
                    TaskManagerEvent.Type.TASK_FINISED));
            }
            else
            {
                recorderMgr.setEndpoints(endpoints);
            }
        }
    }
    
    /**
     * Fire the event if the task has finished or aborted.
     * 
     * @param evt is the <tt>JireconEvent</tt> you want to notify the listeners.
     */
    private void fireEvent(TaskManagerEvent evt)
    {
        if (TaskManagerEvent.Type.TASK_ABORTED == evt.getType())
        {
            isAborted = true;
        }
        
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
            /*
             * Exception can only be thrown by Task.
             */
            Task.this.stop();
            fireEvent(new TaskManagerEvent(info.getMucJid(),
                TaskManagerEvent.Type.TASK_ABORTED));
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
            Thread t = new Thread(r);
            t.setUncaughtExceptionHandler(new ThreadExceptionHandler());
            return t;
        }
    }
}
