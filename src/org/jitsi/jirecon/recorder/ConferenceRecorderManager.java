package org.jitsi.jirecon.recorder;

public interface ConferenceRecorderManager
{
    public void startRecording(String conferenceId);

    public void stopRecording(String conferenceId);

    public void init();

    public void uninit();
}
