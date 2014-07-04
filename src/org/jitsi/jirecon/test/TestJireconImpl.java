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
        final Object syncRoot = new Object();
        
        Jirecon j = new JireconImpl();
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
        catch (OperationFailedException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            result = false;
        }

        String mucJid = "0uf3ggrnrl51att9@conference.example.com";
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
