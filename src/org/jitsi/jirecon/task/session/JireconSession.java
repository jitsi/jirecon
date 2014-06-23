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

public interface JireconSession
{
    public JingleIQ connect(JireconTransportManager transportManager,
        SrtpControlManager srtpControlManager)
        throws XMPPException,
        OperationFailedException,
        IOException;

    public void disconnect(Reason reason, String reasonText);
}
