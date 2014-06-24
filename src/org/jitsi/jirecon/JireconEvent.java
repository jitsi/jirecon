/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

/**
 * Running event of <tt>Jirecon</tt>, which means some important things
 * happened.
 * 
 * @author lishunyang
 * 
 */
public class JireconEvent
{
    /**
     * Event type.
     */
    private Type type;

    /**
     * Source of the event.
     */
    private Object source;

    /**
     * Construction method.
     * 
     * @param source indicates where this event comes from.
     * @param type indicates the event type.
     */
    public JireconEvent(Object source, Type type)
    {
        this.source = source;
        this.type = type;
    }

    /**
     * Get event type.
     * 
     * @return event type
     */
    public Type getType()
    {
        return type;
    }

    /**
     * Get event source.
     * 
     * @return event source
     */
    public Object getSource()
    {
        return source;
    }

    /**
     * <tt>JireconEvent</tt> type.
     * 
     * @author lishunyang
     * 
     */
    public enum Type
    {
        TASK_ABORTED,
    }
}
