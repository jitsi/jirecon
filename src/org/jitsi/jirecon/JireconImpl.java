/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

// TODO: Rewrite those import statements to package import statement.
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.jitsi.jirecon.recorder.JireconRecorderManager;
import org.jitsi.jirecon.recorder.JireconRecorderManagerImpl;
import org.jitsi.jirecon.session.JireconSessionManager;
import org.jitsi.jirecon.session.JireconSessionManagerImpl;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.jirecon.utils.JireconMessageReceiver;
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
     * The session manager.
     */
    private JireconSessionManager sessionManager;

    /**
     * The recorder manager.
     */
    private JireconRecorderManager recorderManager;

    private JireconConfiguration configuration;

    /**
     * The laborious logger.
     */
    private Logger logger;
    
    private final String MEETING_HOST_KEY = "MEETING_HOST";
    private final String XMPP_PORT_KEY = "XMPP_PORT";

    /**
     * Constructor.
     */
    public JireconImpl()
    {
        msgReceivers = new HashSet<JireconMessageReceiver>();
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
        configuration = new JireconConfiguration();
        configuration.loadConfiguration(configurationPath);
        
        String meetingHost = configuration.getProperty(MEETING_HOST_KEY);
        int xmppPort = Integer.valueOf(configuration.getProperty(XMPP_PORT_KEY));
        sessionManager =
            new JireconSessionManagerImpl(meetingHost, xmppPort);
        recorderManager = new JireconRecorderManagerImpl();

        sessionManager.init();
        recorderManager.init();

        // Binding message receiver and sender
//        ((JireconMessageSender) sessionManager)
//            .addReceiver((JireconMessageReceiver) recorderManager);
    }

    /**
     * Stop providing sevice.
     */
    @Override
    public void uninitiate()
    {
        sessionManager.uninit();
        recorderManager.uninit();
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
            sessionManager.openJingleSession(conferenceId);
            recorderManager.startRecording(conferenceId);
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
        recorderManager.stopRecording(conferenceId);
        sessionManager.closeJingleSession(conferenceId);
    }
}
