package org.jitsi.jirecon;

public abstract class JireconCmd
{
    protected Jirecon jirecon;
    
    public JireconCmd(Jirecon jirecon)
    {
        this.jirecon = jirecon;
    }
    
    public abstract void exec();
}
