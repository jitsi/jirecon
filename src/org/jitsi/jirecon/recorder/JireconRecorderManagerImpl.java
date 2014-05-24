/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

import java.util.HashMap;
import java.util.Map;

import org.jitsi.jirecon.session.SessionInfo;
import org.jitsi.jirecon.session.JireconSessionManager;
import org.jitsi.jirecon.utils.JireconFactory;
import org.jitsi.jirecon.utils.JireconFactoryImpl;
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
    private Map<String, JireconRecorder> recorders;

    private MediaService mediaService;

    private JireconFactory factory;

    private Logger logger;

    public JireconRecorderManagerImpl()
    {
        recorders = new HashMap<String, JireconRecorder>();
        logger = Logger.getLogger(this.getClass());
    }

    @Override
    public void startRecording(String conferenceId)
    {
        final JireconRecorder recorder = factory.createRecorder(mediaService);
        recorders.put(conferenceId, recorder);
    }

    @Override
    public void stopRecording(String conferenceId)
    {
        final JireconRecorder recorder = recorders.get(conferenceId);
        recorder.stop();
        recorders.remove(conferenceId);
    }

    @Override
    public void receiveMsg(JireconMessageSender sender, String conferenceId)
    {
        System.out.println("JireconRecorderManager receive a message");
        if (sender instanceof JireconSessionManager)
        {
            final SessionInfo info =
                ((JireconSessionManager) sender).getSessionInfo(conferenceId);
            switch (info.getSessionStatus())
            {
            case CONSTRUCTED:
                recorders.get(conferenceId).start(info);
                break;
            case ABORTED:
                recorders.get(conferenceId).stop();
                recorders.remove(conferenceId);
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
        LibJitsi.start();
        mediaService = LibJitsi.getMediaService();
        factory = new JireconFactoryImpl();
    }

    @Override
    public void uninit()
    {
        LibJitsi.stop();
    }

}
