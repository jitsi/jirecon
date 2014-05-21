package org.jitsi.jirecon.utils;

public interface JireconMessageSender
{
    public void addReceiver(JireconMessageReceiver receiver);
    
    public void removeReceiver(JireconMessageReceiver receiver);
    
    public void sendMsg(String msg);
}
