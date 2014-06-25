/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.session;


import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.dtlscontrol.*;
import org.jitsi.jirecon.transport.*;

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
}
