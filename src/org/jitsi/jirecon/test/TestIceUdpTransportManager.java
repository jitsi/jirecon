package org.jitsi.jirecon.test;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.task.IceUdpTransportManager;
import org.jitsi.service.neomedia.MediaType;

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
            catch (OperationFailedException e)
            {
                e.printStackTrace();
            }
        }
        
        IceUdpTransportPacketExtension pe = null;
        pe = mgr.getTransportPacketExt(MediaType.AUDIO);
        assertNotNull(pe);
        System.out.println(pe.toXML());
    }
}
