/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task;

/**
 * <tt>TaskEvent</tt> listener.
 * 
 * @author lishunyang
 * @see TaskEvent
 * 
 */
public interface TaskEventListener
{
    /**
     * Handle the event.
     * 
     * @param event
     */
    public void handleTaskEvent(TaskEvent event);
}
