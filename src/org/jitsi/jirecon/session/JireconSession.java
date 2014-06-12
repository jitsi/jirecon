/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

import java.io.IOException;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.dtlscontrol.*;
import org.jitsi.jirecon.recorder.*;
import org.jitsi.jirecon.transport.*;
import org.jivesoftware.smack.*;

public interface JireconSession
{
    public JingleIQ connect(JireconSessionInfo sessionInfo,
        JireconRecorderInfo recorderInfo,
        JireconTransportManager transportManager,
        JireconSrtpControlManager srtpControlManager) throws XMPPException, OperationFailedException, IOException;
    
    public void disconnect(Reason reason, String reasonText);

    public JireconSessionInfo getSessionInfo();
    
    public void writeMetaData(String filename) throws IOException;

    // public void addEventListener(JireconEventListener listener);

    // public void removeEventListener(JireconEventListener listener);

    // public void sendAcceptPacket(JireconRecorderInfo info);
}
