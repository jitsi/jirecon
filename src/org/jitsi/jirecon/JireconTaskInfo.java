/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

public class JireconTaskInfo
{
    private String conferenceJid;

    private JireconTaskState state;

    public void setConferenceJid(String conferenceJid)
    {
        this.conferenceJid = conferenceJid;
    }

    public String getConferenceJid()
    {
        return conferenceJid;
    }

    public void setState(JireconTaskState state)
    {
        this.state = state;
    }

    public JireconTaskState getState()
    {
        return state;
    }
}
