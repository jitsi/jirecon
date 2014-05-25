package org.jitsi.jirecon;

import java.util.EventListener;

public interface JireconEventListener extends EventListener
{
    void handleEvent(JireconEvent evt);
}
