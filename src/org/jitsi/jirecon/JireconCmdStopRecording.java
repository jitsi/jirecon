package org.jitsi.jirecon;

public class JireconCmdStopRecording extends JireconCmd
{
    private String conferenceId;
    
    public JireconCmdStopRecording(String conferenceId, Jirecon jirecon)
    {
        super(jirecon);
        this.conferenceId = conferenceId;
    }
    
    @Override
    public void exec()
    {
        jirecon.stopRecording(conferenceId);
    }
}
