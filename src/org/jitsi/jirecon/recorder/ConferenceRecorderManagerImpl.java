package org.jitsi.jirecon.recorder;

import java.util.HashMap;
import java.util.Map;

import org.jitsi.jirecon.session.JingleSessionInfo;
import org.jitsi.jirecon.session.JingleSessionManager;
import org.jitsi.jirecon.utils.JireconMessageReceiver;
import org.jitsi.jirecon.utils.JireconMessageSender;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.util.Logger;

public class ConferenceRecorderManagerImpl
    implements ConferenceRecorderManager, JireconMessageReceiver
{

    /**
     * Bind conference id with ConferenceRecorder
     */
    private Map<String, ConferenceRecorder> recorders;

    private MediaService mediaService;

    private Logger logger;

    public ConferenceRecorderManagerImpl()
    {
        recorders = new HashMap<String, ConferenceRecorder>();
        mediaService = LibJitsi.getMediaService();
        logger = Logger.getLogger(this.getClass());
    }

    @Override
    public void startRecording(String conferenceId)
    {
        final ConferenceRecorder recorder =
            new ConferenceRecorder(mediaService);
        recorders.put(conferenceId, recorder);
    }

    @Override
    public void stopRecording(String conferenceId)
    {
        final ConferenceRecorder recorder = recorders.get(conferenceId);
        recorder.stop();
        recorders.remove(conferenceId);
    }

    @Override
    public void receiveMsg(JireconMessageSender sender, String msg)
    {
        if (sender instanceof JingleSessionManager)
        {
            final JingleSessionInfo info =
                ((JingleSessionManager) sender).getJingleSessionInfo(msg);
            switch (info.getJingleSessionStatus())
            {
            case ABORTED:
                // TODO
                break;
            case CONSTRUCTED:
                recorders.get(msg).start(info);
                break;
            default:
                break;
            }
        }
        else
        {
            logger
                .info("ConferenceRecorderManager receive a message from unknown sender");
        }
    }

    @Override
    public void init()
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void uninit()
    {
        // TODO Auto-generated method stub

    }

}
