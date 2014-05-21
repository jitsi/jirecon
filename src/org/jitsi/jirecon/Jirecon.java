package org.jitsi.jirecon;

// TODO: This class hasn't been finished. 
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
     * @return
     */
    public void initiate();

    /**
     * Stop providing service.
     * @return
     */
    public void uninitiate();
    
    public void startRecording(String conferenceId);
    public void stopRecording(String conferenceId);
    
    public void execCmd(JireconCmd cmd);
}
