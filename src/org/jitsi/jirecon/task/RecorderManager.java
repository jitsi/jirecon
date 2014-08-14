/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.Map.*;
import java.util.concurrent.*;
import javax.media.rtp.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.recording.*;
import org.jitsi.impl.neomedia.rtp.translator.*;
import org.jitsi.impl.neomedia.transform.dtls.*;
import org.jitsi.jirecon.task.TaskEvent.*;
import org.jitsi.sctp4j.*;
import org.jitsi.sctp4j.SctpSocket.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.neomedia.recording.*;
import org.jitsi.util.*;
import org.json.simple.*;
import org.json.simple.parser.*;

 /**
 * <tt>RecorderManager</tt> is used to record media
 * streams and save them into local files.
 * 
 * @author lishunyang
 */
public class RecorderManager
{
    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger
        .getLogger(RecorderManager.class);

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
     * SCTP data channel. It's used for receiving some event packets, such as
     * SPEAKER_CHANGE event.
     */
    private SctpConnection dataChannel;

    /**
     * Used for handling recorder's event.
     */
    private RecorderEventHandlerImpl eventHandler;

    /**
     * The <tt>JireconTaskEventListener</tt>, if <tt>JireconRecorder</tt> has
     * something important, it will notify them.
     */
    private List<TaskEventListener> listeners =
        new ArrayList<TaskEventListener>();

    /**
     * Active endpoints in the meeting currently.
     */
    private List<EndpointInfo> endpoints = new ArrayList<EndpointInfo>();

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
     * Initialize <tt>JireconRecorder</tt>.
     * <p>
     * <strong>Warning:</strong> LibJitsi must be started before calling this
     * method.
     * 
     * @param outputDir decide where to output the files. The directory must be
     *            existed and writable.
     * @param dtlsControls is the map between <tt>MediaType</tt> and
     *            <tt>DtlsControl</tt> which is used for SRTP transfer.
     */
    public void init(String outputDir, Map<MediaType, DtlsControl> dtlsControls)
    {
        this.mediaService = LibJitsi.getMediaService();
        this.outputDir = outputDir;
        logger.setLevelAll();

        /*
         * NOTE: DtlsControl will be managed by MediaStream. So we don't need to
         * open/close DtlsControl addtionally.
         */
        createMediaStreams(dtlsControls);
        createDataChannel(dtlsControls.get(MediaType.DATA));

    }

    /**
     * Start recording media streams.
     * 
     * @param formatAndPTs
     * @param connectors is the map between <tt>MediaType</tt> and
     *            <tt>StreamConnector</tt>. <tt>JireconRecorder</tt> needs those
     *            connectors to transfer stream data.
     * @param targets is the map between <tt>MediaType</tt> and
     *            <tt>MediaStreamTarget</tt>. Every target indicates a media
     *            source.
     * @throws Exception if some operation failed and the
     *             recording is aborted.
     */
    public void startRecording(
        Map<MediaType, Map<MediaFormat, Byte>> formatAndDynamicPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets)
        throws Exception
    {
        /*
         * Here we don't garuantee whether file path is available.
         * RecorderEventHandlerImpl needs check this and do some job.
         */
        final String filename = "metadata.json";
        eventHandler = new RecorderEventHandlerImpl(outputDir + "/" + filename);

        /*
         * 1. Open sctp data channel, if there is data connector and target.
         */
        openDataChannel(connectors.get(MediaType.DATA),
            targets.get(MediaType.DATA));

        /*
         * 2. Prepare audio and video media streams.
         */
        prepareMediaStreams(formatAndDynamicPTs, connectors, targets);

        /*
         * 3. Start receiving audio and video streams
         */
        startReceivingStreams();

        /*
         * 4. Prepare audio and video recorders.
         */
        prepareRecorders();

        /*
         * 5. Start recording audio and video streams.
         */
        startRecordingStreams();
    }

    /**
     * Stop the recording.
     */
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
     * @throws Exception if some operation failed and the
     *             preparation is aborted.
     */
    private void prepareMediaStreams(
        Map<MediaType, Map<MediaFormat, Byte>> formatAndPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets)
        throws Exception
    {
        logger.debug("prepareMediaStreams");

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
     * @throws Exception if some operation failed and the
     *             preparation is aborted.
     */
    private void prepareRecorders() throws Exception
    {
        logger.debug("prepareRecorders");

        for (Entry<MediaType, RTPTranslator> e : rtpTranslators.entrySet())
        {
            Recorder recorder = mediaService.createRecorder(e.getValue());
            recorders.put(e.getKey(), recorder);
        }
    }

    /**
     * Open data channel, build SCTP connection with remote sctp server.
     * <p>
     * If there any parameters is null, data channel won't be openned.
     * 
     * @param connector Data stream connector
     * @param streamTarget Data stream target
     */
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
     * @throws Exception if some operation failed and the
     *             receiving is aborted.
     */
    private void startReceivingStreams() throws Exception
    {
        logger.debug("startReceiving");

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
            throw new Exception("Could not start receiving streams");
        }
        isReceiving = true;
    }

