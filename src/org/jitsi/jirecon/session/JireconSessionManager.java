/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

import org.jitsi.jirecon.utils.JireconConfiguration;
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
    public void init(JireconConfiguration configuration) throws XMPPException;

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
     * @param conferenceName The conference's name which you want to join. The
     *            conference jid will be pattern
     *            <conferenceName>@<conferenceNode>. ATTENTION, NOT FULL JID.
     *            jid.
     * @throws XMPPException
     */
    public void openJingleSession(String conferenceName) throws XMPPException;

    /**
     * Close an existed Jingle session with specified conference id.
     * 
     * @param conferenceJid
     */
    public void closeJingleSession(String conferenceJid);

    public SessionInfo getSessionInfo(String conferenceJid);

}
