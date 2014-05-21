package org.jitsi.jirecon.test;

import org.jitsi.jirecon.session.JingleSessionManager;
import org.jitsi.jirecon.session.JingleSessionManagerImpl;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestJingleSessionManagerImpl extends TestCase
{
    private static String hostname = "jitmeet.example.com";
    private static int port = 5222;
    private static JingleSessionManager mgr = new JingleSessionManagerImpl(hostname, port);
    private static boolean isInitialized = false;
    
    @Override
    protected void setUp()
    {
        isInitialized = mgr.init();
    }
    
    public void testInit()
    {
        assertTrue(isInitialized);
    }
    
    public void testOpenAndCloseJingleSession()
    {
        final String cf1 = "6dqdr7254abrzfr";
        final String cf2 = "1bcw4c1rsuuzbyb9";
        
        assertTrue(mgr.openAJingleSession(cf1));
        assertTrue(mgr.openAJingleSession(cf2));
        
        try
        {
            Thread.sleep(10000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        
        assertTrue(mgr.closeAJingleSession(cf1));
        assertTrue(mgr.closeAJingleSession(cf2));
    }
    
    @Override
    protected void tearDown()
    {
        mgr.uninit();
    }
}
