/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

import java.util.HashMap;
import java.util.Map;

import org.jitsi.jirecon.session.JireconSessionInfo;
import org.jitsi.jirecon.session.JireconSessionManager;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.jirecon.utils.JireconFactory;
import org.jitsi.jirecon.utils.JireconFactoryImpl;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.util.Logger;

public class JireconRecorderManagerImpl
    implements JireconRecorderManager
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
    public void startJireconRecorder(JireconSessionInfo info)
    {
        final JireconRecorder recorder = factory.createRecorder(mediaService);
        recorders.put(info.getConferenceJid(), recorder);
        // TODO
    }

    @Override
    public void stopJireconRecorder(String conferenceJid)
    {
        final JireconRecorder recorder = recorders.get(conferenceJid);
        recorder.stop();
        recorders.remove(conferenceJid);
    }

    @Override
    public void init(JireconConfiguration configuration)
    {
        // NOTE: We must make sure that the LibJitsi has been initiated.
        mediaService = LibJitsi.getMediaService();
        factory = new JireconFactoryImpl();
    }

    @Override
    public void uninit()
    {
    }
}
