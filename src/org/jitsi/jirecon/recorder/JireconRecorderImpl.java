/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

import java.io.IOException;
import java.util.*;
import java.util.Map.*;

import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.impl.neomedia.recording.*;
import org.jitsi.jirecon.recorder.JireconRecorderInfo.JireconRecorderState;
import org.jitsi.jirecon.utils.JireconConfiguration;
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

    private JireconRecorderInfo recorderInfo = new JireconRecorderInfo();

    private static final Logger logger = Logger
        .getLogger(JireconRecorderImpl.class);

    public JireconRecorderImpl(JireconConfiguration configuration,
        MediaService mediaService)
    {
        this.mediaService = mediaService;
        createMediaStreams();
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
        prepareRecorders();
        startReceivingStreams();
        startRecordingStreams();
    }

    @Override
    public void stopRecording()
    {
        stopRecordingStreams();
        stopReceivingStreams();
    }

    @Override
    public JireconRecorderInfo getRecorderInfo()
    {
        return recorderInfo;
    }

    private void prepareMediaStreams(
        Map<MediaFormat, Byte> formatAndDynamicPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets)
    {
        logger.info("completeMediaStreams");
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
        updateState(JireconRecorderState.STREAM_READY);
    }
    
    private void prepareRecorders()
    {
        logger.info("prepareRecorders");
        for (Entry<MediaType, RTPTranslator> e : rtpTranslators.entrySet())
        {
            Recorder recorder = new VideoRecorderImpl(e.getValue());
            recorders.put(e.getKey(), recorder);
        }
        updateState(JireconRecorderState.RECORDER_READY);
    }

    private void startReceivingStreams() throws OperationFailedException
    {
        logger.info("startReceiving");
        int startCount = 0;
        for (Entry<MediaType, MediaStream> e : streams.entrySet())
        {
            MediaStream stream = e.getValue();
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

        updateState(JireconRecorderState.RECEIVING_STREAM);
    }
    
    private void startRecordingStreams() throws IOException, MediaException
    {
        logger.info("startRecording");
        prepareRecorders();
        for (Entry<MediaType, Recorder> e : recorders.entrySet())
        {
            e.getValue().start("useless", ".");
            e.getValue().setEventHandler(
                new RecorderEventHandlerJSONImpl("./" + e.getKey() + "_meta"));
        }
        updateState(JireconRecorderState.RECORDING_STREAM);
    }

    private void stopRecordingStreams()
    {
        logger.info("stopRecording");
        for (Entry<MediaType, Recorder> e : recorders.entrySet())
        {
            e.getValue().stop();
        }
        updateState(JireconRecorderState.STOP_RECORDING_STREAM);
    }
    
    private void stopReceivingStreams()
    {
        logger.info("stopRecording");
        for (Map.Entry<MediaType, MediaStream> e : streams.entrySet())
        {
            e.getValue().stop();
            e.getValue().close();
        }

        updateState(JireconRecorderState.STOP_RECEIVING_STREAM);
    }

    private void createMediaStreams()
    {
        logger.info("prepareMediaStreams");
        for (MediaType mediaType : MediaType.values())
        {
            if (mediaType != MediaType.AUDIO && mediaType != MediaType.VIDEO)
            {
                continue;
            }
            final MediaStream stream =
                mediaService.createMediaStream(mediaType);
            streams.put(mediaType, stream);

            stream.setName(mediaType.toString());
            stream.setDirection(MediaDirection.RECVONLY);
            recorderInfo.addLocalSsrc(mediaType,
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

    private void updateState(JireconRecorderState state)
    {
        recorderInfo.setState(state);
    }

}
