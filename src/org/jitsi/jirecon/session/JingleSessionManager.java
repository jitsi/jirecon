package org.jitsi.jirecon.session;

/**
 * JingleSessionManager is responsible for managing various JingleSessions.
 * 
 * @author lishunyang
 * 
 */
public interface JingleSessionManager
{
    /**
     * Initialize JingleSessoinManager, such as applying for some system
     * resources. This method should be called at the very beginning.
     * 
     * @return True if succeeded, false if failed.
     */
    public boolean init();

    /**
     * Uninitialize JingleSessionManager, such as release some system resources.
     * This method should be called at the end.
     * 
     * @return True if succeeded, false if failed.
     */
    public boolean uninit();

    /**
     * Open an new Jingle session with specified conference id.
     * 
     * @param conferenceId The conference id which you want to join.
     * @return True if succeeded, false if failed.
     */
    public boolean openAJingleSession(String conferenceId);

    /**
     * Close an existed Jingle session with specified conference id.
     * 
     * @param conferenceId
     * @return True if succeeded, false if failed.
     */
    public boolean closeAJingleSession(String conferenceId);

}
