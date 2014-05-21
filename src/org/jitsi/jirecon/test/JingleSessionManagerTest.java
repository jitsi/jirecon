package org.jitsi.jirecon.test;

// TODO: Rewrite those import statements to package import statement.
import org.jitsi.jirecon.session.JingleSessionManager;
import org.jitsi.jirecon.session.JingleSessionManagerImpl;

// TODO: Add unit test
/**
 * This class is used for testing JingleSessionManager
 * 
 * @author lishunyang
 * 
 */
public class JingleSessionManagerTest
{
    /**
     * Simple test of JingleSessionManager
     * 
     * @param args
     */
    public static void main(String[] args)
    {
        JingleSessionManager mgr =
            new JingleSessionManagerImpl("jitmeet.example.com", 5222);

        mgr.init();

        mgr.openAJingleSession("6dqdr7254abrzfr");
        mgr.openAJingleSession("1bcw4c1rsuuzbyb9");

        try
        {
            Thread.sleep(10000);
        }
        catch (InterruptedException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        mgr.closeAJingleSession("1bcw4c1rsuuzbyb9");

        mgr.uninit();
    }
}
