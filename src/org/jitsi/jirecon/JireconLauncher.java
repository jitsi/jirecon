/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package org.jitsi.jirecon;

import java.util.*;
import java.util.concurrent.*;
import org.jitsi.jirecon.JireconEvent.*;


/**
 * A launch application which is used to run <tt>Jirecon</tt>.
 * <p>
 * Usually there will be a associated Shell script to start this application.
 * 
 * @author lishunyang
 * 
 */
public class JireconLauncher
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
     *            1. --conf=CONF FILE PATH
     *            <p>
     *            2. --time=RECORDING SECONDS
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

        final Jirecon jirecon = new Jirecon();
        
        jirecon.addEventListener(new JireconEventListener()
        {
            @Override
            public void handleEvent(JireconEvent evt)
            {
                if (evt.getType() == JireconEvent.Type.TASK_ABORTED
                    || evt.getType() == JireconEvent.Type.TASK_FINISED)
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
