package org.jitsi.jirecon.utils;

/**
 * Send message to JireconMessageReceiver.
 * 
 * @author lishunyang
 * 
 */
public interface JireconMessageSender
{
    /**
     * Add receiver to this sender.
     * 
     * @param receiver Who will receive messages.
     */
    public void addReceiver(JireconMessageReceiver receiver);

    /**
     * Remove receiver of this sender.
     * @param receiver Who will not receive messages.
     */
    public void removeReceiver(JireconMessageReceiver receiver);

    /**
     * Send message.
     * @param msg The string message.
     */
    public void sendMsg(String msg);
}
