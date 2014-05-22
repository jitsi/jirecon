package org.jitsi.jirecon;

/**
 * This class represents the command to uninitiate Jirecon.
 * 
 * @author lishunyang
 * 
 */
public class JireconCmdUninitiate
    extends JireconCmd
{
    /**
     * Constructor.
     * 
     * @param jirecon The executor.
     */
    public JireconCmdUninitiate(Jirecon jirecon)
    {
        super(jirecon);
    }

    /**
     * How to execute this command.
     */
    @Override
    public void exec()
    {
        jirecon.uninitiate();
    }
}
