package org.jitsi.jirecon;

/**
 * This class represents the command to let Jirecon stop a recording.
 * 
 * @author lishunyang
 * 
 */
public class JireconCmdStopRecording
    extends JireconCmd
{
    /**
     * The id indicates which conference to be stopped recording.
     */
    private String conferenceId;

    /**
     * Constructor.
     * 
     * @param conferenceId Which conference to be stopped recording.
     * @param jirecon The executor.
     */
    public JireconCmdStopRecording(String conferenceId, Jirecon jirecon)
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
        jirecon.stopRecording(conferenceId);
    }
}
