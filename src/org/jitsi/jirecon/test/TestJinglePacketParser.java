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
import org.jitsi.jirecon.JingleSessionManager;
import org.jitsi.jirecon.protocol.extension.*;
import org.jitsi.jirecon.utils.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.provider.*;

import junit.framework.TestCase;

public class TestJinglePacketParser
    extends TestCase
{
    private static final String XMPP_HOST = "jitmeet.example.com";

    private static final String JID = "8khk07b3a61j1yvi@conference.example.com";

    private static final String NICK = "TestJinglePacketParser";

    private static final int PORT = 5222;

    public void testParser() throws Exception
    {
        LibJitsi.start();
        ProviderManager providerManager = ProviderManager.getInstance();

        providerManager.addIQProvider(JingleIQ.ELEMENT_NAME,
            JingleIQ.NAMESPACE, new JingleIQProvider());
        providerManager.addExtensionProvider(MediaExtension.ELEMENT_NAME,
            MediaExtension.NAMESPACE, new MediaExtensionProvider());
        providerManager.addExtensionProvider(SctpMapExtension.ELEMENT_NAME,
            SctpMapExtension.NAMESPACE, new SctpMapExtensionProvider());
        
        ConnectionConfiguration conf =
            new ConnectionConfiguration(XMPP_HOST, PORT);
        XMPPConnection conn = new XMPPConnection(conf);
        conn.connect();
        conn.loginAnonymously();
        JingleSessionManager mgr = new JingleSessionManager();
        mgr.init(conn);
        
        mgr.connect(JID, NICK);
        JingleIQ jiq = mgr.waitForInitPacket();
        mgr.disconnect(Reason.SUCCESS, "Bye");
        conn.disconnect();
        
        System.out.println(jiq.toXML());
       
        assertNotNull(JinglePacketParser.getContentPacketExt(jiq, MediaType.AUDIO));
        assertNull(JinglePacketParser.getContentPacketExt(null, MediaType.AUDIO));
        assertNull(JinglePacketParser.getContentPacketExt(jiq, null));
        assertNull(JinglePacketParser.getContentPacketExt(null, null));
        
        assertNotNull(JinglePacketParser.getFingerprintPacketExt(jiq, MediaType.AUDIO));
        assertNull(JinglePacketParser.getFingerprintPacketExt(jiq, null));
        assertNull(JinglePacketParser.getFingerprintPacketExt(null, MediaType.AUDIO));
        assertNull(JinglePacketParser.getFingerprintPacketExt(null, null));
        
        assertNotNull(JinglePacketParser.getFormatAndDynamicPTs(jiq, MediaType.VIDEO));
        assertNull(JinglePacketParser.getFormatAndDynamicPTs(jiq, null));
        assertNull(JinglePacketParser.getFormatAndDynamicPTs(null, MediaType.VIDEO));
        assertNull(JinglePacketParser.getFormatAndDynamicPTs(null, null));
        
        assertNotNull(JinglePacketParser.getSupportedMediaTypes(jiq));
        assertNull(JinglePacketParser.getSupportedMediaTypes(null));
        
        assertNotNull(JinglePacketParser.getTransportPacketExt(jiq, MediaType.VIDEO));
        assertNull(JinglePacketParser.getTransportPacketExt(jiq, null));
        assertNull(JinglePacketParser.getTransportPacketExt(null, MediaType.VIDEO));
        assertNull(JinglePacketParser.getTransportPacketExt(null, null));
        
        LibJitsi.stop();
    }
}
