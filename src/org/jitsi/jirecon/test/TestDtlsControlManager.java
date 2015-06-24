/*
/*
 * Jirecon, the JItsi REcording COntainer.
 *
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
