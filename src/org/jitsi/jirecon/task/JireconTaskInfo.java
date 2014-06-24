/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task;

/**
 * Gather the information of a <tt>JireconTask</tt>, it can be accessed by
 * others.
 * 
 * @author lishunyang
 * 
 */
public class JireconTaskInfo
{
    /**
     * The MUC jid, which is used to join a MUC. There is no mandatory to use
     * whole jid as long as it can be recognized by <tt>XMPPConnector</tt> and
     * join a MUC successfully.
     */
    private String mucJid;

    /**
     * Set Jitsi-meeting jid.
     * 
     * @param mucJid
     */
    public void setMucJid(String mucJid)
    {
        this.mucJid = mucJid;
    }

    /**
     * Get Jitsi-meeting jid.
     * 
     * @return
     */
    public String getMucJid()
    {
        return mucJid;
    }
}
