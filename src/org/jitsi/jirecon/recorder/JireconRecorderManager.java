/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

public interface JireconRecorderManager
{
    public void startRecording(String conferenceId);

    public void stopRecording(String conferenceId);

    public void init();

    public void uninit();
}
