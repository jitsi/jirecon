package org.jitsi.jirecon.utils;

/**
 * Receive message from JireconMessageSender.
 * 
 * @author lishunyang
 * 
 */
public interface JireconMessageReceiver
{
    /**
     * Receiver message.
     * 
     * @param sender Who send this message.
     * @param msg The string message.
     */
    public void receiveMsg(JireconMessageSender sender, String msg);
}
