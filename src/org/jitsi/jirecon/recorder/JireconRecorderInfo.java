/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

import java.util.*;
import org.jitsi.service.neomedia.MediaType;

public class JireconRecorderInfo
{
    private JireconRecorderState state = JireconRecorderState.INIT;

    private Map<MediaType, Long> localSsrcs = new HashMap<MediaType, Long>();

    private String msLabel = UUID.randomUUID().toString();
    
    public void setState(JireconRecorderState state)
    {
        this.state = state;
    }

    public JireconRecorderState getState()
    {
        return state;
    }

    public void addLocalSsrc(MediaType mediaType, Long ssrc)
    {
        localSsrcs.put(mediaType, ssrc);
    }

    public Long getLocalSsrc(MediaType mediaType)
    {
        return localSsrcs.get(mediaType);
    }
    
    public String getMsLabel()
    {
        return msLabel;
    }
    
    public String getLabel(MediaType mediaType)
    {
        return mediaType.toString();
    }
    
    public String getMsid(MediaType mediaType)
    {
        return msLabel + " " + getLabel(mediaType);
    }
    
    public enum JireconRecorderState
    {
        INIT,
        STREAM_READY,
        RECEIVING_STREAM,
        STOP_RECEIVING_STREAM,
        RECORDER_READY,
        RECORDING_STREAM,
        STOP_RECORDING_STREAM,
    }
}
