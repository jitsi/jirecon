/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task;


public class JireconTaskInfo
{
    private String mucJid;

    public void setMucJid(String mucJid)
    {
        this.mucJid = mucJid;
    }

    public String getMucJid()
    {
        return mucJid;
    }
}
