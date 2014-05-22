package org.jitsi.jirecon.recorder;

import org.jitsi.jirecon.session.SessionInfo;

public interface JireconRecorder
{
    public void start(SessionInfo info);

    public void stop();
}
