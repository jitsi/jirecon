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
import org.jitsi.jirecon.dtlscontrol.JireconSrtpControlManager;
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

    private JireconRecorderInfo info;

    private static final Logger logger = Logger.getLogger(JireconRecorderImpl.class);

    public JireconRecorderImpl()
    {
        info = new JireconRecorderInfo();
    }

    @Override
    public void init(JireconConfiguration configuration,
        MediaService mediaService, JireconSrtpControlManager srtpControlManager)
    {
        logger.info("init");
        this.mediaService = mediaService;
        prepareMediaStreams();
    }

    @Override
    public void uninit()
    {
        logger.info("uinit");
        mediaService = null;
        streams.clear();
        recorders.clear();
        rtpTranslators.clear();
        info = new JireconRecorderInfo();
    }

    @Override
    public void prepareMediaStreams()
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
            info.addLocalSsrc(mediaType,
                stream.getLocalSourceID() & 0xFFFFFFFFL);
        }
    }

    @Override
    public void completeMediaStreams(
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
    }

    @Override
    public void startReceiving() throws OperationFailedException
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
    }

    @Override
    public void stopReceiving()
    {
        logger.info("stopRecording");
        for (Map.Entry<MediaType, MediaStream> e : streams.entrySet())
        {
            e.getValue().stop();
            e.getValue().close();
        }
    }

    @Override
    public void prepareRecorders()
    {
        logger.info("prepareRecorders");
        for (Entry<MediaType, RTPTranslator> e : rtpTranslators.entrySet())
        {
            Recorder recorder = new VideoRecorderImpl(e.getValue());
            recorders.put(e.getKey(), recorder);
        }
    }

    @Override
    public void startRecording() throws IOException, MediaException
    {
        logger.info("startRecording");
        prepareRecorders();
        for (Entry<MediaType, Recorder> e : recorders.entrySet())
        {
            e.getValue().start("useless", ".");
            e.getValue().setEventHandler(
                new RecorderEventHandlerJSONImpl("./" + e.getKey() + "_meta"));
        }
    }

    @Override
    public void stopRecording()
    {
        logger.info("stopRecording");
        for (Entry<MediaType, Recorder> e : recorders.entrySet())
        {
            e.getValue().stop();
        }
    }

    @Override
    public JireconRecorderInfo getRecorderInfo()
    {
        return info;
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
}
