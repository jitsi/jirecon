/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.io.IOException;

import org.jivesoftware.smack.XMPPException;

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
     * 
     * @param configurationPath The path of configuration file.
     * @throws IOException Failed to load configuration.
     * @throws XMPPException Failed to create XMPP conenction.
     * @return
     */
    public void initiate(String configurationPath)
        throws IOException,
        XMPPException;

    /**
     * Stop providing service.
     * 
     * @return
     */
    public void uninitiate();

    /**
     * Start recording a conference. This method is asynchronous method which
     * means it will return immediately and left the rest jobs working behind.
     * So when the method returns, it doesn't mean that the recording procedure
     * is running successfully. You need to register an EventListener to get
     * notification.
     * 
     * @param conferenceJid The conference to be recorded.
     * @throws XMPPException Failed to start recording the conference.
     */
    public void startRecording(String conferenceJid) throws XMPPException;

    /**
     * Stop recording a conference
     * 
     * @param conferenceJid The conference to be stopped.
     */
    public void stopRecording(String conferenceJid);
}
