/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

public class RecorderInfo
{
    private JireconRecorderStatus status;
    
    public void setStatus(JireconRecorderStatus status)
    {
        this.status = status;
    }
    
    public JireconRecorderStatus getStatus()
    {
        return status;
    }
}
