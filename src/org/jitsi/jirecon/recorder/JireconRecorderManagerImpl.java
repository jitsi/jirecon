package org.jitsi.jirecon.recorder;

import java.util.HashMap;
import java.util.Map;

import org.jitsi.jirecon.session.SessionInfo;
import org.jitsi.jirecon.session.JireconSessionManager;
import org.jitsi.jirecon.utils.JireconMessageReceiver;
import org.jitsi.jirecon.utils.JireconMessageSender;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.util.Logger;

public class JireconRecorderManagerImpl
    implements JireconRecorderManager, JireconMessageReceiver
{

    /**
     * Bind conference id with ConferenceRecorder
     */
    private Map<String, JireconRecorderImpl> recorders;

    private MediaService mediaService;

    private Logger logger;

    public JireconRecorderManagerImpl()
    {
        recorders = new HashMap<String, JireconRecorderImpl>();
        mediaService = LibJitsi.getMediaService();
        logger = Logger.getLogger(this.getClass());
    }

    @Override
    public void startRecording(String conferenceId)
    {
        final JireconRecorderImpl recorder =
            new JireconRecorderImpl(mediaService);
        recorders.put(conferenceId, recorder);
    }

    @Override
    public void stopRecording(String conferenceId)
    {
        final JireconRecorderImpl recorder = recorders.get(conferenceId);
        recorder.stop();
        recorders.remove(conferenceId);
    }

    @Override
    public void receiveMsg(JireconMessageSender sender, String msg)
    {
        if (sender instanceof JireconSessionManager)
        {
            final SessionInfo info =
                ((JireconSessionManager) sender).getJingleSessionInfo(msg);
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
