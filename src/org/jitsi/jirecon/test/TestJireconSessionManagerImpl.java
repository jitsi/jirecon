/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.test;

import java.io.IOException;

import org.jitsi.jirecon.session.JireconSessionManager;
import org.jitsi.jirecon.session.JireconSessionManagerImpl;
import org.jitsi.jirecon.session.JireconSessionStatus;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.jirecon.utils.JireconConfigurationImpl;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jivesoftware.smack.XMPPException;

import junit.framework.TestCase;

public class TestJireconSessionManagerImpl
    extends TestCase
{
    private static JireconSessionManager mgr;

    @Override
    protected void setUp()
    {
        LibJitsi.start();
        final JireconConfiguration configuration =
            new JireconConfigurationImpl();
        try
        {
            configuration.loadConfiguration("jirecon.property");
        }
        catch (IOException e1)
        {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        try
        {
            mgr = new JireconSessionManagerImpl();
            mgr.init(configuration);
        }
        catch (XMPPException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void testOpenAndCloseJingleSession()
    {
        final String cf1 = "josyq89md1hjv2t9@conference.example.com";
//        final String cf2 = "jvxmxznhy4jnstt9";

        try
        {
            mgr.openJireconSession(cf1);
//            mgr.openJingleSession(cf2);
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

        System.out.println(mgr.getSessionInfo(cf1).getSessionStatus());
//        System.out.println(mgr.getSessionInfo(cf2).getSessionStatus());
        assertEquals(mgr.getSessionInfo(cf1).getSessionStatus(),
            JireconSessionStatus.CONSTRUCTED);
//        assertEquals(mgr.getSessionInfo(cf2).getSessionStatus(),
//            JireconSessionStatus.CONSTRUCTED);

        mgr.closeJireconSession(cf1);
//        mgr.closeJingleSession(cf2);
    }

    @Override
    protected void tearDown()
    {
        mgr.uninit();
    }
}
