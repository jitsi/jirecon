/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.test;

import org.jitsi.jirecon.*;
import org.jitsi.jirecon.TaskManagerEvent.*;
import junit.framework.TestCase;

public class TestTaskManager
    extends TestCase
{
    public void testStart()
    {
        final Object syncRoot = new Object();
        
        TaskManager j = new TaskManager();
        j.addEventListener(new JireconEventListener()
        {

            @Override
            public void handleEvent(TaskManagerEvent evt)
            {
                if (evt.getType() == TaskManagerEvent.Type.TASK_ABORTED)
                {
                    synchronized (syncRoot)
                    {
                        syncRoot.notifyAll();
                    }
                }
                
                else if (evt.getType() == TaskManagerEvent.Type.TASK_FINISED)
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

        String mucJid = "jyo0q5djqijdobt9@conference.example.com";
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
