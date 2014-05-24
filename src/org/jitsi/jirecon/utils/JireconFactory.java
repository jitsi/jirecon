/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.utils;

import org.jitsi.jirecon.recorder.JireconRecorder;
import org.jitsi.jirecon.session.JireconSession;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaStream;
import org.jivesoftware.smack.XMPPConnection;

/**
 * This factory is used to create JireconSession and JireconRecorder.
 * 
 * @author lishunyang
 * 
 */
public interface JireconFactory
{
    /**
     * Create JireconSession
     * 
     * @param connection The XMPP connection which is shared with all
     *            JireconSession.
     * @return JireconSession instance.
     */
    public JireconSession createSession(XMPPConnection connection);

    public JireconRecorder createRecorder(MediaService service);
}
