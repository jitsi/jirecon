/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.session;

import java.io.IOException;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.dtlscontrol.*;
import org.jitsi.jirecon.transport.*;
import org.jivesoftware.smack.*;

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
     * @return Jingle session-init packet which can be used by other classes.
     * @throws XMPPException
     * @throws OperationFailedException if some operations failed and the
     *             connecting is aborted.
     * @throws IOException
     */
    // TODO: Unify the exceptions, merge the XMPPException and IOException into
    // OperationFailedException.
    // TODO: We should add a paremeter to specify the XMPP server host instead
    // of reading it from configuration files directly.
    public JingleIQ connect(JireconTransportManager transportManager,
        SrtpControlManager srtpControlManager)
        throws XMPPException,
        OperationFailedException,
        IOException;

    /**
     * Disconnect with XMPP server and terminate the Jingle session.
     * 
     * @param reason <tt>Reason</tt> type of disconnecting.
     * @param reasonText The human-read reasons.
     */
    public void disconnect(Reason reason, String reasonText);
}
