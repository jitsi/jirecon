/*
/*
 * Jirecon, the JItsi REcording COntainer.
 *
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
