package org.jitsi.jirecon.test;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.DtlsFingerprintPacketExtension;

import org.jitsi.jirecon.task.DtlsControlManager;
import org.jitsi.service.neomedia.MediaType;

import junit.framework.TestCase;

public class TestDtlsControlManager
    extends TestCase
{
    public void testDtlsControlManager()
    {
        DtlsControlManager mgr = new DtlsControlManager();
        
        assertNotNull(mgr.getLocalFingerprint(MediaType.AUDIO));
        
        assertEquals("sha-1", mgr.getLocalFingerprintHashFunction(MediaType.AUDIO));
        
        DtlsFingerprintPacketExtension fp = null;
        fp = mgr.getFingerprintPacketExt(MediaType.AUDIO);
        
        assertNotNull(fp);
        
        System.out.println(fp.toXML());
        
        mgr.addRemoteFingerprint(MediaType.AUDIO, fp);
        
        assertNotNull(mgr.getDtlsControl(MediaType.VIDEO));
        
        assertTrue(MediaType.values().length == mgr.getAllDtlsControl().size());
    }
}
