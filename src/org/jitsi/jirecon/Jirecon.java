package org.jitsi.jirecon;

// TODO: This class hasn't been finished. 
/**
 * Jirecon is responsible for recording conferences.
 * 
 * @author lishunyang
 * 
 */
public interface Jirecon
{
    /**
     * Start providing service.
     * @return
     */
    public boolean start();

    /**
     * Stop providing service.
     * @return
     */
    public boolean stop();
}
