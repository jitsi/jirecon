/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.test;


import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.*;

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
        catch (OperationFailedException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            result = false;
        }

        String mucJid = "ia74os7qoc5cow29@conference.example.com";
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
