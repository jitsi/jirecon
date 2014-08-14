/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.test;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import org.jitsi.jirecon.task.*;
import org.jitsi.service.neomedia.*;
import junit.framework.TestCase;

public class TestIceUdpTransportManager
    extends TestCase
{
    public void testEndpoint()
    {
        IceUdpTransportManager mgr = new IceUdpTransportManager();
        
        for (MediaType mediaType : MediaType.values())
        {
            try
            {
                mgr.harvestLocalCandidates(mediaType);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        
        IceUdpTransportPacketExtension pe = null;
        pe = mgr.createTransportPacketExt(MediaType.AUDIO);
        assertNotNull(pe);
        System.out.println(pe.toXML());
    }
}
