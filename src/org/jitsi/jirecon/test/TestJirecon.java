/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.test;

import org.jitsi.jirecon.*;
import org.jitsi.jirecon.JireconEvent.*;
import junit.framework.TestCase;

public class TestJirecon
    extends TestCase
{
    public void testStart()
    {
        final Object syncRoot = new Object();
        
        Jirecon j = new Jirecon();
        j.addEventListener(new JireconEventListener()
        {

            @Override
            public void handleEvent(JireconEvent evt)
            {
                if (evt.getType() == JireconEvent.Type.TASK_ABORTED)
                {
                    synchronized (syncRoot)
                    {
                        syncRoot.notifyAll();
                    }
                }
                
                else if (evt.getType() == JireconEvent.Type.TASK_FINISED)
                {
                    synchronized (syncRoot)
                    {
                        syncRoot.notifyAll();
                    }
                }
            }
            
        });

        boolean result = true;

        try
        {
            j.init("jirecon.properties");
        }
        catch (Exception e)
        {
            e.printStackTrace();
            result = false;
        }

        String mucJid = "rkgq15wogh9f6r@conference.example.com";
        j.startJireconTask(mucJid);

        try
        {
            synchronized (syncRoot)
            {
                syncRoot.wait();
            }
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        assertTrue(result);

        j.uninit();
    }
}
