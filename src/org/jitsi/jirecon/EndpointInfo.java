/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.util.*;
import org.jitsi.service.neomedia.*;

/**
 * Data structure that encapsulates endpoint.
 * <p>
 * An endpoint represents a participant in the meeting. It contains id and
 * ssrcs.
 * 
 * @author lishunyang
 * 
 */
public class EndpointInfo
{
    /**
     * Map between <tt>MediaType</tt> and ssrc. Notice that only audio or video
     * has ssrc.
     */
    private Map<MediaType, Long> ssrcs = new HashMap<MediaType, Long>();

    /**
     * Endpoint id.
     */
    private String id;

    public void setId(String id)
    {
        this.id = id;
    }

    public void setSsrc(MediaType mediaType, long ssrc)
    {
        ssrcs.put(mediaType, ssrc);
    }

    public String getId()
    {
        return id;
    }

    public String getBareId()
    {
        return id.split("@")[0];
    }

    public Map<MediaType, Long> getSsrcs()
    {
        return ssrcs;
    }

    public long getSsrc(MediaType mediaType)
    {
        return ssrcs.get(mediaType);
    }
}
