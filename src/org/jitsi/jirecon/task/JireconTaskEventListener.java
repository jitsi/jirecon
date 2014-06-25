/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task;

/**
 * <tt>JireconTaskEvent</tt> listener.
 * 
 * @author lishunyang
 * @see JireconTaskEvent
 * 
 */
public interface JireconTaskEventListener
{
    /**
     * Handle the event.
     * 
     * @param event
     */
    public void handleTaskEvent(JireconTaskEvent event);
}
