/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.util.EventListener;

public interface JireconEventListener extends EventListener
{
    void handleEvent(JireconEvent evt);
}
