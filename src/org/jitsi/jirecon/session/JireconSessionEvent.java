package org.jitsi.jirecon.session;

import org.jitsi.jirecon.JireconEvent;

public class JireconSessionEvent extends JireconEvent
{
    private JireconSessionInfo info;
    
    public JireconSessionEvent(Object source, JireconSessionInfo info)
    {
        super(source);
    }

    public JireconSessionInfo getJireconSessionInfo()
    {
        return info;
    }
}
