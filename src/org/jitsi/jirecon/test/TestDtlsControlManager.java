/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.test;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;

import org.jitsi.jirecon.DtlsControlManager;
import org.jitsi.service.neomedia.*;

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
        fp = mgr.createFingerprintPacketExt(MediaType.AUDIO);
        
        assertNotNull(fp);
        
        System.out.println(fp.toXML());
        
        mgr.setRemoteFingerprint(MediaType.AUDIO, fp);
        
        assertNotNull(mgr.getDtlsControl(MediaType.VIDEO));
        
        assertTrue(MediaType.values().length == mgr.getAllDtlsControl().size());
    }
}
