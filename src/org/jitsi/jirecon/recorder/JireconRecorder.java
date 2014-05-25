/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

import org.jitsi.jirecon.session.JireconSessionInfo;

public interface JireconRecorder
{
    public void start(JireconSessionInfo info);

    public void stop();
}
