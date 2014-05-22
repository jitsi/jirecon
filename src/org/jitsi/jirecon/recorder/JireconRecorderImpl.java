package org.jitsi.jirecon.recorder;

import java.util.Map;

import org.ice4j.ice.CandidatePair;
import org.jitsi.jirecon.session.SessionInfo;
import org.jitsi.service.neomedia.DefaultStreamConnector;
import org.jitsi.service.neomedia.MediaDirection;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaStream;
import org.jitsi.service.neomedia.MediaStreamTarget;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.RTPTranslator;
import org.jitsi.service.neomedia.SrtpControlType;
import org.jitsi.service.neomedia.StreamConnector;

public class JireconRecorderImpl implements JireconRecorder
{
    private Map<MediaType, MediaStream> streams;

    private MediaService mediaService;

    // TODO:
    // private Map<MediaType, Recorder> recorders;

    /**
     * The conference recorder need an active media service.
     * 
     * @param mediaService
     */
    public JireconRecorderImpl(MediaService mediaService)
    {
        this.mediaService = mediaService;
    }

    // This method may throw exceptions in the future.
    @Override
    public void start(SessionInfo info)
    {
        startReceivingStreams(info);
        prepareRecorders();
        startRecording();
    }

    @Override
    public void stop()
    {
        stopRecording();
        releaseRecorders();
        releaseMediaStreams();
    }

    private void startRecording()
    {
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

    private boolean startReceivingStreams(SessionInfo info)
    {
        for (MediaType media : MediaType.values())
        {
            MediaStream stream = createMediaStream(info, media);
            streams.put(media, stream);
            stream.start();
        }

        return true;
    }

    private MediaStream createMediaStream(SessionInfo info,
        MediaType media)
    {
        MediaStream stream =
            mediaService.createMediaStream(createConnector(info, media), media,
                mediaService.createSrtpControl(SrtpControlType.DTLS_SRTP));

        // TODO: Translator thins is not clear
        stream.setRTPTranslator(createTranslator());

        stream.setName(media.toString());
        stream.setDirection(MediaDirection.RECVONLY);
        stream.addDynamicRTPPayloadType(info.getDynamicPayloadTypeId(media),
            info.getFormat(media));
        stream.setFormat(info.getFormat(media));
        stream.setTarget(createStreamTarget(info, media));

        return stream;
    }

    private StreamConnector createConnector(SessionInfo info,
        MediaType media)
    {
        final CandidatePair rtpPair = info.getRtpCandidatePair(media);
        final CandidatePair rtcpPair = info.getRtcpCandidatePair(media);
        return new DefaultStreamConnector(rtpPair.getLocalCandidate()
            .getDatagramSocket(), rtcpPair.getLocalCandidate()
            .getDatagramSocket());
    }

    private MediaStreamTarget createStreamTarget(SessionInfo info,
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
