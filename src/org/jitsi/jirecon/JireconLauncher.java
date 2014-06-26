/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package org.jitsi.jirecon;

import java.io.IOException;
import java.util.*;

import org.jivesoftware.smack.XMPPException;

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

        for (String arg : args)
        {
            if (arg.startsWith(CONF_ARG_NAME))
                conf = arg.substring(CONF_ARG_NAME.length());
            else if (arg.startsWith(TIME_ARG_NAME))
                time = Long.valueOf(arg.substring(TIME_ARG_NAME.length()));
            else
                mucJids.add(arg);
        }

        if (mucJids.size() == 0)
        {
            System.out
                .println("You have to specify at least one conference jid to record, exit.");
            return;
        }
        if (null == conf)
            conf = "jirecon.properties";
        if (time < 0)
            time = 20;

        Jirecon jirecon = new JireconImpl();

        try
        {
            jirecon.init(conf);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return;
        }
        catch (XMPPException e)
        {
            e.printStackTrace();
            return;
        }

        for (String jid : mucJids)
            jirecon.startJireconTask(jid);

        try
        {
            Thread.sleep(time * 1000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        for (String jid : mucJids)
            jirecon.stopJireconTask(jid);

        jirecon.uninit();
    }
}
