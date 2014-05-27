/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.transport;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.BindException;

import org.jitsi.jirecon.JireconEventListener;
import org.jitsi.jirecon.utils.JireconConfiguration;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;

public interface JireconTransportManager
{
    public void init(JireconConfiguration configuration);

    public void uninit();

    public void harvestLocalCandidates()
        throws BindException,
        IllegalArgumentException,
        IOException;

    public void harvestRemoteCandidates(JingleIQ jiq);

    public IceUdpTransportPacketExtension getTransportPacketExt();

    public void startConnectivityEstablishment();

    public void addStateChangeListener(PropertyChangeListener listener);
}
