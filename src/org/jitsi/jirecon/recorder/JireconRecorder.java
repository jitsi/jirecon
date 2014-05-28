/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

import org.jitsi.jirecon.JireconEventListener;
import org.jitsi.jirecon.session.JireconSessionInfo;
import org.jitsi.jirecon.transport.JireconTransportManager;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.service.neomedia.MediaService;

public interface JireconRecorder
{
    public void init(JireconConfiguration configuration, MediaService service,
        JireconTransportManager transportManager);

    public void uninit();

    public void start(JireconSessionInfo info);

    public void stop();

    public JireconRecorderState getState();

    public void addEventListener(JireconEventListener listener);

    public void removeEventListener(JireconEventListener listener);
}
