package org.jitsi.jirecon;

/**
 * This abstract class represents the commands that can be executed by Jirecon.
 * 
 * @author lishunyang
 * 
 */
public abstract class JireconCmd
{
    /**
     * The executor.
     */
    protected Jirecon jirecon;

    /**
     * Constructor.
     * 
     * @param jirecon The executor.
     */
    public JireconCmd(Jirecon jirecon)
    {
        this.jirecon = jirecon;
    }

    /**
     * How to execute this command.
     */
    public abstract void exec();
}
