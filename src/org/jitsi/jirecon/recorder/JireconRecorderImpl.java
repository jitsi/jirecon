/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.ice4j.ice.CandidatePair;
import org.jitsi.jirecon.JireconEvent;
import org.jitsi.jirecon.JireconEventListener;
import org.jitsi.jirecon.session.JireconSessionInfo;
import org.jitsi.jirecon.transport.JireconTransportManager;
import org.jitsi.jirecon.utils.JireconConfiguration;
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
    List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();
    
    private Map<MediaType, MediaStream> streams = new HashMap<MediaType, MediaStream>();

    private JireconTransportManager transportManager;
    private MediaService mediaService;
    private Map<MediaType, RTPTranslator> rtpTranslators = new HashMap<MediaType, RTPTranslator>();

    private RecorderInfo info;

    // TODO:
    // private Map<MediaType, Recorder> recorders;

    private Logger logger;

    /**
     * The conference recorder need an active media service.
     * 
     * @param mediaService
     */
    public JireconRecorderImpl()
    {
        info = new RecorderInfo();
        logger = Logger.getLogger(this.getClass());
    }
    
    @Override
    public void init(JireconConfiguration configuration, MediaService service, JireconTransportManager transportManager)
    {
        this.mediaService = mediaService;
        this.transportManager = transportManager;
    }

    @Override
    public void uninit()
    {
        // TODO Auto-generated method stub
        
    }

    // This method may throw exceptions in the future.
    @Override
    public void start(JireconSessionInfo info)
    {
        logger.info("JireconRecorder start");
        updateState(JireconRecorderState.INITIATING);
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

    private void updateState(JireconRecorderState state)
    {
        info.setState(state);
        switch (state)
        {
        case ABORTED:
            fireEvent(new JireconEvent(this, JireconEvent.State.ABORTED));
            break;
        default:
            break;
        }
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
            updateState(JireconRecorderState.RECVEIVING);
        }
        else
        {
            updateState(JireconRecorderState.ABORTED);
        }

        return true;
    }

    private MediaStream createMediaStream(JireconSessionInfo info,
        MediaType mediaType)
    {
        final StreamConnector connector = transportManager.getStreamConnector(mediaType);
        final MediaStream stream =
            mediaService.createMediaStream(connector, mediaType,
                mediaService.createSrtpControl(SrtpControlType.DTLS_SRTP));

        // TODO: Translator thins is not clear
        stream.setRTPTranslator(getTranslator(mediaType));

        stream.setName(mediaType.toString());
        stream.setDirection(MediaDirection.RECVONLY);
        for (Entry<MediaFormat, Byte> e : info.getPayloadTypes(mediaType)
            .entrySet())
        {
            stream.addDynamicRTPPayloadType(e.getValue(), e.getKey());
            if (null == stream.getFormat())
            {
                stream.setFormat(e.getKey());
            }
        }
        
        MediaStreamTarget target = transportManager.getStreamTarget(mediaType);
        stream.setTarget(target);

        return stream;
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
    
    @Override
    public void addEventListener(JireconEventListener listener)
    {
        listeners.add(listener);
    }

    @Override
    public void removeEventListener(JireconEventListener listener)
    {
        listeners.remove(listener);
    }

    @Override
    public JireconRecorderState getState()
    {
        return info.getState();
    }
    
    private void fireEvent(JireconEvent evt)
    {
        for (JireconEventListener l : listeners)
        {
            l.handleEvent(evt);
        }
    }

}
