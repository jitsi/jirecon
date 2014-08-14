/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task;

import java.util.*;
import org.jitsi.service.neomedia.*;

public class Endpoint
{
    private Map<MediaType, Long> ssrcs = new HashMap<MediaType, Long>();

    private String id;

    public Endpoint()
    {
    }

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
