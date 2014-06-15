/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task;

import org.jitsi.jirecon.JireconEventListener;
import org.jivesoftware.smack.XMPPConnection;

/**
 * Jirecon is responsible for recording conferences.
 * 
 * @author lishunyang
 * 
 */
public interface JireconTask
{
    public void init(String conferenceJid, XMPPConnection connection,
        String savingDir);

    public void uninit();

    public void start();

    public void stop();

    public JireconTaskInfo getTaskInfo();

    public void addEventListener(JireconEventListener listener);

    public void removeEventListener(JireconEventListener listener);
}
