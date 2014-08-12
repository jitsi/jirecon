/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;

import org.jitsi.jirecon.*;
import org.jitsi.jirecon.dtlscontrol.*;
import org.jitsi.jirecon.task.recorder.*;
import org.jitsi.jirecon.task.session.*;
import org.jitsi.jirecon.transport.*;
import org.jitsi.jirecon.utils.*;
import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.service.libjitsi.LibJitsi;
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
    implements JireconTask, 
    JireconEventListener, 
    JireconTaskEventListener,
    Runnable
{
    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger
        .getLogger(JireconTaskImpl.class.getName());
    
    /**
     * The <tt>JireconEvent</tt> listeners, they will be notified when some
     * important things happen.
     */
    private List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    /**
     * The instance of <tt>JireconSession</tt>.
     */
    private JingleSessionManager session;

    /**
     * The instance of <tt>JireconTransportManager</tt>.
     */
    private IceUdpTransportManager transport;

    /**
     * The instance of <tt>DtlsControlManager</tt>.
     */
    private DtlsControlManager dtlsControl;
    
    /**
     * The instance of <tt>JireconRecorder</tt>.
     */
    private JireconRecorder recorder;

    /**
     * The thread pool to make the method "start" to be asynchronous.
     */
    private ExecutorService executorService;

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
    private JireconTaskInfo info = new JireconTaskInfo();
    
    /**
     * {@inheritDoc}
     */
    @Override
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
            .getString(JireconConfigurationKey.NICK_KEY));

        executorService =
            Executors.newSingleThreadExecutor(new HandlerThreadFactory());

        transport = new IceUdpTransportManager();

        dtlsControl = new DtlsControlManager();
        dtlsControl.setHashFunction(configuration
            .getString(JireconConfigurationKey.HASH_FUNCTION_KEY));

        session = new JingleSessionManager();
        session.addTaskEventListener(this);
        session.init(connection);

        recorder = new JireconRecorderImpl();
        recorder.addTaskEventListener(this);
        recorder.init(savingDir, dtlsControl.getAllDtlsControl());
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
        
        info = new JireconTaskInfo();
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
            transport.free();
            recorder.stopRecording();
            session.disconnect(Reason.SUCCESS, "OK, gotta go.");
            isStopped = true;
            
            /*
             * We should only fire TASK_FINISHED event when the task has really
             * finished, because when task is aborted, this "stop" method will
             * also be called, and in this scene we shouldn't fire TASK_FINISHED
             * event.
             */
            if (!isAborted)
                fireEvent(new JireconEvent(info.getMucJid(),
                    JireconEvent.Type.TASK_FINISED));
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
            session.joinMUC(info.getMucJid(), info.getNickname());
            
            /* 2. Wait for session-init packet. */
            JingleIQ initIq = session.waitForInitPacket();
            List<MediaType> supportedMediaTypes =
                JinglePacketParser.getSupportedMediaTypes(initIq);
            
            /*
             * 3. Harvest local candidates. (audio type, video type and data
             * type.)
             */
            for (MediaType mediaType : supportedMediaTypes)
            {
                transport.harvestLocalCandidates(mediaType);
            }

            /*
             * 4.1 Prepare for sending session-accept packet.
             */
            Map<MediaType, Map<MediaFormat, Byte>> formatAndPTs = new HashMap<MediaType, Map<MediaFormat, Byte>>();
            for (MediaType mediaType : new MediaType[] {MediaType.AUDIO, MediaType.VIDEO})
            {
                formatAndPTs.put(mediaType, JinglePacketParser
                    .getFormatAndDynamicPTs(initIq, mediaType));
            }

            Map<MediaType, Long> localSsrcs = recorder.getLocalSsrcs();
            
            // Transport packet extension.
            Map<MediaType, AbstractPacketExtension> transportPEs =
                new HashMap<MediaType, AbstractPacketExtension>();
            for (MediaType mediaType : supportedMediaTypes)
            {
                transportPEs.put(mediaType,
                    transport.getTransportPacketExt(mediaType));
            }

            // Fingerprint packet extension.
            Map<MediaType, AbstractPacketExtension> fingerprintPEs =
                new HashMap<MediaType, AbstractPacketExtension>();
            for (MediaType mediaType : supportedMediaTypes)
            {
                fingerprintPEs.put(mediaType,
                    dtlsControl.getFingerprintPacketExt(mediaType));
            }

            /* 4.2 Send session-accept packet. */
            session.sendAcceptPacket(formatAndPTs, localSsrcs, transportPEs,
                fingerprintPEs);

            /* 4.3 Wait for session-ack packet. */
            session.waitForResultPacket();

            /*
             * 5.1 Prepare for ICE connectivity establishment. Harvest remote
             * candidates.
             */
            Map<MediaType, IceUdpTransportPacketExtension> remoteTransportPEs = new HashMap<MediaType, IceUdpTransportPacketExtension>();
            for (MediaType mediaType : supportedMediaTypes)
            {
                remoteTransportPEs.put(mediaType, JinglePacketParser.getTransportPacketExt(initIq, mediaType));
            }
            transport.harvestRemoteCandidates(remoteTransportPEs);

            /*
             * 5.2 Start establishing ICE connectivity. Warning: that this
             * method is asynchronous method.
             */
            transport.startConnectivityEstablishment();

            /*
             * 6.1 Prepare for recording. Once transport manager has selected
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
                    transport.getStreamConnector(mediaType);
                streamConnectors.put(mediaType, streamConnector);

                MediaStreamTarget mediaStreamTarget =
                    transport.getStreamTarget(mediaType);
                mediaStreamTargets.put(mediaType, mediaStreamTarget);
            }
            
            /* 6.2 Start recording. */
            recorder.startRecording(formatAndPTs, streamConnectors,
                mediaStreamTargets);
            
            fireEvent(new JireconEvent(info.getMucJid(),
                JireconEvent.Type.TASK_STARTED));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fireEvent(new JireconEvent(info.getMucJid(),
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
        for (JireconEventListener l : listeners)
        {
            l.handleEvent(evt);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleTaskEvent(JireconTaskEvent event)
    {
        logger.info("JireconTask event: " + event.getType());

        if (event.getType() == JireconTaskEvent.Type.PARTICIPANT_CAME)
        {
            List<JireconEndpoint> endpoints =
                session.getEndpoints();
            recorder.setEndpoints(endpoints);
        }

        else if (event.getType() == JireconTaskEvent.Type.PARTICIPANT_LEFT)
        {
            List<JireconEndpoint> endpoints =
                session.getEndpoints();
            // Oh, it seems that all participants have left the MUC(except Jirecon
            // or other participants which only receive data). It's time to
            // finish the recording.
            if (endpoints.isEmpty())
            {
                stop();
                fireEvent(new JireconEvent(info.getMucJid(),
                    JireconEvent.Type.TASK_FINISED));
            }
            else
            {
                recorder.setEndpoints(endpoints);
            }
        }
    }
    
    /**
     * Fire the event if the task has finished or aborted.
     * 
     * @param evt is the <tt>JireconEvent</tt> you want to notify the listeners.
     */
    private void fireEvent(JireconEvent evt)
    {
        if (JireconEvent.Type.TASK_ABORTED == evt.getType())
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
            if (t instanceof JireconTask)
            {
                ((JireconTask) e).stop();
                fireEvent(new JireconEvent(info.getMucJid(),
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
            Thread t = new Thread(r);
            t.setUncaughtExceptionHandler(new ThreadExceptionHandler());
            return t;
        }
    }
}
