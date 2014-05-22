package org.jitsi.jirecon;

/**
 * Jirecon is responsible for recording conferences.
 * 
 * @author lishunyang
 * 
 */
public interface Jirecon
{
    /**
     * Start providing service.
     * 
     * @return
     */
    public void initiate();

    /**
     * Stop providing service.
     * 
     * @return
     */
    public void uninitiate();

    /**
     * Start recording a conference
     * 
     * @param conferenceId The conference to be recorded.
     */
    public void startRecording(String conferenceId);

    /**
     * Stop recording a conference
     * 
     * @param conferenceId The conference to be stopped.
     */
    public void stopRecording(String conferenceId);

    /**
     * Execute Jirecon command. Jirecon provides two kinds of calling method:
     * first, call the method directly, second, call the execCmd method.
     * 
     * @param cmd The command to be executed.
     */
    public void execCmd(JireconCmd cmd);
}
