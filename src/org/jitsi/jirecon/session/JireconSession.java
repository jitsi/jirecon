/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

import java.beans.PropertyChangeListener;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.JireconEventListener;
import org.jitsi.jirecon.dtlscontrol.JireconSrtpControlManager;
import org.jitsi.jirecon.recorder.JireconRecorderInfo;
import org.jitsi.jirecon.transport.JireconTransportManager;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.service.neomedia.DtlsControl;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Packet;

public interface JireconSession
{
    public void init(JireconConfiguration configuration,
        XMPPConnection connection, String conferenceJid,
        JireconTransportManager transportManager,
        JireconSrtpControlManager srtpControlManager);

    public void uninit();

    public void start();

    public void stop();

    public JireconSessionInfo getSessionInfo();

    public void addEventListener(JireconEventListener listener);

    public void removeEventListener(JireconEventListener listener);
    
    public void sendAcceptPacket(JireconRecorderInfo info);
}
