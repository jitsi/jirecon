package org.jitsi.jirecon.session;

import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Packet;

public interface JireconSession
{
    public void startSession(String conferenceId) throws XMPPException;

    public void terminateSession();

    public void handleSessionPacket(Packet packet);

    public SessionInfo getSessionInfo();
}
