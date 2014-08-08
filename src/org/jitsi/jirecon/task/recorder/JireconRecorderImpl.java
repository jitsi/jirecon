/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.recorder;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.*;
import java.util.Map.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.media.rtp.SessionAddress;

import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.impl.neomedia.RTPConnectorUDPImpl;
import org.jitsi.impl.neomedia.RawPacket;
import org.jitsi.impl.neomedia.recording.*;
import org.jitsi.impl.neomedia.rtp.translator.RTPTranslatorImpl;
import org.jitsi.impl.neomedia.transform.dtls.DtlsPacketTransformer;
import org.jitsi.impl.neomedia.transform.dtls.DtlsTransformEngine;
import org.jitsi.jirecon.task.*;
import org.jitsi.sctp4j.NetworkLink;
import org.jitsi.sctp4j.Sctp;
import org.jitsi.sctp4j.SctpDataCallback;
import org.jitsi.sctp4j.SctpNotification;
import org.jitsi.sctp4j.SctpSocket;
import org.jitsi.sctp4j.SctpSocket.NotificationListener;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.neomedia.recording.*;
import org.jitsi.util.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * An implementation of <tt>JireconRecorder</tt>.
 * <p>
 * <tt>JireconRecorderImpl</tt> will record the media streams that you
 * specified, besides, it also records a meta data file which is used for
 * post-proceeding.
 * 
 * @author lishunyang
 * @see JireconRecorder
 * 
 */
