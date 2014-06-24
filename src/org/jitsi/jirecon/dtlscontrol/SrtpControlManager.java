/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package org.jitsi.jirecon.dtlscontrol;

import java.util.Map;

import org.jitsi.service.neomedia.*;

/**
 * A manager which is responsible for holding <tt>SrtpControl</tt> for different
 * <tt>MediaType</tt>.
 * 
 * @author lishunyang
 * 
 */
public interface SrtpControlManager
{
    /**
     * Add a specified <tt>MediaType</tt> finger print of a remote peer. If
     * there is a finger print with same <tt>MediaType</tt>, the old finger
     * print will be replaced.
     * 
     * @param mediaType The <tt>MediaType</tt> of the finger print.
     * @param fingerprint The finger print.
     */
    public void addRemoteFingerprint(MediaType mediaType, String fingerprint);

    /**
     * Get the specified <tt>MediaType</tt> local finger print.
     * 
     * @param mediaType The <tt>MediaType</tt> of the finger print.
     * @return The local finger print.
     */
    public String getLocalFingerprint(MediaType mediaType);

    /**
     * Get the hash function name of specified <tt>MediaType</tt> local finger
     * print.
     * 
     * @param mediaType The <tt>MediaType</tt> of the finger print.
     * @return The hash function name.
     */
    public String getLocalFingerprintHashFunction(MediaType mediaType);

    /**
     * Get the <tt>SrtpControl</tt> of specified <tt>MediaType</tt>.
     * 
     * @param mediaType The <tt>MediaType</tt> of the <tt>SrtpControl</tt>.
     * @return The <tt>SrtpControl</tt> instance.
     */
    public SrtpControl getSrtpControl(MediaType mediaType);

    /**
     * Get all <tt>SrtpControl</tt> of this manager.
     * 
     * @return The map between <tt>MediaType</tt> and <tt>SrtpControl</tt>.
     */
    public Map<MediaType, SrtpControl> getAllSrtpControl();
}
