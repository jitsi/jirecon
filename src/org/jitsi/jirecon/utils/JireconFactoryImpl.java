/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.utils;

import org.jitsi.jirecon.recorder.JireconRecorder;
import org.jitsi.jirecon.recorder.JireconRecorderImpl;
import org.jitsi.jirecon.session.JireconSession;
import org.jitsi.jirecon.session.JireconSessionImpl;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaStream;
import org.jivesoftware.smack.XMPPConnection;

/**
 * This class is used to create JireconSession and JireconRecorder instance.
 * 
 * @author lishunyang
 * 
 */
public class JireconFactoryImpl
    implements JireconFactory
{

    /**
     * Create JireconSession instance.
     */
    @Override
    public JireconSession createSession(XMPPConnection connection)
    {
        return new JireconSessionImpl(connection);
    }

    @Override
    public JireconRecorder createRecorder(MediaService service)
    {
        return new JireconRecorderImpl(service);
    }

}
