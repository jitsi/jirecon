/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.util.EventListener;

/**
 * Listener interface of <tt>JireconEvent</tt>.
 * 
 * @author lishunyang
 * @see JireconEvent
 */
public interface JireconEventListener
    extends EventListener
{
    /**
     * Handle the specified <tt>JireconEvent</tt>.
     * 
     * @param evt is the specified event.
     */
    void handleEvent(JireconEvent evt);
}
