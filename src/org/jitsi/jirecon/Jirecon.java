/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
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
     * Start recording a conference
     * 
     * @param conferenceId The conference to be recorded.
     */
    public void startRecording(String conferenceId);

    /**
     * Stop recording a conference
     * 
     * @param conferenceId The conference to be stopped.
     */
    public void stopRecording(String conferenceId);
}
