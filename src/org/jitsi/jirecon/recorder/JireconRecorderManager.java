/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

import org.jitsi.jirecon.utils.JireconConfiguration;

public interface JireconRecorderManager
{
    public void startRecording(String conferenceJid);

    public void stopRecording(String conferenceJid);

    public void init(JireconConfiguration configuration);

    public void uninit();
}
