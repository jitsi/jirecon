package org.jitsi.jirecon.test;

import org.jitsi.jirecon.Jirecon;
import org.jitsi.jirecon.JireconCmd;
import org.jitsi.jirecon.JireconCmdInitiate;
import org.jitsi.jirecon.JireconCmdStartRecording;
import org.jitsi.jirecon.JireconCmdUninitiate;
import org.jitsi.jirecon.JireconImpl;

import junit.framework.TestCase;

public class TestJireconImpl extends TestCase
{
    public void testAll()
    {
        Jirecon j = new JireconImpl();
        JireconCmd cmd;
        
        cmd = new JireconCmdInitiate(j);
        j.execCmd(cmd);
        
        try
        {
            Thread.sleep(10000);
        }
        catch (InterruptedException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        cmd = new JireconCmdUninitiate(j);
        j.execCmd(cmd);
    }
}
