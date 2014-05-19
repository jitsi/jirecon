package jirecon;

import jirecon.session.JingleSessionManager;
import jirecon.session.JingleSessionManagerImpl;

public class JireconImpl implements Jirecon
{
    private String jitsiMeetHostname = "jitmeet.example.com";
    private int xmppServerPort = 5222;
    private JingleSessionManager jingleSessionManager;
    
    public JireconImpl()
    {
        jingleSessionManager = new JingleSessionManagerImpl(jitsiMeetHostname, xmppServerPort);
    }

    @Override
    public boolean start()
    {
        boolean ret = false;
        ret = jingleSessionManager.init();
        // TODO: Recorder stuff
        
        return ret;
    }

    @Override
    public boolean stop()
    {
        boolean ret = false;
        ret = jingleSessionManager.uninit();
        // TODO: Recorder stuff
        
        return ret;
    }

}
