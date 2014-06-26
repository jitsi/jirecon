/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.recorder;

import java.io.*;
import java.util.*;
import java.util.Map.*;

import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.impl.neomedia.recording.*;
import org.jitsi.jirecon.task.*;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.neomedia.recording.*;
import org.jitsi.util.Logger;

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
     * The <tt>JireconTaskEventListener</tt>, if <tt>JireconRecorder</tt> has
     * something important, it will notify them.
     */
    private List<JireconTaskEventListener> listeners =
        new ArrayList<JireconTaskEventListener>();

    /**
     * Map between participant's jid and their associated ssrcs.
     * <p>
     * Every participant usually has two ssrc(one for audio and one for video),
     * these two ssrc are associated.
     */
    private Map<String, List<String>> associatedSsrcs =
        new HashMap<String, List<String>>();

    /**
     * Whether the <tt>JireconRecorderImpl</tt> is receiving streams.
     */
    private boolean isReceiving = false;

    /**
     * Whether the <tt>JireconRecorderImpl</tt> is recording streams.
     */
    private boolean isRecording = false;

    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger
        .getLogger(JireconRecorderImpl.class);

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
        createMediaStreams(srtpControls);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startRecording(Map<MediaType, Map<MediaFormat, Byte>> formatAndDynamicPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets)
        throws OperationFailedException
    {
        prepareMediaStreams(formatAndDynamicPTs, connectors, targets);
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
        stopTranslators();
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
            Recorder recorder = new RecorderRtpImpl(e.getValue());
            recorders.put(e.getKey(), recorder);
        }
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
    private void startRecordingStreams() 
        throws OperationFailedException
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

        RecorderEventHandler eventHandler =
            new JireconRecorderEventHandler(outputDir + "/metadata.json");
        for (Entry<MediaType, Recorder> entry : recorders.entrySet())
        {
            entry.getValue().setEventHandler(eventHandler);
            try
            {
                // Start recording
                entry.getValue().start(entry.getKey().toString(), outputDir);
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

    /**
     * Stop recording media streams.
     */
    private void stopRecordingStreams()
    {
        logger.info("stopRecording");
        if (!isRecording)
            return;

        for (Entry<MediaType, Recorder> e : recorders.entrySet())
        {
            e.getValue().stop();
            System.out.println("Stop " + e.getKey() + " Over");
        }
        recorders.clear();
        isRecording = false;
    }

    /**
     * Stop receiving media streams.
     */
    private void stopReceivingStreams()
    {
        logger.info("stopReceiving");
        if (!isReceiving)
            return;

        for (Map.Entry<MediaType, MediaStream> e : streams.entrySet())
        {
            e.getValue().close();
            e.getValue().stop();
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
        logger.info("prepareMediaStreams");
        for (MediaType mediaType : MediaType.values())
        {
            // Make sure we are focusing on right media type, because MediaType
            // has other types.
            if (mediaType != MediaType.AUDIO && mediaType != MediaType.VIDEO)
                continue;

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
            rtpTranslators.put(mediaType, translator);
        }
        return translator;
    }

    /**
     * Find and get the <tt>MediaType</tt> ssrc which belongs to the same
     * endpoint with an existed ssrc. An endpoint means a media stream source,
     * each media stream source generally contains two ssrc, one for audio
     * stream and one for video stream.
     * 
     * @param ssrc indicates an endpoint.
     * @param mediaType is the <tt>MediaType</tt> which indicates which ssrc you
     *            want to get.
     * @return ssrc or -1 if not found
     */
    private long getAssociatedSsrc(long ssrc, MediaType mediaType)
    {
        for (Entry<String, List<String>> e : associatedSsrcs.entrySet())
        {
            // Associated ssrc should be 2(one for audio and one for video), but
            // we need to check it again in
            // case
            // of something weird happened.
            if (e.getValue().size() < 2)
                continue;

            String first = e.getValue().get(0);
            String second = e.getValue().get(1);
            if (ssrc == Long.valueOf(first))
                return Long.valueOf(second);
            if (ssrc == Long.valueOf(second))
                return Long.valueOf(first);
        }

        return -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAssociatedSsrcs(Map<String, List<String>> ssrcs)
    {
        synchronized (associatedSsrcs)
        {
            associatedSsrcs = ssrcs;
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
        Map<MediaType, Long> localSsrcs = new HashMap<MediaType, Long>();
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
    private class JireconRecorderEventHandler
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
        public JireconRecorderEventHandler(String filename)
            throws OperationFailedException
        {
            // If there is an existed file with "filename", add suffix to
            // "filename". For instance, from "metadata.json" to
            // "metadata.json-1".
            int count = 1;
            String filenameAvailable = filename;
            while (true)
            {
                File file = new File(filenameAvailable);
                if (file.exists())
                    filenameAvailable = filename + "-" + count++;
                else
                    break;
            }
            try
            {
                handler = new RecorderEventHandlerJSONImpl(filenameAvailable);
            }
            catch (IOException e)
            {
                throw new OperationFailedException(
                    "Could not create event handler, " + e.getMessage(),
                    OperationFailedException.GENERAL_ERROR);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close()
        {
            System.out.println("close");
        }

        /**
         * Handle event.
         */
        @Override
        public synchronized boolean handleEvent(RecorderEvent event)
        {
            System.out.println(event + " ssrc:" + event.getSsrc());
            RecorderEvent.Type type = event.getType();

            // TODO: It seems that there is no STARTED event.
            if (RecorderEvent.Type.SPEAKER_CHANGED.equals(type))
            {
                System.out.println("SPEAKER_CHANGED audio ssrc: "
                    + event.getAudioSsrc());
                long audioSsrc = event.getAudioSsrc();
                long videoSsrc = getAssociatedSsrc(audioSsrc, MediaType.VIDEO);
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
}
