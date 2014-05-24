/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
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
