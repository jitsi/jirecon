/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

public class JireconEvent
{
    private State state;
    private Object source;
    
    public JireconEvent(Object source, State state)
    {
        this.source = source;
        this.state = state;
    }
    
    public State getState() 
    {
        return state;
    }
    
    public Object getSource()
    {
        return source;
    }

    public enum State
    {
        ABORTED,
        SESSION_BUILDING,
        SESSION_CONSTRUCTED,
        RECORDER_BUILDING,
        RECORDER_RECEIVING
    }
}
