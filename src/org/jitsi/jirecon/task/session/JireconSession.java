/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.session;

import java.util.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.task.JireconTaskEventListener;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jivesoftware.smack.XMPPConnection;

/**
 * <tt>JireconSession</tt> is a session manager which is responsible for joining
 * specified XMPP MUC and building Jingle session.
 * 
 * @author lishunyang
 * 
 */
public interface JireconSession
{
    /**
     * Initialize <tt>JireconSession</tt>.
     * <p>
     * <strong>Warning:</strong> LibJitsi must be started before calling this
     * method.
     * 
     * @param connection is used for send/receive XMPP packet.
     */
    public void init(XMPPConnection connection);

    /**
     * Join a Multi-User-Chat of specified MUC jid.
     * 
     * @param mucJid The specified MUC jid.
     * @param nickname The name in MUC.
     * @throws OperationFailedException if failed to join MUC.
     */
    public void joinMUC(String mucJid, String nickname)
        throws OperationFailedException;
    
    /**
     * Wait for Jingle session-init packet after join the MUC.
     * <p>
     * <strong>Warning:</strong> This method will block for at most
     * <tt>MAX_WAIT_TIME</tt> ms if there isn't init packet.
     * 
     * @return Jingle session-init packet that we get.
     * @throws OperationFailedException if the method time out.
     */
    public JingleIQ waitForInitPacket() 
        throws OperationFailedException;

    /**
     * Send Jingle session-accept packet to the remote peer.
     * 
     * @param formatAndPTs
     * @param localSsrcs
     * @param transportPEs
     * @param fingerprintPEs
     */
    public void sendAcceptPacket(
        Map<MediaType, Map<MediaFormat, Byte>> formatAndPTs,
        Map<MediaType, Long> localSsrcs,
        Map<MediaType, AbstractPacketExtension> transportPEs,
        Map<MediaType, AbstractPacketExtension> fingerprintPEs);

    /**
     * Wait for ack packet.
     * <p>
     * <strong>Warning:</strong> This method will block for at most
     * <tt>MAX_WAIT_TIME</tt> ms if there isn't ack packet.
     */
    public void waitForResultPacket();
        
    /**
     * Disconnect with XMPP server and terminate the Jingle session.
     * 
     * @param reason <tt>Reason</tt> type of disconnecting.
     * @param reasonText The human-read reasons.
     */
    public void disconnect(Reason reason, String reasonText);

    /**
     * Add <tt>JireconTaskEvent</tt> listener.
     * 
     * @param listener
     */
    public void addTaskEventListener(JireconTaskEventListener listener);

    /**
     * Remove <tt>JireconTaskEvent</tt> listener.
     * 
     * @param listener
     */
    public void removeTaskEventListener(JireconTaskEventListener listener);

    /**
     * Get associated ssrc list.
     * <p>
     * Every participant usually has two ssrc(one for audio and one for video),
     * these two ssrc are associated.
     * 
     * @return Map between participant's jid and its associated ssrc.
     */
    public Map<String, Map<MediaType, Long>> getAssociatedSsrcs();
}
