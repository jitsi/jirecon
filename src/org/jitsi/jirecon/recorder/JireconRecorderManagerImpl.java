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
import org.jitsi.jirecon.utils.JireconConfiguration;
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
    public void startRecording(String conferenceJid)
    {
        final JireconRecorder recorder = factory.createRecorder(mediaService);
        recorders.put(conferenceJid, recorder);
    }

    @Override
    public void stopRecording(String conferenceJid)
    {
        final JireconRecorder recorder = recorders.get(conferenceJid);
        recorder.stop();
        recorders.remove(conferenceJid);
    }

    @Override
    public void receiveMsg(JireconMessageSender sender, String conferenceJid)
    {
        System.out.println("JireconRecorderManager receive a message");
        if (sender instanceof JireconSessionManager)
        {
            final SessionInfo info =
                ((JireconSessionManager) sender).getSessionInfo(conferenceJid);
            switch (info.getSessionStatus())
            {
            case CONSTRUCTED:
                recorders.get(conferenceJid).start(info);
                break;
            case ABORTED:
                recorders.get(conferenceJid).stop();
                recorders.remove(conferenceJid);
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
    public void init(JireconConfiguration configuration)
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
