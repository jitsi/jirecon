package org.jitsi.jirecon;

/**
 * This class represents the command to initiate Jirecon.
 * 
 * @author lishunyang
 * 
 */
public class JireconCmdInitiate
    extends JireconCmd
{
    /**
     * Constructor.
     * 
     * @param jirecon The executor.
     */
    public JireconCmdInitiate(Jirecon jirecon)
    {
        super(jirecon);
    }

    /**
     * How to execute this command.
     */
    @Override
    public void exec()
    {
        jirecon.initiate();
    }
}
