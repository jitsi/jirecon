/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.test;

import java.io.IOException;

import org.jitsi.jirecon.*;
import org.jivesoftware.smack.XMPPException;

import junit.framework.TestCase;

public class TestJireconImpl
    extends TestCase
{
    public void testStart()
    {
        Jirecon j = new JireconImpl();

        boolean result = true;

        try
        {
            j.init("jirecon.properties");
        }
        catch (IOException e1)
        {
            e1.printStackTrace();
            result = false;
        }
        catch (XMPPException e1)
        {
            e1.printStackTrace();
            result = false;
        }
        
        String mucJid = "2fc9458vuwcgcik9@conference.example.com";
        j.startJireconTask(mucJid);

        try
        {
            Thread.sleep(20000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        assertTrue(result);

        j.uninit();
    }
}
