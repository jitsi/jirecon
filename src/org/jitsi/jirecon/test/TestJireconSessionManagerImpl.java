/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jitsi.jirecon.session.JireconSessionManager;
import org.jitsi.jirecon.session.JireconSessionManagerImpl;
import org.jitsi.jirecon.session.JireconSessionStatus;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.jirecon.utils.JireconConfigurationImpl;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.MediaType;
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
        catch (IOException e)
        {
            e.printStackTrace();
        }
        try
        {
            mgr = new JireconSessionManagerImpl();
            mgr.init(configuration);
        }
        catch (XMPPException e)
        {
            e.printStackTrace();
        }
    }

    public void testOpenAndCloseJingleSession()
    {
        final List<String> cf = new ArrayList<String>();
        cf.add("v1up1wzk1hh0k9@conference.example.com");

        try
        {
            for (String cfname : cf)
            {
                mgr.openJireconSession(cfname);
            }
        }
        catch (XMPPException e)
        {
            e.printStackTrace();
        }

        try
        {
            Thread.sleep(10000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        for (String cfname : cf)
        {
            System.out.println(mgr.getSessionInfo(cfname).getSessionStatus());
            assertEquals(mgr.getSessionInfo(cfname).getSessionStatus(),
                JireconSessionStatus.CONSTRUCTED);
            assertEquals(mgr.getSessionInfo(cfname).getRemoteSsrcs(MediaType.VIDEO).size(), 1);
        }

        for (String cfname : cf)
        {
            mgr.closeJireconSession(cfname);
        }
    }

    @Override
    protected void tearDown()
    {
        mgr.uninit();
    }
}
