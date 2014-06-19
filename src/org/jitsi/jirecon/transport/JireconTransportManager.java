/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.transport;

import java.io.IOException;
import java.net.BindException;
import java.util.Map;

import org.jitsi.service.neomedia.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.service.protocol.OperationFailedException;

public interface JireconTransportManager
{
    public void free();

    public void harvestLocalCandidates()
        throws BindException,
        IllegalArgumentException,
        IOException;

    public void harvestRemoteCandidates(
        Map<MediaType, IceUdpTransportPacketExtension> transportPEs);

    public IceUdpTransportPacketExtension getTransportPacketExt();

    public MediaStreamTarget getStreamTarget(MediaType mediaType);

    public StreamConnector getStreamConnector(MediaType mediaType) throws OperationFailedException;

    public void startConnectivityEstablishment() throws OperationFailedException;
}
