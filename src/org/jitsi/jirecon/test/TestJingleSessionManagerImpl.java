package org.jitsi.jirecon.test;

import org.jitsi.jirecon.session.JireconSessionManager;
import org.jitsi.jirecon.session.JireconSessionManagerImpl;
import org.jitsi.jirecon.session.JireconSessionStatus;
import org.jivesoftware.smack.XMPPException;

import junit.framework.TestCase;

public class TestJingleSessionManagerImpl extends TestCase
{
    private static String hostname = "jitmeet.example.com";
    private static int port = 5222;
    private static JireconSessionManager mgr = new JireconSessionManagerImpl(hostname, port);
    
    @Override
    protected void setUp()
    {
        try
        {
            mgr.init();
        }
        catch (XMPPException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public void testOpenAndCloseJingleSession()
    {
        final String cf1 = "eifb7fii8dunmi";
        //final String cf2 = "1bcw4c1rsuuzbyb9";
        
        try
        {
            mgr.openJingleSession(cf1);
            //mgr.openJingleSession(cf2);
        }
        catch (XMPPException e1)
        {
            e1.printStackTrace();
        }
        
        try
        {
            Thread.sleep(10000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        
        System.out.println(mgr.getJingleSessionInfo(cf1).getJingleSessionStatus());
        assertEquals(mgr.getJingleSessionInfo(cf1).getJingleSessionStatus(), JireconSessionStatus.CONSTRUCTED);
        
        mgr.closeJingleSession(cf1);
        //mgr.closeJingleSession(cf2);
    }
    
    @Override
    protected void tearDown()
    {
        mgr.uninit();
    }
}
