package org.jitsi.jirecon;

/**
 * This class represents the command to let Jirecon start a recording.
 * 
 * @author lishunyang
 * 
 */
public class JireconCmdStartRecording
    extends JireconCmd
{
    /**
     * The id indicates which conference to be recorded.
     */
    private String conferenceId;

    /**
     * Constructor.
     * 
     * @param conferenceId Which conference to be recorded.
     * @param jirecon The executor.
     */
    public JireconCmdStartRecording(String conferenceId, Jirecon jirecon)
    {
        super(jirecon);
        this.conferenceId = conferenceId;
    }

    /**
     * How to execute this command.
     */
    @Override
    public void exec()
    {
        jirecon.startRecording(conferenceId);
    }
}
