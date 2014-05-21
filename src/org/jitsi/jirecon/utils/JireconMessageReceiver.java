package org.jitsi.jirecon.utils;

public interface JireconMessageReceiver
{
    public void receiveMsg(JireconMessageSender sender, String msg);
}
