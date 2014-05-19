package jirecon.session;

public interface JingleSessionManager
{
    public boolean init();
    
    public boolean uninit();
    
    public void openAJingleSession(String conferenceId);
    
    public void closeAJingleSession(String conferenceId);
    
}
