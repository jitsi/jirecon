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
import org.jitsi.jirecon.recorder.JireconRecorderInfo.JireconRecorderEvent;
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

    @Override
    public JireconRecorderInfo getRecorderInfo()
    {
        return recorderInfo;
    }

    private void prepareMediaStreams(
        Map<MediaFormat, Byte> formatAndDynamicPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets)
        throws OperationFailedException
    {
        logger.info("completeMediaStreams");
        if (!recorderInfo.readyTo(JireconRecorderEvent.PREPARE_STREAM))
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
        if (!recorderInfo.readyTo(JireconRecorderEvent.PREPARE_RECORDER))
        {
            throw new OperationFailedException(
                "Could not prepare recorders, streams are not ready.",
                OperationFailedException.GENERAL_ERROR);
        }

        for (Entry<MediaType, RTPTranslator> e : rtpTranslators.entrySet())
        {
            Recorder recorder = new VideoRecorderImpl(e.getValue());
            recorders.put(e.getKey(), recorder);
        }
        
        updateState(JireconRecorderEvent.PREPARE_RECORDER);
    }

    private void startReceivingStreams() throws OperationFailedException
    {
        logger.info("startReceiving");
        if (!recorderInfo.readyTo(JireconRecorderEvent.START_RECEIVING_STREAM))
        {
            throw new OperationFailedException(
                "Could not start receiving streams, streams are not ready.",
                OperationFailedException.GENERAL_ERROR);
        }

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

        updateState(JireconRecorderEvent.START_RECEIVING_STREAM);
    }

    private void startRecordingStreams()
        throws IOException,
        MediaException,
        OperationFailedException
    {
        logger.info("startRecording");
        if (!recorderInfo.readyTo(JireconRecorderEvent.START_RECORDING_STREAM))
        {
            throw new OperationFailedException(
                "Could not start recording streams, recorders are not ready.",
                OperationFailedException.GENERAL_ERROR);
        }

//        prepareRecorders();
        for (Entry<MediaType, Recorder> e : recorders.entrySet())
        {
            e.getValue().start("useless", ".");
            e.getValue().setEventHandler(
                new RecorderEventHandlerJSONImpl("./" + e.getKey() + "_meta"));
        }
        
        updateState(JireconRecorderEvent.START_RECORDING_STREAM);
    }

    private void stopRecordingStreams() throws OperationFailedException
    {
        logger.info("stopRecording");
        if (!recorderInfo.readyTo(JireconRecorderEvent.STOP_RECORDING_STREAM))
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
        if (!recorderInfo.readyTo(JireconRecorderEvent.STOP_RECEIVING_STREAM))
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

    private void updateState(JireconRecorderEvent evt)
    {
        recorderInfo.updateState(evt);
    }

}
