/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.dtlscontrol.*;
import org.jitsi.jirecon.recorder.*;
import org.jitsi.jirecon.transport.*;
import org.jitsi.jirecon.utils.*;
import org.jivesoftware.smack.*;

public interface JireconSession
{
    public void init(JireconConfiguration configuration,
        XMPPConnection connection, String conferenceJid);

    public void uninit();

    public void joinConference() throws XMPPException;

    public void leaveConference();

    public void sendAck(JingleIQ jiq);

    public void sendAccpetPacket(JireconSessionInfo sessionInfo,
        JireconRecorderInfo recorderInfo,
        JireconTransportManager transportManager,
        JireconSrtpControlManager srtpControlManager);

    public void sendByePacket(Reason reason, String reasonText);

    public JingleIQ waitForInitPacket() throws OperationFailedException;

    public void waitForAckPacket() throws OperationFailedException;

    public void recordSessionInfo(JingleIQ jiq);

    public JireconSessionInfo getSessionInfo();

    // public void addEventListener(JireconEventListener listener);

    // public void removeEventListener(JireconEventListener listener);

    // public void sendAcceptPacket(JireconRecorderInfo info);
}
