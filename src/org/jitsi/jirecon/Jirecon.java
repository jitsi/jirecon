/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.io.IOException;

import org.jivesoftware.smack.XMPPException;

public interface Jirecon
{
    public void init(String configurationPath)
        throws IOException,
        XMPPException;

    public void uninit();

    public void startJireconTask(String conferenceJid) throws XMPPException;

    public void stopJireconTask(String conferenceJid);
    
    public void addEventListener(JireconEventListener listener);

    public void removeEventListener(JireconEventListener listener);
}
