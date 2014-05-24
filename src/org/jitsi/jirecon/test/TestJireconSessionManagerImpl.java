/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.test;

import org.jitsi.jirecon.session.JireconSessionManager;
import org.jitsi.jirecon.session.JireconSessionManagerImpl;
import org.jitsi.jirecon.session.JireconSessionStatus;
import org.jivesoftware.smack.XMPPException;

import junit.framework.TestCase;

public class TestJireconSessionManagerImpl extends TestCase
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
        final String cf1 = "n2qer2v6zn3tyb9";
        final String cf2 = "jvxmxznhy4jnstt9";
        
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
            Thread.sleep(20000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        
        System.out.println(mgr.getSessionInfo(cf1).getSessionStatus());
        System.out.println(mgr.getSessionInfo(cf2).getSessionStatus());
        assertEquals(mgr.getSessionInfo(cf1).getSessionStatus(), JireconSessionStatus.CONSTRUCTED);
        assertEquals(mgr.getSessionInfo(cf2).getSessionStatus(), JireconSessionStatus.CONSTRUCTED);
        
        mgr.closeJingleSession(cf1);
        mgr.closeJingleSession(cf2);
    }
    
    @Override
    protected void tearDown()
    {
        mgr.uninit();
    }
}