    /**
     * Start recording media streams.
     * 
     * @throws Exception if some operation failed and the
     *             recording is aborted.
     */
    private void startRecordingStreams() throws Exception
    {
        logger.debug("startRecording");
        
        if (!isReceiving)
        {
            throw new Exception(
                "Could not start recording streams, media streams are not receiving.");
        }
        if (isRecording)
        {
            throw new Exception(
                "Could not start recording streams, recorders are already recording.");
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
                throw new Exception("Could not start recording streams, " + e.getMessage());
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
        logger.debug("Stop recording streams.");
        
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
        logger.debug("Stop receiving streams");
        
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
     * <p>
     * Actually we don't stop <tt>RTPTranslator</tt> manually, because it will
     * be closed in recorder.
     */
    private void stopTranslators()
    {
        for (Entry<MediaType, RTPTranslator> e : rtpTranslators.entrySet())
        {
            e.getValue().dispose();
        }
        rtpTranslators.clear();
    }

    /**
     * Create data channel. We need <tt>DtlsControl</tt> to initialize it.
     * 
     * @param dtlsControl
     */
    private void createDataChannel(DtlsControl dtlsControl)
    {
        dataChannel = new SctpConnection(dtlsControl);
    }

    /**
     * Create media streams. After media streams are created, we can get ssrcs
     * of them.
     * <p>
     * <strong>Warning:</strong> We can only add <tt>SrtpControl</tt> to
     * <tt>MediaStream</tt> at this moment.
     * 
     * @param dtlsControls is the map between <tt>MediaType</tt> and
     *            <tt>SrtpControl</tt>.
     */
    private void createMediaStreams(Map<MediaType, DtlsControl> dtlsControls)
    {
        logger.debug("createMediaStreams");
        
        for (MediaType mediaType : new MediaType[]
        { MediaType.AUDIO, MediaType.VIDEO })
        {
            final MediaStream stream =
                mediaService.createMediaStream(null, mediaType,
                    dtlsControls.get(mediaType));
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
            for (EndpointInfo endpoint : endpoints)
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

    /**
     * Find the specified <tt>MediaType<tt> ssrc which belongs to some endpont.
     * <strong>Warning:</strong> An endpoint means a media stream source, each
     * media stream source generally contains two ssrc, one for audio stream and
     * one for video stream.
     * 
     * @param endpointId indicates an endpoint
     * @param mediaType is the <tt>MediaType</tt> which indicates which ssrc you
     *            want to get.
     * @return ssrc or -1 if not found
     */
    private long getEndpointSsrc(String endpointId, MediaType mediaType)
    {
        synchronized (endpoints)
        {
            for (EndpointInfo endpoint : endpoints)
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
    public void setEndpoints(List<EndpointInfo> newEndpoints)
    {
        synchronized (endpoints)
        {
            endpoints = newEndpoints;
            for (EndpointInfo endpoint : endpoints)
            {
                final String endpointId = endpoint.getId();
                for (Entry<MediaType, Long> ssrc : endpoint.getSsrcs()
                    .entrySet())
                {
                    Recorder recorder = recorders.get(ssrc.getKey());
                    Synchronizer synchronizer = recorder.getSynchronizer();
                    synchronizer.setEndpoint(ssrc.getValue(), endpointId);

                    logger.info("endpoint: " + endpointId + " " + ssrc.getKey()
                        + " " + ssrc.getValue());
                }
            }
        }
    }

    /**
     * Add <tt>JireconTaskEvent</tt> listener.
     * 
     * @param listener
     */
    public void addTaskEventListener(TaskEventListener listener)
    {
        synchronized (listeners)
        {
            listeners.add(listener);
        }
    }

    /**
     * Remove <tt>JireconTaskEvent</tt> listener.
     * 
     * @param listener
     */
    public void removeTaskEventListener(TaskEventListener listener)
    {
        synchronized (listeners)
        {
            listeners.remove(listener);
        }
    }

    /**
     * Get local ssrcs of each <tt>MediaType</tt>.
     * 
     * @return Map between <tt>MediaType</tt> and ssrc.
     */
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
    private void fireEvent(TaskEvent event)
    {
        synchronized (listeners)
        {
            for (TaskEventListener l : listeners)
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
         * @throws Exception if failed to create handler
         */
        public RecorderEventHandlerImpl(String filename)
            throws Exception
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
                    handler =
                        new RecorderEventHandlerJSONImpl(filenameAvailable);
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
                        throw new Exception(
                            "Could not create event handler, no write permission to meta file.");
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
            logger.debug("close");
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

    /**
     * SCTP connection, which is used for receiving packets from data channel.
     * 
     * @author lishunyang
     * 
     */
    private class SctpConnection
        implements SctpDataCallback, NotificationListener
    {
        /**
         * Message type used to acknowledge WebRTC data channel allocation on SCTP
         * stream ID on which <tt>MSG_OPEN_CHANNEL</tt> message arrives.
         */
        private static final int MSG_CHANNEL_ACK = 0x2;
        
        /**
         * Message with this type sent over control PPID in order to open new WebRTC
         * data channel on SCTP stream ID that this message is sent.
         */
        private static final int MSG_OPEN_CHANNEL = 0x3;
        
        private final byte[] MSG_CHANNEL_ACK_BYTES
        = new byte[] { MSG_CHANNEL_ACK };
        
        /**
         * Payload protocol id for control data. Used for <tt>WebRtcDataStream</tt>
         * allocation.
         */
        static final int WEB_RTC_PPID_CTRL = 50;
        
        /**
         * Payload protocol id that identifies text data UTF8 encoded in WebRTC data
         * channels.
         */
        static final int WEB_RTC_PPID_STRING = 51;
        
        /**
         * Payload protocol id that identifies binary data in WebRTC data channel.
         */
        static final int WEB_RTC_PPID_BIN = 53;
        
        private ExecutorService sctpExecutor;

        private DtlsControl dtlsControl;

        private SctpSocket sctpSocket;

        /**
         * Construction method.
         * 
         * @param dtlsControl
         */
        public SctpConnection(DtlsControl dtlsControl)
        {
            this.dtlsControl = dtlsControl;

            sctpExecutor =
                Executors.newSingleThreadExecutor(new HandlerThreadFactory());
        }

        /**
         * Build SCTP connection with remote SCTP server.
         * 
         * @param connector
         * @param streamTarget
         */
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

            sctpExecutor.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    DatagramSocket iceUdpSocket = null;
                    
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
                        iceUdpSocket =
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

                        /*
                         * Actually the port can be any number, but let's keep
                         * it 5000.
                         */
                        final int port = 5000;
                        /*
                         * NOTE: This method is asynchronrous and it may take
                         * some time to build connection.
                         */
                        sctpSocket.connect(port);

                        
                        while (!iceUdpSocket.isClosed())
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
                        /*
                         * We need to guarentee that socket is opened, becase
                         * when someone stop the recording task, udp socket will
                         * be closed, and this will cause an exception thrown by
                         * receive method. But in this case, we shouldn't throw
                         * exception.
                         */
                        if (!iceUdpSocket.isClosed())
                        {
                            e.printStackTrace();
                            fireEvent(new TaskEvent(
                                TaskEvent.Type.RECORDER_ABORTED));
                        }
                    }
                }
            });
        }

        /**
         * Shut down SCTP connection.
         */
        public void disconnect()
        {
            if (sctpSocket != null)
                sctpSocket.close();

            dtlsControl.cleanup();

            /*
             * NOTE: We shouldn't shutdown SCTP, because other JireconTask may be using
             * it. SCTP util is global.
             */
        }

        /**
         * {@inheritDoc} Receive the SCTP packets and parse into event string,
         * then record them.
         */
        @Override
        public void onSctpPacket(byte[] data, int sid, int ssn, int tsn,
            long ppid, int context, int flags)
        {
            System.out.println("Everybody freeze! " + ppid);
            for (byte b : data)
            {
                // System.out.printf("%03d ", b);
                System.out.printf("%x ", b);
            }
            System.out.println();

            if (ppid == WEB_RTC_PPID_CTRL)
            {
                // Channel control PPID
                try
                {
                    onCtrlPacket(data, sid);
                }
                catch (IOException e)
                {
                    logger.error("IOException when processing ctrl packet", e);
                }
            }
            else if (ppid == WEB_RTC_PPID_STRING || ppid == WEB_RTC_PPID_BIN)
            {

                ByteBuffer buffer = ByteBuffer.wrap(data);
                int messageType =
                    /* 1 byte unsigned integer */0xFF & buffer.get();
                System.out.println(messageType);

                /*
                 * We only care about SPEAKER_CHANGE event.
                 */

                try
                {
                    String dataStr = new String(data, "UTF-8");
                    JSONParser parser = new JSONParser();
                    JSONObject json = (JSONObject) parser.parse(dataStr);
                    String endpointId =
                        json.get("dominantSpeakerEndpoint").toString();

                    logger.debug("Hey! " + endpointId);
                    System.out.println("Hey! " + endpointId);

                    RecorderEvent event = new RecorderEvent();
                    event.setMediaType(MediaType.AUDIO);
                    event.setType(RecorderEvent.Type.SPEAKER_CHANGED);
                    event.setEndpointId(endpointId);
                    event.setAudioSsrc(getEndpointSsrc(endpointId,
                        MediaType.AUDIO));
                    eventHandler.handleEvent(event);
                }
                catch (ParseException e)
                {
                    e.printStackTrace();
                }
                catch (UnsupportedEncodingException e)
                {
                    e.printStackTrace();
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onSctpNotification(SctpSocket socket,
            SctpNotification notification)
        {
            logger.debug("SCTP Notification: " + notification);
            System.out.println("SCTP Notification: " + notification);

            if (notification.sn_type == SctpNotification.SCTP_ASSOC_CHANGE)
            {
                SctpNotification.AssociationChange assocChange =
                    (SctpNotification.AssociationChange) notification;

                switch (assocChange.state)
                {
                case SctpNotification.AssociationChange.SCTP_COMM_UP:
                    logger.debug("SCTP connection established successfully.");
                    System.out
                        .println("SCTP connection established successfully.");
                    break;
                case SctpNotification.AssociationChange.SCTP_COMM_LOST:
                case SctpNotification.AssociationChange.SCTP_SHUTDOWN_COMP:
                case SctpNotification.AssociationChange.SCTP_CANT_STR_ASSOC:
                    break;
                }
            }
        }
        
        /**
         * Handles control packet.
         * @param data raw packet data that arrived on control PPID.
         * @param sid SCTP stream id on which the data has arrived.
         */
        private void onCtrlPacket(byte[] data, int sid)
            throws IOException
        {
            System.out.print("Control Packet");
            for (byte b : data)
            {
                System.out.printf("%03d ", b);
            }
            System.out.println();
            
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int messageType = /* 1 byte unsigned integer */ 0xFF & buffer.get();

            if(messageType == MSG_OPEN_CHANNEL)
            {
                int channelType = /* 1 byte unsigned integer */ 0xFF & buffer.get();
                int priority
                    = /* 2 bytes unsigned integer */ 0xFFFF & buffer.getShort();
                long reliability
                    = /* 4 bytes unsigned integer */ 0xFFFFFFFFL & buffer.getInt();
                int labelLength
                    = /* 2 bytes unsigned integer */ 0xFFFF & buffer.getShort();
                int protocolLength
                    = /* 2 bytes unsigned integer */ 0xFFFF & buffer.getShort();
                String label;
                String protocol;

                if (labelLength == 0)
                {
                    label = "";
                }
                else
                {
                    byte[] labelBytes = new byte[labelLength];

                    buffer.get(labelBytes);
                    label = new String(labelBytes, "UTF-8");
                }
                if (protocolLength == 0)
                {
                    protocol = "";
                }
                else
                {
                    byte[] protocolBytes = new byte[protocolLength];

                    buffer.get(protocolBytes);
                    protocol = new String(protocolBytes, "UTF-8");
                }

                System.out.println("!!! "
                    + " data channel open request on SID: " + sid + " type: "
                    + channelType + " prio: " + priority + " reliab: "
                    + reliability + " label: " + label + " proto: " + protocol);

                sendOpenChannelAck(sid);
            }
        }
        
        /**
         * Sends acknowledgment for open channel request on given SCTP stream ID.
         * @param sid SCTP stream identifier to be used for sending ack.
         */
        private void sendOpenChannelAck(int sid)
            throws IOException
        {
            // Send ACK
            byte[] ack = MSG_CHANNEL_ACK_BYTES;
            int sendAck = sctpSocket.send(ack, true, sid, WEB_RTC_PPID_CTRL);

            if(sendAck != ack.length)
            {
                logger.error("Failed to send open channel confirmation");
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
            /*
             * If Sctp data channel crashed, fire an aborted event to notify
             * upper class.
             */
            fireEvent(new TaskEvent(
                TaskEvent.Type.RECORDER_ABORTED));
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
