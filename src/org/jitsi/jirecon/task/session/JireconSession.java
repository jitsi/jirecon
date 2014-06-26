/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.session;

import java.util.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.dtlscontrol.*;
import org.jitsi.jirecon.task.JireconTaskEventListener;
import org.jitsi.jirecon.transport.*;
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
     * Connect with XMPP server and build a Jingle session.
     * 
     * @param transportManager which is used for building ICE connectivity.
     * @param srtpControlManager which is used for SRTP support.
     * @param mucJid The specified MUC jid.
     * @param nickname which is name used in MUC.
     * @return Jingle session-init packet which can be used by other classes.
     * @throws OperationFailedException if some operations failed and the
     *             connecting is aborted.
     */
    public JingleIQ connect(JireconTransportManager transportManager,
        SrtpControlManager srtpControlManager, String mucJid, String nickname)
        throws OperationFailedException;

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
     * Tell <tt>JireconSession</tt> what local ssrcs are.
     * 
     * @param ssrcs Map between <tt>MediaType</tt> and ssrc.
     */
    public void setLocalSsrcs(Map<MediaType, Long> ssrcs);

    /**
     * Get associated ssrc list.
     * <p>
     * Every participant usually has two ssrc(one for audio and one for video),
     * these two ssrc are associated.
     * 
     * @return Map between participant's jid and its associated ssrc.
     */
    public Map<String, List<String>> getAssociatedSsrcs();

    /**
     * Get map between <tt>MediaFormat</tt> and dynamic payload type.
     * 
     * @return Map between <tt>MediaFormat</tt> and dynamic payload type.
     */
    public Map<MediaFormat, Byte> getFormatAndPayloadType();
}
