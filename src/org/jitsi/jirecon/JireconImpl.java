package org.jitsi.jirecon;

// TODO: Rewrite those import statements to package import statement.
import org.jitsi.jirecon.session.JingleSessionManager;
import org.jitsi.jirecon.session.JingleSessionManagerImpl;

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

    /**
     * Constructor.
     */
    public JireconImpl()
    {
        jingleSessionManager =
            new JingleSessionManagerImpl(jitsiMeetHostname, xmppServerPort);
    }

    /**
     * Start providing service.
     */
    @Override
    public boolean start()
    {
        boolean ret = false;
        ret = jingleSessionManager.init();

        // TODO: Recorder stuff

        return ret;
    }

    /**
     * Stop providing sevice.
     */
    @Override
    public boolean stop()
    {
        boolean ret = false;
        ret = jingleSessionManager.uninit();

        // TODO: Recorder stuff

        return ret;
    }

}
