package org.jitsi.jirecon;

public class JireconCmdUninitiate extends JireconCmd
{
    public JireconCmdUninitiate(Jirecon jirecon)
    {
        super(jirecon);
    }
    
    @Override
    public void exec()
    {
        jirecon.uninitiate();
    }
}
