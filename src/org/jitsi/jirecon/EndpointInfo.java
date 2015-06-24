/*
/*
 * Jirecon, the JItsi REcording COntainer.
 *
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
