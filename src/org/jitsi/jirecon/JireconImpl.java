package org.jitsi.jirecon;

// TODO: Rewrite those import statements to package import statement.
import java.util.HashSet;
import java.util.Set;

import org.jitsi.jirecon.recorder.JireconRecorderManager;
import org.jitsi.jirecon.recorder.JireconRecorderManagerImpl;
import org.jitsi.jirecon.session.JireconSessionManager;
import org.jitsi.jirecon.session.JireconSessionManagerImpl;
import org.jitsi.jirecon.utils.JireconMessageReceiver;
import org.jitsi.jirecon.utils.JireconMessageSender;
import org.jitsi.util.Logger;
import org.jivesoftware.smack.XMPPException;

/**
 * This is an implementation of Jirecon
 * 
 * @author lishunyang
 * 
 */
public class JireconImpl
    implements Jirecon
{
    /**
     * JireconImpl can send message to these message receivers.
     */
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
     * The session manager.
     */
    private JireconSessionManager jingleSessionManager;

    /**
     * The recorder manager.
     */
    private JireconRecorderManager conferenceRecorderManager;

    /**
     * The laborious logger.
     */
    private Logger logger;

    /**
     * Constructor.
     */
    public JireconImpl()
    {
        msgReceivers = new HashSet<JireconMessageReceiver>();
        logger = Logger.getLogger(JireconImpl.class);
        jingleSessionManager =
            new JireconSessionManagerImpl(jitsiMeetHostname, xmppServerPort);
        conferenceRecorderManager = new JireconRecorderManagerImpl();
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

    /**
     * Execute a command, this method provide another way to get service.
     */
    @Override
    public void execCmd(JireconCmd cmd)
    {
        cmd.exec();
    }

    /**
     * Start a conference recording
     * 
     * @param conferenceId The conference to be recorded.
     */
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

    /**
     * Stop a conference recording.
     * 
     * @param conferenceId The conference to be stopped recording.
     */
    @Override
    public void stopRecording(String conferenceId)
    {
        conferenceRecorderManager.stopRecording(conferenceId);
        jingleSessionManager.closeJingleSession(conferenceId);
    }
}
