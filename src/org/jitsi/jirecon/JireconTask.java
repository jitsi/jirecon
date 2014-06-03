/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import org.jitsi.jirecon.session.JireconSessionInfo;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.service.neomedia.DtlsControl;
import org.jitsi.service.neomedia.MediaService;
import org.jivesoftware.smack.XMPPConnection;

/**
 * Jirecon is responsible for recording conferences.
 * 
 * @author lishunyang
 * 
 */
public interface JireconTask
{
    public void init(JireconConfiguration configuration, String conferenceJid,
        XMPPConnection connection, MediaService service);

    public void uninit();

    public void start();

    public void stop();

    public JireconTaskInfo getTaskInfo();

    public void addEventListener(JireconEventListener listener);

    public void removeEventListener(JireconEventListener listener);
}
