/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.ice4j.ice.CandidatePair;
import org.jitsi.impl.neomedia.recording.RecorderEventHandlerJSONImpl;
import org.jitsi.impl.neomedia.recording.RecorderImpl;
import org.jitsi.impl.neomedia.recording.VideoRecorderImpl;
import org.jitsi.jirecon.JireconEvent;
import org.jitsi.jirecon.JireconEventId;
import org.jitsi.jirecon.JireconEventListener;
import org.jitsi.jirecon.dtlscontrol.JireconSrtpControlManager;
import org.jitsi.jirecon.session.JireconSessionInfo;
import org.jitsi.jirecon.transport.JireconTransportManager;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.service.neomedia.DefaultStreamConnector;
import org.jitsi.service.neomedia.DtlsControl;
import org.jitsi.service.neomedia.MediaDirection;
import org.jitsi.service.neomedia.MediaException;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaStream;
import org.jitsi.service.neomedia.MediaStreamStats;
import org.jitsi.service.neomedia.MediaStreamTarget;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.RTPTranslator;
import org.jitsi.service.neomedia.SrtpControl;
import org.jitsi.service.neomedia.SrtpControlType;
import org.jitsi.service.neomedia.StreamConnector;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jitsi.service.neomedia.recording.Recorder;
import org.jitsi.service.neomedia.recording.RecorderEventHandler;
import org.jitsi.util.Logger;

public class JireconRecorderImpl
    implements JireconRecorder
{
    List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    private Map<MediaType, MediaStream> streams =
        new HashMap<MediaType, MediaStream>();

    private JireconTransportManager transportManager;

    private JireconSrtpControlManager srtpControlManager;

    private MediaService mediaService;

    private Map<MediaType, RTPTranslator> rtpTranslators =
        new HashMap<MediaType, RTPTranslator>();

    // private Recorder videoRecorder;

    private Map<MediaType, Recorder> recorders =
        new HashMap<MediaType, Recorder>();

    private JireconRecorderInfo info;

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
        info = new JireconRecorderInfo();
        logger = Logger.getLogger(this.getClass());
    }

    @Override
    public void init(JireconConfiguration configuration,
        MediaService mediaService, JireconTransportManager transportManager,
        JireconSrtpControlManager srtpControlManager)
    {
        this.mediaService = mediaService;
        this.transportManager = transportManager;
        this.srtpControlManager = srtpControlManager;
        createMediaStreams();
    }

    @Override
    public void uninit()
    {
        // TODO Auto-generated method stub

    }

    // This method may throw exceptions in the future.
    @Override
    public void start()
    {
        logger.info("JireconRecorder start");
        updateState(JireconRecorderState.BUILDING);
        startReceivingStreams();
        prepareRecorders();
        try
        {
            startRecording();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            updateState(JireconRecorderState.ABORTED);
            return;
        }
        catch (MediaException e)
        {
            e.printStackTrace();
            updateState(JireconRecorderState.ABORTED);
            return;
        }
        updateState(JireconRecorderState.RECORDING);
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
        case BUILDING:
            fireEvent(new JireconEvent(this, JireconEventId.RECORDER_BUILDING));
            break;
        case RECEIVING:
            fireEvent(new JireconEvent(this, JireconEventId.RECORDER_RECEIVING));
            break;
        case RECORDING:
            fireEvent(new JireconEvent(this, JireconEventId.RECORDER_RECORDING));
            break;
        case ABORTED:
            fireEvent(new JireconEvent(this, JireconEventId.RECORDER_ABORTED));
            break;
        default:
            break;
        }
    }

    private void startRecording() throws IOException, MediaException
    {
        logger.info("JireconRecorder start recording");
        prepareRecorders();
        for (Entry<MediaType, Recorder> e : recorders.entrySet())
        {
            e.getValue().start("useless", ".");
            e.getValue().setEventHandler(
                new RecorderEventHandlerJSONImpl("./" + e.getKey() + "_meta"));
        }
    }

    private void prepareRecorders()
    {
        for (Entry<MediaType, RTPTranslator> e : rtpTranslators.entrySet())
        {
            Recorder recorder = new VideoRecorderImpl(e.getValue());
            recorders.put(e.getKey(), recorder);
        }
    }

    private void stopRecording()
    {
        for (Entry<MediaType, Recorder> e : recorders.entrySet())
        {
            e.getValue().stop();
        }
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

    private boolean startReceivingStreams()
    {
        logger.info("Jirecon start receiving streams");
        int startCount = 0;
        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
            {
                continue;
            }

            MediaStream stream = streams.get(mediaType);
            MediaStreamTarget target =
                transportManager.getStreamTarget(mediaType);
            stream.setTarget(target);
            stream.start();
            if (stream.isStarted())
            {
                startCount += 1;
            }
        }

        if (streams.size() == startCount)
        {
            updateState(JireconRecorderState.RECEIVING);
        }
        else
        {
            updateState(JireconRecorderState.ABORTED);
        }

        return true;
    }

    private void createMediaStreams()
    {
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
            System.out.println("JireconRecorderImpl prepareMediaStream "
                + mediaType + ", " + (stream.getLocalSourceID() & 0xFFFFFFFFL));
            // fireEvent(new JireconEvent(this,
            // JireconEventId.RECORDER_INIT_OK));
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

    @Override
    public void prepareMediaStreams(JireconSessionInfo sessionInfo)
    {
        logger.info("Jirecon prepare media streams");
        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
            {
                continue;
            }

            MediaStream stream = streams.get(mediaType);
            StreamConnector connector =
                transportManager.getStreamConnector(mediaType);
            stream.setConnector(connector);

            for (Entry<MediaFormat, Byte> e : sessionInfo.getPayloadTypes(
                mediaType).entrySet())
            {
                // System.out.println(mediaType + " stream add "
                // + e.getKey().getEncoding() + ", " + e.getValue());
                stream.addDynamicRTPPayloadType(e.getValue(), e.getKey());
                if (null == stream.getFormat())
                {
                    stream.setFormat(e.getKey());
                }
            }

            mediaService.createSrtpControl(SrtpControlType.DTLS_SRTP);
            SrtpControl dtlsControl =
                srtpControlManager.getSrtpControl(mediaType);
            // dtlsControl.setConnector(stream.getSrtpControl());
            stream.setRTPTranslator(getTranslator(mediaType));

        }
    }

    @Override
    public JireconRecorderInfo getRecorderInfo()
    {
        return info;
    }

}
