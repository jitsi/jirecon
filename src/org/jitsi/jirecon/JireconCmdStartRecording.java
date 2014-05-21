package org.jitsi.jirecon;

public class JireconCmdStartRecording extends JireconCmd
{
    private String conferenceId;
    
    public JireconCmdStartRecording(String conferenceId, Jirecon jirecon)
    {
        super(jirecon);
        this.conferenceId = conferenceId;
    }
    
    @Override
    public void exec()
    {
        jirecon.startRecording(conferenceId);
    }
}
