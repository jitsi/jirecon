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

package org.jitsi.jirecon;

import java.util.*;
import java.util.concurrent.*;
import org.jitsi.jirecon.TaskManagerEvent.*;


/**
 * A launch application which is used to run <tt>TaskManager</tt>.
 * <p>
 * Usually there will be a associated Shell script to start this application.
 * 
 * @author lishunyang
 * 
 */
public class Main
{
    /**
     * Prefix of configuration parameter.
     */
    private static final String CONF_ARG_NAME = "--conf=";

    /**
     * Prefix of time parameter.
     */
    private static final String TIME_ARG_NAME = "--time=";

    /**
     * Configuration file path.
     */
    private static String conf;

    /**
     * How many seconds each recording task will persist.
     */
    private static long time;
    
    /**
     * The number of recording task.
     */
    private static int taskCount;
    
    /**
     * Application entry.
     * 
     * @param args <tt>JireconLauncher</tt> only cares about two arguments:
     *            <p>
     *            1. --conf=CONFIGURATION FILE PATH. Indicate the path of
     *            configuration file.
     *            <p>
     *            2. --time=RECORDING SECONDS. Indicate how many seconds will
     *            the recording task last. If you didn't specify this parameter,
     *            Jirecon will continue recording forever unless all
     *            participants has left the meeting.
     */
    public static void main(String[] args)
    {
        conf = null;
        time = -1;
        List<String> mucJids = new ArrayList<String>();
        final Object syncRoot = new Object();

        for (String arg : args)
        {
            if (arg.startsWith(CONF_ARG_NAME))
                conf = arg.substring(CONF_ARG_NAME.length());
            else if (arg.startsWith(TIME_ARG_NAME))
                time = Long.valueOf(arg.substring(TIME_ARG_NAME.length()));
            else
                mucJids.add(arg);
        }

        taskCount = mucJids.size();
        if (0 == taskCount)
        {
            System.out
                .println("You have to specify at least one conference jid to record, exit.");
            return;
        }
        if (null == conf)
            conf = "jirecon.properties";

        final TaskManager jirecon = new TaskManager();
        
        jirecon.addEventListener(new JireconEventListener()
        {
            @Override
            public void handleEvent(TaskManagerEvent evt)
            {
                if (evt.getType() == TaskManagerEvent.Type.TASK_ABORTED
                    || evt.getType() == TaskManagerEvent.Type.TASK_FINISED)
                {
                    taskCount--;
                    System.out.println("Task: " + evt.getMucJid() + " " + evt.getType());
                    
                    if (0 == taskCount)
                    {
                        synchronized (syncRoot)
                        {
                            syncRoot.notifyAll();
                        }
                    }
                }
            }
        });

        try
        {
            jirecon.init(conf);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return;
        }
        

        for (String jid : mucJids)
            jirecon.startJireconTask(jid);
        
        new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                if (time > 0)
                {
                    try
                    {
                        TimeUnit.SECONDS.sleep(time);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                    synchronized (syncRoot)
                    {
                        syncRoot.notifyAll();
                    }
                }
            }
            
        }).start();

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

        for (String jid : mucJids)
            jirecon.stopJireconTask(jid, true);

        jirecon.uninit();
        System.out.println("JireconLauncher exit.");
    }
}
