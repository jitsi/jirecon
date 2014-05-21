package org.jitsi.jirecon;

// TODO: Rewrite those import statements to package import statement.
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jitsi.jirecon.recorder.ConferenceRecorderManager;
import org.jitsi.jirecon.recorder.ConferenceRecorderManagerImpl;
import org.jitsi.jirecon.session.JingleSessionManager;
import org.jitsi.jirecon.session.JingleSessionManagerImpl;
import org.jitsi.jirecon.utils.JireconMessageReceiver;
import org.jitsi.jirecon.utils.JireconMessageSender;
import org.jitsi.util.Logger;
import org.jivesoftware.smack.XMPPException;

// TODO: This class hasn't finished yet.
/**
 * This is an implementation of Jirecon
 * 
 * @author lishunyang
 * 
 */
public class JireconImpl
    implements Jirecon
{
    Set<JireconMessageReceiver> msgReceivers;

    /**
     * The host name of JitsiMeet. This property should be set in configuration
     * files.
     */
    private String jitsiMeetHostname = "jitmeet.example.com";

    /**
     * The port of XMPP server. This property should be set in configuration
     * file.
     */
    private int xmppServerPort = 5222;

    /**
     * The Jingle session manager.
     */
    private JingleSessionManager jingleSessionManager;

    private ConferenceRecorderManager conferenceRecorderManager;

    private Logger logger;

    /**
     * Constructor.
     */
    public JireconImpl()
    {
        msgReceivers = new HashSet<JireconMessageReceiver>();
        logger = Logger.getLogger(JireconImpl.class);
        jingleSessionManager =
            new JingleSessionManagerImpl(jitsiMeetHostname, xmppServerPort);
        conferenceRecorderManager = new ConferenceRecorderManagerImpl();
    }

    /**
     * Start providing service.
     */
    @Override
    public void initiate()
    {
        try
        {
            jingleSessionManager.init();
            conferenceRecorderManager.init();
        }
        catch (XMPPException e)
        {
            logger.fatal("Jirecon initialize failed, exit");
            return;
        }

        // Binding message receiver and sender
        ((JireconMessageSender) jingleSessionManager)
            .addReceiver((JireconMessageReceiver) conferenceRecorderManager);
    }

    /**
     * Stop providing sevice.
     */
    @Override
    public void uninitiate()
    {
        jingleSessionManager.uninit();
        conferenceRecorderManager.uninit();
    }

    @Override
    public void execCmd(JireconCmd cmd)
    {
        cmd.exec();
    }

    @Override
    public void startRecording(String conferenceId)
    {
        try
        {
            jingleSessionManager.openJingleSession(conferenceId);
            conferenceRecorderManager.startRecording(conferenceId);
        }
        catch (XMPPException e)
        {
            logger.fatal("Start recording conference " + conferenceId
                + " failed.");
        }
    }

    @Override
    public void stopRecording(String conferenceId)
    {
        conferenceRecorderManager.stopRecording(conferenceId);
        jingleSessionManager.closeJingleSession(conferenceId);
    }
}
