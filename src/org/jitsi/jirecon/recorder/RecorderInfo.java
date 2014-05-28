/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

public class RecorderInfo
{
    private JireconRecorderState state;
    
    public void setState(JireconRecorderState state)
    {
        this.state = state;
    }
    
    public JireconRecorderState getState()
    {
        return state;
    }
}
