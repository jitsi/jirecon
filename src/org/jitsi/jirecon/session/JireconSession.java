/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

import java.beans.PropertyChangeListener;

import org.jitsi.jirecon.JireconEventListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Packet;

public interface JireconSession
{
    public void startSession(String conferenceJid) throws XMPPException;

    public void terminateSession();

    public void handleSessionPacket(Packet packet);

    public JireconSessionInfo getSessionInfo();
    
    public void addEventListener(JireconEventListener listener);
    
    public void removeEventListener(JireconEventListener listener);
}
