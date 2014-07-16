/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task;

import org.jitsi.jirecon.JireconEventListener;
import org.jivesoftware.smack.XMPPConnection;

/**
 * The individual task to record specified Jitsi-meeting.
 * 
 * @author lishunyang
 * 
 */
public interface JireconTask
{
    /**
     * Initialize a <tt>JireconTask</tt>. Specify which Jitsi-meet you want to
     * record and where we should output the media files.
     * 
     * @param mucJid indicates which meet you want to record.
     * @param connection is an existed <tt>XMPPConnection</tt> which will be
     *            used to send/receive Jingle packet.
     * @param savingDir indicates where we should output the media fiels.
     */
    public void init(String mucJid, XMPPConnection connection, String savingDir);

    /**
     * Uninitialize the <tt>JireconTask</tt> and get ready to be recycled by GC.
     * 
     * @param keepData Whether we should keep data. Keep the data if it is true,
     *            other wise remove them.
     */
    public void uninit(boolean keepData);

    /**
     * Start the <tt>JireconTask</tt>.
     * <p>
     * <strong>Warning:</strong> This is a asynchronous method, so it will
     * return quickly, but it doesn't mean that the task has been successfully
     * started. It will notify event listeners if the task is failed.
     */
    public void start();

    /**
     * Stop the <tt>JireconTask</tt>.
     */
    public void stop();
    
    /**
     * Get the task information.
     * 
     * @return The task information.
     */
    public JireconTaskInfo getTaskInfo();

    /**
     * Register an event listener to this <tt>JireconTask</tt>.
     * 
     * @param listener
     */
    public void addEventListener(JireconEventListener listener);

    /**
     * Remove and event listener from this <tt>JireconTask</tt>.
     * 
     * @param listener
     */
    public void removeEventListener(JireconEventListener listener);
}
