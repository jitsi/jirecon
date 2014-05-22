package org.jitsi.jirecon.session;

import org.jivesoftware.smack.XMPPException;

/**
 * JingleSessionManager is responsible for managing various JingleSessions.
 * 
 * @author lishunyang
 * 
 */
public interface JireconSessionManager
{
    /**
     * Initialize JingleSessoinManager, such as applying for some system
     * resources. This method should be called at the very beginning.
     * 
     * @throws XMPPException Throws XMPPException if can't construct XMPP
     *             connection.
     */
    public void init() throws XMPPException;

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
     * @throws XMPPException
     */
    public void openJingleSession(String conferenceId) throws XMPPException;

    /**
     * Close an existed Jingle session with specified conference id.
     * 
     * @param conferenceId
     */
    public void closeJingleSession(String conferenceId);
    
    public SessionInfo getJingleSessionInfo(String conferenceId);

}
