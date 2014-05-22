package org.jitsi.jirecon.recorder;

public interface JireconRecorderManager
{
    public void startRecording(String conferenceId);

    public void stopRecording(String conferenceId);

    public void init();

    public void uninit();
}
