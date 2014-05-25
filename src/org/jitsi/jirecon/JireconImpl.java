/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

// TODO: Rewrite those import statements to package import statement.
import java.io.IOException;
import java.util.EventListener;

import org.jitsi.jirecon.recorder.JireconRecorderManager;
import org.jitsi.jirecon.recorder.JireconRecorderManagerImpl;
import org.jitsi.jirecon.session.JireconSessionEvent;
import org.jitsi.jirecon.session.JireconSessionInfo;
import org.jitsi.jirecon.session.JireconSessionManager;
import org.jitsi.jirecon.session.JireconSessionManagerImpl;
import org.jitsi.jirecon.session.JireconSessionStatus;
import org.jitsi.jirecon.utils.JireconConfigurationImpl;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.util.Logger;
import org.jivesoftware.smack.XMPPException;

/**
 * This is an implementation of Jirecon
 * 
 * @author lishunyang
 * 
 */
public class JireconImpl
    implements Jirecon, JireconEventListener
{
    /**
     * The session manager.
     */
    private JireconSessionManager sessionManager;

    /**
     * The recorder manager.
     */
    private JireconRecorderManager recorderManager;

    private JireconConfigurationImpl configuration;

    /**
     * The laborious logger.
     */
    private Logger logger;

    /**
     * Constructor.
     */
    public JireconImpl()
    {
        logger = Logger.getLogger(JireconImpl.class);
    }

    /**
     * Start providing service.
     * 
     * @throws IOException
     * @throws XMPPException
     */
    @Override
    public void initiate(String configurationPath)
        throws IOException,
        XMPPException
    {
        LibJitsi.start();
        configuration = new JireconConfigurationImpl();
        configuration.loadConfiguration(configurationPath);

        sessionManager = new JireconSessionManagerImpl();
        recorderManager = new JireconRecorderManagerImpl();

        sessionManager.init(configuration);
        recorderManager.init(configuration);

        // Binding message receiver and sender
        // ((JireconMessageSender) sessionManager)
        // .addReceiver((JireconMessageReceiver) recorderManager);
    }

    /**
     * Stop providing sevice.
     */
    @Override
    public void uninitiate()
    {
        sessionManager.uninit();
        recorderManager.uninit();
        LibJitsi.stop();
    }

    /**
     * Start a conference recording
     * 
     * @param conferenceJid The conference to be recorded.
     * @throws XMPPException
     */
    @Override
    public void startRecording(String conferenceJid) throws XMPPException
    {
        try
        {
            // NOTE: Open session firstly, and then start recorder through session event.
            sessionManager.openJireconSession(conferenceJid);
        }
        catch (XMPPException e)
        {
            logger.fatal("Jireson: Failed to start recording the conference "
                + conferenceJid + ".");
            throw e;
        }
    }

    /**
     * Stop a conference recording.
     * 
     * @param conferenceJid The conference to be stopped recording.
     */
    @Override
    public void stopRecording(String conferenceJid)
    {
        // NOTE: Stop recorder firstly, and then close session through recorder event.
        recorderManager.stopJireconRecorder(conferenceJid);
    }

    @Override
    public void handleEvent(JireconEvent evt)
    {
        if (evt instanceof JireconSessionEvent)
        {
            JireconSessionEvent jsEvt = (JireconSessionEvent) evt;
            JireconSessionInfo info = jsEvt.getJireconSessionInfo();
            switch (info.getSessionStatus())
            {
            case CONSTRUCTED:
                recorderManager.startJireconRecorder(info);
                break;
            case ABORTED:
                sessionManager.closeJireconSession(info.getConferenceJid());
                break;
            default:
                break;
            }
        }
    }

}
