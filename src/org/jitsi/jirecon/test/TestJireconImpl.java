/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.test;

import java.io.IOException;

import org.jitsi.jirecon.Jirecon;
import org.jitsi.jirecon.JireconImpl;
import org.jivesoftware.smack.XMPPException;

import junit.framework.TestCase;

public class TestJireconImpl extends TestCase
{
    public void testAll()
    {
        Jirecon j = new JireconImpl();
        
        boolean result = true;
        
        try
        {
            j.initiate("jirecon.property");
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
        
        try
        {
            Thread.sleep(10000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        
        assertTrue(result);
        
        j.uninitiate();
    }
}
