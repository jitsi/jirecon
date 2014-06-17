/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

public class JireconEvent
{
    private JireconEventId evtId;

    private Object source;

    public JireconEvent(Object source, JireconEventId evtId)
    {
        this.source = source;
        this.evtId = evtId;
    }

    public JireconEventId getEventId()
    {
        return evtId;
    }

    public Object getSource()
    {
        return source;
    }
    
    public enum JireconEventId
    {
        TASK_ABORTED,
    }
}
