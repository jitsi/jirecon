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

/**
 * Transport manager, is responsible for establish ICE connectivity.
 * 
 * @author lishunyang
 * 
 */
public interface JireconTransportManager
{
    /**
     * Free the resources holded by <tt>JireconTransportManager</tt>.
     */
    public void free();

    // TODO: Simplify the Exception, merge them into one
    // OperationFailedException.
    /**
     * Harvest local candidates.
     * 
     * @throws BindException
     * @throws IllegalArgumentException
     * @throws IOException
     */
    public void harvestLocalCandidates()
        throws BindException,
        IllegalArgumentException,
        IOException;

    /**
     * Parse and harvest remote candidates from a
     * <tt>IceUdpTransportPacketExtension</tt>.
     * 
     * @param transportPEs The <tt>IceUdpTransportPacketExtension</tt> to be
     *            parsed.
     */
    public void harvestRemoteCandidates(
        Map<MediaType, IceUdpTransportPacketExtension> transportPEs);

    /**
     * Get a <tt>IceUdpTransportPacketExtension</tt> created by
     * <tt>JireconTransportManager</tt>.
     * 
     * @return <tt>IceUdpTransportPacketExtension</tt>
     */
    public IceUdpTransportPacketExtension getTransportPacketExt();

    /**
     * Get <tt>MediaStreamTarget</tt> of specified <tt>MediaType</tt> created by
     * <tt>JireconTransportManager</tt>.
     * 
     * @param mediaType The specified <tt>MediaType</tt>
     * @return <tt>MediaStreamTarget</tt>
     */
    public MediaStreamTarget getStreamTarget(MediaType mediaType);

    /**
     * Get <tt>StreamConnector</tt> of specified <tt>MediaType</tt> created by
     * <tt>JireconTransportManager</tt>.
     * 
     * @param mediaType The specified <tt>MediaType</tt>
     * @return <tt>StreamConnector</tt>
     * @throws OperationFailedException if some operation failed.
     */
    public StreamConnector getStreamConnector(MediaType mediaType)
        throws OperationFailedException;

    /**
     * Start establish ICE connectivity.
     * 
     * @throws OperationFailedException
     */
    public void startConnectivityEstablishment()
        throws OperationFailedException;
}