public class JireconRecorderImpl
    implements JireconRecorder
{
    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger
        .getLogger(JireconRecorderImpl.class);
    
    /**
     * The map between <tt>MediaType</tt> and <tt>MediaStream</tt>. Those are
     * used to receiving media streams.
     */
    private Map<MediaType, MediaStream> streams =
        new HashMap<MediaType, MediaStream>();

    /**
     * The instance of <tt>MediaService</tt>.
     */
    private MediaService mediaService;

    /**
     * The map between <tt>MediaType</tt> and <tt>RTPTranslator</tt>. Those are
     * used to initialize recorder.
     */
    private Map<MediaType, RTPTranslator> rtpTranslators =
        new HashMap<MediaType, RTPTranslator>();

    /**
     * The map between <tt>MediaType</tt> and <tt>Recorder</tt>. Those are used
     * to record media streams into local files.
     */
    private Map<MediaType, Recorder> recorders =
        new HashMap<MediaType, Recorder>();
    
    /**
     * TODO
     */
    private SctpConnection dataChannel;
    
    private RecorderEventHandlerImpl eventHandler;

    /**
     * The <tt>JireconTaskEventListener</tt>, if <tt>JireconRecorder</tt> has
     * something important, it will notify them.
     */
    private List<JireconTaskEventListener> listeners =
        new ArrayList<JireconTaskEventListener>();

    private List<JireconEndpoint> endpoints =
        new ArrayList<JireconEndpoint>();

    /**
     * Map between <tt>MediaType</tt> and local recorder's ssrc.
     */
    private Map<MediaType, Long> localSsrcs = new HashMap<MediaType, Long>();

    /**
     * Whether the <tt>JireconRecorderImpl</tt> is receiving streams.
     */
    private boolean isReceiving = false;

    /**
     * Whether the <tt>JireconRecorderImpl</tt> is recording streams.
     */
    private boolean isRecording = false;

    /**
     * Indicate where <tt>JireconRecorderImpl</tt> will put the local files.
     */
    private String outputDir;

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(String outputDir, Map<MediaType, SrtpControl> srtpControls) 
    {
        this.mediaService = LibJitsi.getMediaService();
        this.outputDir = outputDir;

        // SrtpControl will be managed by MediaStream. 
        createMediaStreams(srtpControls);
        createDataChannel(srtpControls.get(MediaType.DATA));
        
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startRecording(
        Map<MediaType, Map<MediaFormat, Byte>> formatAndDynamicPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets)
        throws OperationFailedException
    {
        eventHandler =
            new RecorderEventHandlerImpl(outputDir + "/metadata.json");
        
        prepareMediaStreams(formatAndDynamicPTs, connectors, targets);
        openDataChannel(connectors.get(MediaType.DATA), targets.get(MediaType.DATA));
        startReceivingStreams();
        prepareRecorders();
        startRecordingStreams();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopRecording()
    {
        stopRecordingStreams();
        stopReceivingStreams();
        closeDataChannel();
        // stopTranslators();
    }

    /**
     * Make all <tt>JireconRecorderImpl</tt> ready to start receiving media
     * streams.
     * 
     * @param formatAndPTs
     * @param connectors is the map between <tt>MediaType</tt> and
     *            <tt>StreamConnector</tt>. Those connectors are used to
     *            transfer stream data.
     * @param targets is the map between <tt>MediaType</tt> and
     *            <tt>MediaStreamTarget</tt>. The target indicate media stream
     *            source.
     * @throws OperationFailedException if some operation failed and the
     *             preparation is aborted.
     */
    private void prepareMediaStreams(
        Map<MediaType, Map<MediaFormat, Byte>> formatAndPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets)
        throws OperationFailedException
    {
        logger.info("prepareMediaStreams");

        for (Entry<MediaType, MediaStream> e : streams.entrySet())
        {
            final MediaType mediaType = e.getKey();
            final MediaStream stream = e.getValue();
            
            stream.setConnector(connectors.get(mediaType));
            stream.setTarget(targets.get(mediaType));
            
            for (Entry<MediaFormat, Byte> f : formatAndPTs.get(mediaType)
                .entrySet())
            {
                stream.addDynamicRTPPayloadType(f.getValue(), f.getKey());
                if (null == stream.getFormat())
                    stream.setFormat(f.getKey());
            }

            stream.setRTPTranslator(getTranslator(mediaType));
        }
    }

    /**
     * Make the <tt>JireconRecorderImpl</tt> ready to start recording media
     * streams.
     * 
     * @throws OperationFailedException if some operation failed and the
     *             preparation is aborted.
     */
    private void prepareRecorders() 
        throws OperationFailedException
    {
        logger.info("prepareRecorders");

        for (Entry<MediaType, RTPTranslator> e : rtpTranslators.entrySet())
        {
            Recorder recorder = mediaService.createRecorder(e.getValue());
            recorders.put(e.getKey(), recorder);
        }
    }
    
    private void openDataChannel(StreamConnector connector,
        MediaStreamTarget streamTarget)
    {
        if (null == connector || null == streamTarget)
        {
            logger.debug("Ignore data channel");
            return;
        }
        
        dataChannel.connect(connector, streamTarget);
    }
    
    /**
     * Start receiving media streams.
     * 
     * @throws OperationFailedException if some operation failed and the
     *             receiving is aborted.
     */
    private void startReceivingStreams() 
        throws OperationFailedException
    {
        logger.info("startReceiving");

        int startCount = 0;
        for (Entry<MediaType, MediaStream> e : streams.entrySet())
        {
            MediaStream stream = e.getValue();
            stream.getSrtpControl().start(e.getKey());
            stream.start();
            if (stream.isStarted())
            {
                startCount += 1;
            }
        }

        // If any media stream failed to start, the starting procedure failed.
        if (streams.size() != startCount)
        {
            throw new OperationFailedException(
                "Could not start receiving streams",
                OperationFailedException.GENERAL_ERROR);
        }
        isReceiving = true;
    }

    /**
     * Start recording media streams.
     * 
     * @throws OperationFailedException if some operation failed and the
     *             recording is aborted.
     */
    private void startRecordingStreams() throws OperationFailedException
    {
        logger.info("startRecording");
        if (!isReceiving)
        {
            throw new OperationFailedException(
                "Could not start recording streams, media streams are not receiving.",
                OperationFailedException.GENERAL_ERROR);
        }
        if (isRecording)
        {
            throw new OperationFailedException(
                "Could not start recording streams, recorders are already recording.",
                OperationFailedException.GENERAL_ERROR);
        }

        for (Entry<MediaType, Recorder> entry : recorders.entrySet())
        {
            final Recorder recorder = entry.getValue();
            recorder.setEventHandler(eventHandler);
            try
            {
                recorder.start(entry.getKey().toString(), outputDir);
            }
            catch (Exception e)
            {
                throw new OperationFailedException(
                    "Could not start recording streams, " + e.getMessage(),
                    OperationFailedException.GENERAL_ERROR);
            }
        }
        isRecording = true;
    }
    
    private void closeDataChannel()
    {
        dataChannel.disconnect();
    }
    
    /**
     * Stop recording media streams.
     */
    private void stopRecordingStreams()
    {
        logger.info("Stop recording streams.");
        if (!isRecording)
            return;

        for (Entry<MediaType, Recorder> e : recorders.entrySet())
        {
            e.getValue().stop();
        }
        recorders.clear();
        isRecording = false;
    }

    /**
     * Stop receiving media streams.
     */
    private void stopReceivingStreams()
    {
        logger.info("Stop receiving streams");
        if (!isReceiving)
            return;

        for (Map.Entry<MediaType, MediaStream> e : streams.entrySet())
        {
            e.getValue().close();
        }
        
        streams.clear();
        isReceiving = false;
    }

    /**
     * Stop the RTP translators.
     */
    private void stopTranslators()
    {
        for (Entry<MediaType, RTPTranslator> e : rtpTranslators.entrySet())
        {
            e.getValue().dispose();
        }
        rtpTranslators.clear();
    }
    
    private void createDataChannel(SrtpControl srtpControl)
    {
        dataChannel = new SctpConnection((DtlsControl) srtpControl);
    }

    /**
     * Create media streams. After media streams are created, we can get ssrcs
     * of them.
     * <p>
     * <strong>Warning:</strong> We can only add <tt>SrtpControl</tt> to
     * <tt>MediaStream</tt> at this moment.
     * 
     * @param srtpControls is the map between <tt>MediaType</tt> and
     *            <tt>SrtpControl</tt>.
     */
    private void createMediaStreams(Map<MediaType, SrtpControl> srtpControls)
    {
        logger.info("createMediaStreams");
        for (MediaType mediaType : new MediaType[] {MediaType.AUDIO, MediaType.VIDEO})
        {
            final MediaStream stream =
                mediaService.createMediaStream(null, mediaType,
                    srtpControls.get(mediaType));
            streams.put(mediaType, stream);

            stream.setName(mediaType.toString());
            stream.setDirection(MediaDirection.RECVONLY);
        }
    }

    /**
     * Get a <tt>RTPTranslator</tt> for a specified <tt>MediaType</tt>. Create a
     * new one if it doesn't exist.
     * 
     * @param mediaType is the <tt>MediaType</tt> that you specified.
     * @return <tt>RTPTranslator</tt>
     */
    private RTPTranslator getTranslator(MediaType mediaType)
    {
        RTPTranslator translator = null;
        if (rtpTranslators.containsKey(mediaType))
            translator = rtpTranslators.get(mediaType);
        else
        {
            translator = mediaService.createRTPTranslator();
            // I have to do the casting, because RTPTranslator interface doesn't
            // have that method.
            ((RTPTranslatorImpl) translator).setLocalSSRC(localSsrcs
                .get(mediaType));
            rtpTranslators.put(mediaType, translator);
        }
        return translator;
    }

    /**
     * Find and get the <tt>MediaType</tt> ssrc which belongs to some endpoint.
     * <strong>Warning:</strong> An endpoint means a media stream source, each
     * media stream source generally contains two ssrc, one for audio stream and
     * one for video stream.
     * 
     * @param ssrc indicates an endpoint.
     * @param mediaType is the <tt>MediaType</tt> which indicates which ssrc you
     *            want to get.
     * @return ssrc or -1 if not found
     */
    private long getAssociatedSsrc(long ssrc, MediaType mediaType)
    {
        synchronized (endpoints)
        {
            for (JireconEndpoint endpoint : endpoints)
            {
                Map<MediaType, Long> ssrcs = endpoint.getSsrcs();
                
                if (ssrcs.size() < 2)
                    continue;

                if (ssrcs.containsValue(ssrc))
                {
                    return ssrcs.get(mediaType);
                }
            }
        }

        return -1;
    }
    
    private long getEndpointSsrc(String endpointId, MediaType mediaType)
    {
        synchronized (endpoints)
        {
            for (JireconEndpoint endpoint : endpoints)
            {
                if (0 == endpoint.getId().compareTo(endpointId)
                    || 0 == endpoint.getBareId().compareTo(endpointId))
                {
                    return endpoint.getSsrc(mediaType);
                }
            }
            
            return -1;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEndpoints(List<JireconEndpoint> newEndpoints)
    {
        synchronized (endpoints)
        {
            endpoints = newEndpoints;
            for (JireconEndpoint endpoint : endpoints)
            {
                final String endpointId = endpoint.getId();
                for (Entry<MediaType, Long> ssrc : endpoint.getSsrcs().entrySet())
                {
                    Recorder recorder = recorders.get(ssrc.getKey());
                    Synchronizer synchronizer = recorder.getSynchronizer();
                    synchronizer.setEndpoint(ssrc.getValue(), endpointId);

                    logger.info("endpoint: " + endpointId + " "
                        + ssrc.getKey() + " " + ssrc.getValue());
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addTaskEventListener(JireconTaskEventListener listener)
    {
        synchronized (listeners)
        {
            listeners.add(listener);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeTaskEventListener(JireconTaskEventListener listener)
    {
        synchronized (listeners)
        {
            listeners.remove(listener);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<MediaType, Long> getLocalSsrcs()
    {
        if (!localSsrcs.isEmpty())
            return localSsrcs;

        synchronized (streams)
        {
            for (Entry<MediaType, MediaStream> entry : streams.entrySet())
            {
                localSsrcs.put(entry.getKey(), entry.getValue()
                    .getLocalSourceID() & 0xFFFFFFFFL);
            }
        }
        return localSsrcs;
    }

    /**
     * Fire a <tt>JireconTaskEvent</tt>, notify listeners we've made new
     * progress which they may interest in.
     * 
     * @param event
     */
    private void fireEvent(JireconTaskEvent event)
    {
        synchronized (listeners)
        {
            for (JireconTaskEventListener l : listeners)
                l.handleTaskEvent(event);
        }
    }

    /**
     * An implementation of <tt>RecorderEventHandler</tt>. It is mainly used for
     * recording SPEAKER_CHANGED event in to meta data file.
     * 
     * @author lishunyang
     * 
     */
    private class RecorderEventHandlerImpl
        implements RecorderEventHandler
    {
        /**
         * The true <tt>RecorderEventHandler</tt> which is used for handling
         * event actually.
         */
        private RecorderEventHandler handler;

        /**
         * The construction method for creating
         * <tt>JireconRecorderEventHandler</tt>.
         * 
         * @param filename the meta data file's name.
         * @throws OperationFailedException if failed to create handler
         */
        public RecorderEventHandlerImpl(String filename)
            throws OperationFailedException
        {
            // If there is an existed file with "filename", add suffix to
            // "filename". For instance, from "metadata.json" to
            // "metadata.json-1".
            int count = 1;
            String filenameAvailable = filename;
            File file = null;
            while (true)
            {
                file = new File(filenameAvailable);
                
                try
                {
                    handler = new RecorderEventHandlerJSONImpl(filenameAvailable);
                    break;
                }
                catch (IOException e)
                {
                    if (file.exists())
                    {
                        filenameAvailable = filename + "-" + count++;
                    }
                    else
                    {
                        throw new OperationFailedException(
                            "Could not create event handler, no write permission to meta file.",
                            OperationFailedException.GENERAL_ERROR);
                    }
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close()
        {
            logger.info("close");
        }

        /**
         * Handle event.
         */
        @Override
        public synchronized boolean handleEvent(RecorderEvent event)
        {
            logger.debug(event + " ssrc:" + event.getSsrc());
            
            RecorderEvent.Type type = event.getType();

            if (RecorderEvent.Type.SPEAKER_CHANGED.equals(type))
            {
                /*
                 * We have to use audio ssrc instead endpoint id to find video
                 * ssrc because of the compatibility.
                 */
                logger.debug("SPEAKER_CHANGED audio ssrc: "
                    + event.getAudioSsrc());
                final long audioSsrc = event.getAudioSsrc();
                final long videoSsrc =
                    getAssociatedSsrc(audioSsrc, MediaType.VIDEO);
                if (videoSsrc < 0)
                {
                    logger
                        .fatal("Could not find video SSRC associated with audioSsrc="
                            + audioSsrc);

                    // don't write events without proper 'ssrc' values
                    return false;
                }

                // for the moment just use the first SSRC
                event.setSsrc(videoSsrc);
            }
            return handler.handleEvent(event);
        }
    }
    
    private class SctpConnection
        implements SctpDataCallback, NotificationListener
    {
        private ExecutorService executorService;

        private DtlsControl dtlsControl;

        private SctpSocket sctpSocket;
        
        public SctpConnection(DtlsControl dtlsControl)
        {
            this.dtlsControl = dtlsControl;

            executorService =
                Executors.newSingleThreadExecutor(new HandlerThreadFactory());
        }

        public void connect(final StreamConnector connector,
            final MediaStreamTarget streamTarget)
        {
            logger.debug(connector.getDataSocket().getLocalAddress()
                .getHostName()
                + ", "
                + connector.getDataSocket().getLocalPort()
                + "----->"
                + streamTarget.getDataAddress().getHostName()
                + ", "
                + streamTarget.getDataAddress().getPort());

            dtlsControl.start(MediaType.DATA);

            executorService.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        Sctp.init();
                        
                        RTPConnectorUDPImpl rtpConnector =
                            new RTPConnectorUDPImpl(connector);

                        rtpConnector.addTarget(new SessionAddress(streamTarget
                            .getDataAddress().getAddress(), streamTarget
                            .getDataAddress().getPort()));

                        dtlsControl.setConnector(rtpConnector);

                        DtlsTransformEngine engine =
                            (DtlsTransformEngine) dtlsControl
                                .getTransformEngine();
                        final DtlsPacketTransformer transformer =
                            (DtlsPacketTransformer) engine.getRTPTransformer();

                        sctpSocket = Sctp.createSocket(5000);
                        sctpSocket.setNotificationListener(SctpConnection.this);
                        sctpSocket.setDataCallback(SctpConnection.this);

                        // Receive loop, breaks when SCTP socket is closed
                        DatagramSocket iceUdpSocket =
                            rtpConnector.getDataSocket();
                        byte[] receiveBuffer = new byte[2035];
                        DatagramPacket rcvPacket =
                            new DatagramPacket(receiveBuffer, 0,
                                receiveBuffer.length);

                        sctpSocket.setLink(new NetworkLink()
                        {
                            private final RawPacket rawPacket = new RawPacket();

                            @Override
                            public void onConnOut(
                                org.jitsi.sctp4j.SctpSocket s, byte[] packet)
                                throws IOException
                            {
                                // Send through DTLS transport
                                rawPacket.setBuffer(packet);
                                rawPacket.setLength(packet.length);

                                transformer.transform(rawPacket);
                            }
                        });

                        sctpSocket.connect(5000);

                        while (true)
                        {
                            iceUdpSocket.receive(rcvPacket);

                            RawPacket raw =
                                new RawPacket(rcvPacket.getData(), rcvPacket
                                    .getOffset(), rcvPacket.getLength());

                            raw = transformer.reverseTransform(raw);

                            // Check for app data
                            if (raw == null)
                                continue;
                            
                            // Pass network packet to SCTP stack
                            sctpSocket.onConnIn(raw.getBuffer(),
                                raw.getOffset(), raw.getLength());
                        }

                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                        fireEvent(new JireconTaskEvent(
                            JireconTaskEvent.Type.RECORDER_ABORTED));
                    }
                }
            });
        }

        public void disconnect()
        {
            if (sctpSocket != null)
                sctpSocket.close();
            
            dtlsControl.cleanup();
            
            // We shouldn't finish SCTP, because other JireconTask may be using it.
        }

        @Override
        public void onSctpPacket(byte[] data, int sid, int ssn, int tsn,
            long ppid, int context, int flags)
        {
            String dataStr = new String(data);
            JSONParser parser = new JSONParser();
            
            try
            {
                JSONObject json = (JSONObject) parser.parse(dataStr);
                String endpointId = json.get("dominantSpeakerEndpoint").toString();
                
                logger.debug("Hey! " + endpointId);
                
              RecorderEvent event = new RecorderEvent();
              event.setMediaType(MediaType.AUDIO); // Is that suitable?
              event.setType(RecorderEvent.Type.SPEAKER_CHANGED);
              event.setEndpointId(endpointId);
              event.setAudioSsrc(getEndpointSsrc(endpointId, MediaType.AUDIO));
              eventHandler.handleEvent(event);
            }
            catch (ParseException e)
            {
                e.printStackTrace();
            }
            

        }

        @Override
        public void onSctpNotification(SctpSocket socket,
            SctpNotification notification)
        {
            logger.debug("SCTP Notification: " + notification);

            if (notification.sn_type == SctpNotification.SCTP_ASSOC_CHANGE)
            {
                SctpNotification.AssociationChange assocChange =
                    (SctpNotification.AssociationChange) notification;

                switch (assocChange.state)
                {
                case SctpNotification.AssociationChange.SCTP_COMM_UP:
                    break;
                case SctpNotification.AssociationChange.SCTP_COMM_LOST:
                case SctpNotification.AssociationChange.SCTP_SHUTDOWN_COMP:
                case SctpNotification.AssociationChange.SCTP_CANT_STR_ASSOC:
                    break;
                }
            }
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
                fireEvent(new JireconTaskEvent(
                    JireconTaskEvent.Type.RECORDER_ABORTED));
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
