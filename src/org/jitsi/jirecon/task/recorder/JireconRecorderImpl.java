/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.recorder;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.*;

import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.impl.neomedia.recording.*;
import org.jitsi.jirecon.task.JireconTaskSharingInfo;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.neomedia.recording.*;
import org.jitsi.util.Logger;

/**
 * An implementation of <tt>JireconRecorder</tt>.
 * <p>
 * <tt>JireconRecorderImpl</tt> will record the media streams that you specified,
 * besides, it also records a meta data file which is used for post-proceeding.
 * 
 * @author lishunyang
 * 
 */
public class JireconRecorderImpl
    implements JireconRecorder
{
    /**
     * The mapping between <tt>MediaType</tt> and <tt>MediaStream</tt>. Those
     * are used to receiving media streams.
     */
    private Map<MediaType, MediaStream> streams =
        new HashMap<MediaType, MediaStream>();

    /**
     * The instance of <tt>MediaService</tt>.
     */
    private MediaService mediaService;

    /**
     * The mapping between <tt>MediaType</tt> and <tt>RTPTranslator</tt>. Those
     * are used to initialize recorder.
     */
    private Map<MediaType, RTPTranslator> rtpTranslators =
        new HashMap<MediaType, RTPTranslator>();

    /**
     * The mapping between <tt>MediaType</tt> and <tt>Recorder</tt>. Those are
     * used to record media streams into local files.
     */
    private Map<MediaType, Recorder> recorders =
        new HashMap<MediaType, Recorder>();

    /**
     * The <tt>JireconTaskSharingInfo</tt> is used to share some necessary
     * information between <tt>JireconRecorderImpl</tt> and other classes.
     */
    private JireconTaskSharingInfo sharingInfo;

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
    private final String SAVING_DIR;

    /**
     * Construct method of <tt>JireconRecorderImpl</tt>.
     * <p>
     * <strong>Warning:</strong> LibJitsi must be started before calling this
     * construnction method.
     * 
     * @param SAVING_DIR decide where to output the files. The directory must be
     *            existed and writable.
     * @param sharingInfo includes some necessary information, it is shared with
     *            other classes.
     * @param srtpControls is the mapping between <tt>MediaType</tt> and <tt>SrtpControl</tt>
     *            which is used for SRTP transfer.
     */
    public JireconRecorderImpl(String SAVING_DIR,
        JireconTaskSharingInfo sharingInfo,
        Map<MediaType, SrtpControl> srtpControls)
    {
        this.mediaService = LibJitsi.getMediaService();
        this.SAVING_DIR = SAVING_DIR;
        this.sharingInfo = sharingInfo;
        createMediaStreams(srtpControls);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startRecording(Map<MediaFormat, Byte> formatAndDynamicPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets)
        throws OperationFailedException,
        IOException,
        MediaException
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
     * Make all <tt>JireconRecorderImpl</tt> ready to start receiving media streams.
     * 
     * @param formatAndDynamicPTs is the mapping between <tt>MediaFormat</tt> and
     *            dynamic payload type id. <tt>MediaStream</tt> needs those to
     *            distinguish different <tt>MediaFormat</tt>.
     * @param connectors is the mapping between <tt>MediaType</tt> and
     *            <tt>StreamConnector</tt>. Those connectors are used to transfer
     *            stream data.
     * @param targets is the mapping between <tt>MediaType</tt> and
     *            <tt>MediaStreamTarget</tt>. The target indicate media stream source.
     * @throws OperationFailedException if some operation failed and the
     *             preparation is aborted.
     */
    private void prepareMediaStreams(
        Map<MediaFormat, Byte> formatAndDynamicPTs,
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
            for (Entry<MediaFormat, Byte> f : formatAndDynamicPTs.entrySet())
            {
                if (mediaType == f.getKey().getMediaType())
                {
                    stream.addDynamicRTPPayloadType(f.getValue(), f.getKey());
                    if (null == stream.getFormat())
                    {
                        stream.setFormat(f.getKey());
                    }
                }
            }

            stream.setRTPTranslator(getTranslator(mediaType));
        }
    }

    /**
     * Make the <tt>JireconRecorderImpl</tt> ready to start recording media streams.
     * 
     * @throws OperationFailedException if some operation failed and the
     *             preparation is aborted.
     */
    private void prepareRecorders() throws OperationFailedException
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
    private void startReceivingStreams() throws OperationFailedException
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
     * @throws IOException
     * @throws MediaException
     * @throws OperationFailedException if some operation failed and the
     *             recording is aborted.
     */
    // TODO Unify the exceptions, merge IOException and MediaException into
    // OperationFailedException so that it looks better.
    private void startRecordingStreams()
        throws IOException,
        MediaException,
        OperationFailedException
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
            new JireconRecorderEventHandler(SAVING_DIR + "/metadata.json");
        for (Entry<MediaType, Recorder> e : recorders.entrySet())
        {
            e.getValue().setEventHandler(eventHandler);
            e.getValue().start(e.getKey().toString(), SAVING_DIR);
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
     * <strong>Warning:</strong> We can only add <tt>SrtpControl</tt> to <tt>MediaStream</tt>
     * at this moment.
     * 
     * @param srtpControls is the mapping between <tt>MediaType</tt> and <tt>SrtpControl</tt>.
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

            // Once the media streams are created, we put the ssrc into shared
            // info, so that other classes could use them.
            sharingInfo.addLocalSsrc(mediaType,
                stream.getLocalSourceID() & 0xFFFFFFFFL);
        }
    }

    /**
     * Get a <tt>RTPTranslator</tt> for a specified <tt>MediaType</tt>. Create a new one if it
     * doesn't exist.
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
     * Find and get the <tt>MediaType</tt> ssrc which belongs to the same endpoint with
     * an existed ssrc. An endpoint means a media stream source, each media
     * stream source generally contains two ssrc, one for audio stream and one
     * for video stream.
     * 
     * @param ssrc indicates an endpoint.
     * @param mediaType is the <tt>MediaType</tt> which indicates which ssrc you want
     *            to get.
     * @return ssrc or -1 if not found
     */
    private long getAssociatedSsrc(long ssrc, MediaType mediaType)
    {
        Map<String, Map<MediaType, String>> participants =
            sharingInfo.getParticipantsSsrcs();

        if (null == participants)
            return -1;

        for (Entry<String, Map<MediaType, String>> e : participants.entrySet())
        {
            logger.info(e.getKey() + " audio "
                + e.getValue().get(MediaType.AUDIO));
            logger.info(e.getKey() + " video "
                + e.getValue().get(MediaType.VIDEO));
            for (String s : e.getValue().values())
            {
                if (ssrc == Long.valueOf(s))
                {
                    return Long.valueOf(e.getValue().get(mediaType));
                }
            }
        }

        return -1;
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
         * The true <tt>RecorderEventHandler</tt> which is used for handling event
         * actually.
         */
        private RecorderEventHandler handler;

        /**
         * The construction method for creating <tt>JireconRecorderEventHandler</tt>.
         * 
         * @param filename the meta data file's name.
         */
        public JireconRecorderEventHandler(String filename)
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
            // TODO: If we failed to create RecorderEventHandlerJSONImpl, maybe
            // it better to thrown the exception instead of catching it.
            try
            {
                handler = new RecorderEventHandlerJSONImpl(filenameAvailable);
            }
            catch (IOException e)
            {
                e.printStackTrace();
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
         * Handle event, only focusing on SPEAKER_CHANGED event.
         */
        @Override
        public synchronized boolean handleEvent(RecorderEvent event)
        {
            System.out.println(event + " ssrc:" + event.getSsrc());
            RecorderEvent.Type type = event.getType();

            // TODO: I think here we should handle the STARTED event and ENDED
            // event too.
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
