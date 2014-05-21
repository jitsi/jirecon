package org.jitsi.jirecon;

public class JireconCmdInitiate extends JireconCmd
{
    public JireconCmdInitiate(Jirecon jirecon)
    {
        super(jirecon);
    }
    
    @Override
    public void exec()
    {
        jirecon.initiate();
    }
}
