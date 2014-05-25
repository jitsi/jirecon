/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

import java.util.Map;
import java.util.Map.Entry;

import org.ice4j.ice.CandidatePair;
import org.jitsi.jirecon.session.JireconSessionInfo;
import org.jitsi.service.neomedia.DefaultStreamConnector;
import org.jitsi.service.neomedia.MediaDirection;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaStream;
import org.jitsi.service.neomedia.MediaStreamStats;
import org.jitsi.service.neomedia.MediaStreamTarget;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.RTPTranslator;
import org.jitsi.service.neomedia.SrtpControlType;
import org.jitsi.service.neomedia.StreamConnector;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jitsi.util.Logger;

public class JireconRecorderImpl
    implements JireconRecorder
{
    private Map<MediaType, MediaStream> streams;

    private MediaService mediaService;

    private RecorderInfo info;

    // TODO:
    // private Map<MediaType, Recorder> recorders;

    private Logger logger;

    /**
     * The conference recorder need an active media service.
     * 
     * @param mediaService
     */
    public JireconRecorderImpl(MediaService mediaService)
    {
        this.mediaService = mediaService;
        info = new RecorderInfo();
        logger = Logger.getLogger(this.getClass());
    }

    // This method may throw exceptions in the future.
    @Override
    public void start(JireconSessionInfo info)
    {
        logger.info("JireconRecorder start");
        updateStatus(JireconRecorderStatus.INITIATING);
        startReceivingStreams(info);
        prepareRecorders();
        startRecording();
    }

    @Override
    public void stop()
    {
        logger.info("JireconRecorder end");
        stopRecording();
        releaseRecorders();
        releaseMediaStreams();
    }

    private void updateStatus(JireconRecorderStatus status)
    {
        info.setStatus(status);
    }

    private void startRecording()
    {
        logger.info("JireconRecorder start recording");
    }

    private void prepareRecorders()
    {
    }

    private void stopRecording()
    {
    }

    private void releaseRecorders()
    {
    }

    private void releaseMediaStreams()
    {
        for (Map.Entry<MediaType, MediaStream> e : streams.entrySet())
        {
            e.getValue().stop();
            e.getValue().close();
        }
    }

    private boolean startReceivingStreams(JireconSessionInfo info)
    {
        logger.info("Jirecon start receiving streams");
        int startCount = 0;
        for (MediaType media : MediaType.values())
        {
            MediaStream stream = createMediaStream(info, media);
            streams.put(media, stream);
            stream.start();
            if (stream.isStarted())
            {
                startCount += 1;
            }
        }

        if (streams.size() == startCount)
        {
            updateStatus(JireconRecorderStatus.RECVEIVING);
        }
        else
        {
            updateStatus(JireconRecorderStatus.RECVEIVING_ERROR);
        }

        return true;
    }

    private MediaStream createMediaStream(JireconSessionInfo info,
        MediaType media)
    {
        MediaStream stream =
            mediaService.createMediaStream(createConnector(info, media), media,
                mediaService.createSrtpControl(SrtpControlType.DTLS_SRTP));

        // TODO: Translator thins is not clear
        stream.setRTPTranslator(createTranslator());

        stream.setName(media.toString());
        stream.setDirection(MediaDirection.RECVONLY);
        for (Entry<MediaFormat, Byte> e : info.getPayloadTypes(media)
            .entrySet())
        {
            stream.addDynamicRTPPayloadType(e.getValue(), e.getKey());
            if (null == stream.getFormat())
            {
                stream.setFormat(e.getKey());
            }
        }
        stream.setTarget(createStreamTarget(info, media));

        return stream;
    }

    private StreamConnector createConnector(JireconSessionInfo info,
        MediaType media)
    {
        final CandidatePair rtpPair = info.getRtpCandidatePair(media);
        final CandidatePair rtcpPair = info.getRtcpCandidatePair(media);
        return new DefaultStreamConnector(rtpPair.getLocalCandidate()
            .getDatagramSocket(), rtcpPair.getLocalCandidate()
            .getDatagramSocket());
    }

    private MediaStreamTarget createStreamTarget(JireconSessionInfo info,
        MediaType media)
    {
        final CandidatePair rtpPair = info.getRtpCandidatePair(media);
        final CandidatePair rtcpPair = info.getRtcpCandidatePair(media);
        return new MediaStreamTarget(rtpPair.getRemoteCandidate()
            .getTransportAddress(), rtcpPair.getRemoteCandidate()
            .getTransportAddress());
    }

    private RTPTranslator createTranslator()
    {
        return mediaService.createRTPTranslator();
    }
}
