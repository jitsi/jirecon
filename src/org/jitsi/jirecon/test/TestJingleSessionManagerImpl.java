package org.jitsi.jirecon.test;

import org.jitsi.jirecon.session.JingleSessionManager;
import org.jitsi.jirecon.session.JingleSessionManagerImpl;
import org.jivesoftware.smack.XMPPException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestJingleSessionManagerImpl extends TestCase
{
    private static String hostname = "jitmeet.example.com";
    private static int port = 5222;
    private static JingleSessionManager mgr = new JingleSessionManagerImpl(hostname, port);
    
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
        final String cf1 = "6dqdr7254abrzfr";
        final String cf2 = "1bcw4c1rsuuzbyb9";
        
        try
        {
            mgr.openJingleSession(cf1);
            mgr.openJingleSession(cf2);
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
        
        mgr.closeJingleSession(cf1);
        mgr.closeJingleSession(cf2);
    }
    
    @Override
    protected void tearDown()
    {
        mgr.uninit();
    }
}
