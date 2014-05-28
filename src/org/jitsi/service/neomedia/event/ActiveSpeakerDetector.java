/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.service.neomedia.event;

import java.util.*;

/**
 * Implementers of this interface get notified about current audio levels of
 * multiple audio streams (identified by their SSRCs) via calls to
 * {@link #levelChanged(long, int)} and try to keep track of which stream is
 * 'active'. When the 'active' stream changes, listeners registered via
 * {@link #addActiveSpeakerChangedListener(ActiveSpeakerChangedListener)} are
 * notified.
 *
 *
 * @author Boris Grozev
 */
public abstract class ActiveSpeakerDetector
{
    /**
     * The list of listeners to be notified when the active stream changes.
     */
    private final List<ActiveSpeakerChangedListener> listeners
            = new ArrayList<ActiveSpeakerChangedListener>(1);

    public abstract void levelChanged(long ssrc, int level);

    /**
     * Adds a listener to be notified when the active stream changes.
     * @param listener the listener to add.
     */
    public void addActiveSpeakerChangedListener(
            ActiveSpeakerChangedListener listener)
    {
        synchronized (listeners)
        {
            listeners.add(listener);
        }
    }

    /**
     * Notifies registered listeners that the new 'active' stream has SSRC
     * <tt>ssrc</tt>.
     * @param ssrc the SSRC of the new 'active' stream.
     */
    protected void activeChanged(long ssrc)
    {
        for (ActiveSpeakerChangedListener listener : listeners)
        {
            listener.activeSpeakerChanged(ssrc);
        }
    }
}
