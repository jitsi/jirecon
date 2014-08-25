/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.datachannel;

public interface WebRtcDataStreamListener
{
    /**
     * Fired when new WebRTC data channel is opened.
     *
     * @param channel the <tt>WebRtcDataStream</tt> that represents opened
     * WebRTC data channel.
     */
    public void onChannelOpened(
            WebRtcDataStream channel);
}
