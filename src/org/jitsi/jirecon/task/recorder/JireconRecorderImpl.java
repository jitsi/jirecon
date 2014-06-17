/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.recorder;

import java.io.IOException;
import java.util.*;
import java.util.Map.*;

import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.impl.neomedia.recording.*;
import org.jitsi.jirecon.dtlscontrol.JireconSrtpControlManager;
import org.jitsi.jirecon.task.JireconTaskSharingInfo;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.neomedia.recording.*;
import org.jitsi.util.Logger;

public class JireconRecorderImpl
    implements JireconRecorder
{
    private Map<MediaType, MediaStream> streams =
        new HashMap<MediaType, MediaStream>();

    private MediaService mediaService;

    private Map<MediaType, RTPTranslator> rtpTranslators =
        new HashMap<MediaType, RTPTranslator>();

    private Map<MediaType, Recorder> recorders =
        new HashMap<MediaType, Recorder>();

    private JireconTaskSharingInfo sharingInfo;

    private JireconRecorderState state = JireconRecorderState.INIT;

    private static final Logger logger = Logger
        .getLogger(JireconRecorderImpl.class);

    private final String SAVING_DIR;

    public JireconRecorderImpl(String SAVING_DIR,
        JireconTaskSharingInfo sharingInfo, Map<MediaType, SrtpControl> srtpControls)
    {
        // Have to make sure that Libjitsi has been started.
        this.mediaService = LibJitsi.getMediaService();
        this.SAVING_DIR = SAVING_DIR;
        this.sharingInfo = sharingInfo;
        createMediaStreams(srtpControls);
    }

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

    @Override
    public void stopRecording()
    {
        try
        {
            stopRecordingStreams();
        }
        catch (OperationFailedException e)
        {
            e.printStackTrace();
        }

        try
        {
            stopReceivingStreams();
        }
        catch (OperationFailedException e)
        {
            e.printStackTrace();
        }
    }

    private void prepareMediaStreams(
        Map<MediaFormat, Byte> formatAndDynamicPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets)
        throws OperationFailedException
    {
        logger.info("prepareMediaStreams");
        if (!readyTo(JireconRecorderEvent.PREPARE_STREAM))
        {
            throw new OperationFailedException(
                "Could not prepare streams, other reason.",
                OperationFailedException.GENERAL_ERROR);
        }

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

            // FIXME: How to deal with DTLS control?
            stream.setRTPTranslator(getTranslator(mediaType));
        }

        updateState(JireconRecorderEvent.PREPARE_STREAM);
    }

    private void prepareRecorders() throws OperationFailedException
    {
        logger.info("prepareRecorders");
        if (!readyTo(JireconRecorderEvent.PREPARE_RECORDER))
        {
            throw new OperationFailedException(
                "Could not prepare recorders, streams are not ready.",
                OperationFailedException.GENERAL_ERROR);
        }

        for (Entry<MediaType, RTPTranslator> e : rtpTranslators.entrySet())
        {
            Recorder recorder = new RecorderRtpImpl(e.getValue());
            recorders.put(e.getKey(), recorder);
        }

        updateState(JireconRecorderEvent.PREPARE_RECORDER);
    }

    private void startReceivingStreams() throws OperationFailedException
    {
        logger.info("startReceiving");
        if (!readyTo(JireconRecorderEvent.START_RECEIVING_STREAM))
        {
            throw new OperationFailedException(
                "Could not start receiving streams, streams are not ready.",
                OperationFailedException.GENERAL_ERROR);
        }

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

        if (streams.size() != startCount)
        {
            throw new OperationFailedException(
                "Could not start receiving streams",
                OperationFailedException.GENERAL_ERROR);
        }

        updateState(JireconRecorderEvent.START_RECEIVING_STREAM);
    }

    private void startRecordingStreams()
        throws IOException,
        MediaException,
        OperationFailedException
    {
        logger.info("startRecording");
        if (!readyTo(JireconRecorderEvent.START_RECORDING_STREAM))
        {
            throw new OperationFailedException(
                "Could not start recording streams, recorders are not ready.",
                OperationFailedException.GENERAL_ERROR);
        }

        RecorderEventHandler eventHandler =
            new JireconRecorderEventHandler(SAVING_DIR + "/meta");
        for (Entry<MediaType, Recorder> e : recorders.entrySet())
        {
            e.getValue().setEventHandler(eventHandler);
            e.getValue().start(e.getKey().toString(), SAVING_DIR);
        }

        updateState(JireconRecorderEvent.START_RECORDING_STREAM);
    }

    private void stopRecordingStreams() throws OperationFailedException
    {
        logger.info("stopRecording");
        if (!readyTo(JireconRecorderEvent.STOP_RECORDING_STREAM))
        {
            throw new OperationFailedException(
                "Could not stop recording streams, streams are not been recording.",
                OperationFailedException.GENERAL_ERROR);
        }

        for (Entry<MediaType, Recorder> e : recorders.entrySet())
        {
            e.getValue().stop();
        }

        updateState(JireconRecorderEvent.STOP_RECORDING_STREAM);
    }

    private void stopReceivingStreams() throws OperationFailedException
    {
        logger.info("stopRecording");
        if (!readyTo(JireconRecorderEvent.STOP_RECEIVING_STREAM))
        {
            throw new OperationFailedException(
                "Could not stop receiving streams, streams are not been receiving.",
                OperationFailedException.GENERAL_ERROR);
        }

        for (Map.Entry<MediaType, MediaStream> e : streams.entrySet())
        {
            e.getValue().stop();
            e.getValue().close();
        }

        updateState(JireconRecorderEvent.STOP_RECEIVING_STREAM);
    }

    private void createMediaStreams(Map<MediaType, SrtpControl> srtpControls)
    {
        logger.info("prepareMediaStreams");
        for (MediaType mediaType : MediaType.values())
        {
            if (mediaType != MediaType.AUDIO && mediaType != MediaType.VIDEO)
            {
                continue;
            }
            final MediaStream stream =
                mediaService.createMediaStream(null, mediaType, srtpControls.get(mediaType));
            streams.put(mediaType, stream);

            stream.setName(mediaType.toString());
            stream.setDirection(MediaDirection.RECVONLY);
            sharingInfo.addLocalSsrc(mediaType,
                stream.getLocalSourceID() & 0xFFFFFFFFL);
        }
    }

    private RTPTranslator getTranslator(MediaType mediaType)
    {
        if (rtpTranslators.containsKey(mediaType))
        {
            return rtpTranslators.get(mediaType);
        }

        final RTPTranslator translator = mediaService.createRTPTranslator();
        rtpTranslators.put(mediaType, translator);

        return translator;
    }

    private long getAssociatedSsrc(long ssrc, MediaType mediaType)
    {
        Map<String, Map<MediaType, String>> participants =
            sharingInfo.getParticipantsSsrcs();

        if (null == participants)
            return -1;

        for (Entry<String, Map<MediaType, String>> e : participants.entrySet())
        {
            System.out.println(e.getKey() + " audio "
                + e.getValue().get(MediaType.AUDIO));
            System.out.println(e.getKey() + " video "
                + e.getValue().get(MediaType.VIDEO));
        }

        for (Entry<String, Map<MediaType, String>> e : participants.entrySet())
        {
            System.out.println(ssrc + " "
                + Long.valueOf(e.getValue().get(mediaType)));
            if ((ssrc - Long.valueOf(e.getValue().get(mediaType))) == 0)
            {
                if (mediaType.equals(MediaType.AUDIO))
                    return Long.valueOf(e.getValue().get(MediaType.VIDEO));
                else if (mediaType.equals(MediaType.VIDEO))
                    return Long.valueOf(e.getValue().get(MediaType.AUDIO));
            }
        }

        return -1;
    }

    private class JireconRecorderEventHandler
        implements RecorderEventHandler
    {
        private RecorderEventHandler handler;

        public JireconRecorderEventHandler(String filename)
            throws IOException
        {
            handler = new RecorderEventHandlerJSONImpl(filename);
        }

        @Override
        public void close()
        {
            System.out.println("close");
        }

        @Override
        public synchronized boolean handleEvent(RecorderEvent event)
        {
            System.out.println(event + " ssrc:" + event.getSsrc());
            RecorderEvent.Type type = event.getType();

            if (RecorderEvent.Type.SPEAKER_CHANGED.equals(type))
            {
                System.out.println("SPEAKER_CHANGED audio ssrc: "
                    + event.getAudioSsrc());
                long audioSsrc = event.getAudioSsrc();
                long videoSsrc = getAssociatedSsrc(audioSsrc, MediaType.AUDIO);
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

    private void updateState(JireconRecorderEvent evt)
    {
        switch (evt)
        {
        case PREPARE_STREAM:
            state = JireconRecorderState.STREAM_READY;
            break;
        case PREPARE_RECORDER:
            state = JireconRecorderState.RECORDER_READY;
            break;
        case START_RECEIVING_STREAM:
            state = JireconRecorderState.RECEIVING_STREAM;
            break;
        case START_RECORDING_STREAM:
            state = JireconRecorderState.RECORDING_STREAM;
            break;
        case STOP_RECEIVING_STREAM:
            state = JireconRecorderState.STREAM_READY;
            break;
        case STOP_RECORDING_STREAM:
            state = JireconRecorderState.RECORDER_READY;
            break;
        }
    }

    private boolean readyTo(JireconRecorderEvent evt)
        throws OperationFailedException
    {
        switch (evt)
        {
        case PREPARE_STREAM:
            if (JireconRecorderState.INIT != state)
                return false;
            break;
        case START_RECEIVING_STREAM:
            if (JireconRecorderState.STREAM_READY != state)
                return false;
            break;
        case PREPARE_RECORDER:
            if (JireconRecorderState.RECEIVING_STREAM != state)
                return false;
            break;
        case START_RECORDING_STREAM:
            if (JireconRecorderState.RECORDER_READY != state)
                return false;
            break;
        case STOP_RECEIVING_STREAM:
            if (JireconRecorderState.RECEIVING_STREAM != state)
                return false;
            break;
        case STOP_RECORDING_STREAM:
            if (JireconRecorderState.RECORDING_STREAM != state)
                return false;
            break;
        }
        return true;
    }

    private enum JireconRecorderEvent
    {
        PREPARE_STREAM,
        PREPARE_RECORDER,
        START_RECEIVING_STREAM,
        STOP_RECEIVING_STREAM,
        START_RECORDING_STREAM,
        STOP_RECORDING_STREAM
    }

    private enum JireconRecorderState
    {
        INIT, STREAM_READY, RECEIVING_STREAM, RECORDER_READY, RECORDING_STREAM,
    }

}
