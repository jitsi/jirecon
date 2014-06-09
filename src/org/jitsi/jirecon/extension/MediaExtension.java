/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.extension;

import java.util.*;
import java.util.Map.*;

import org.jivesoftware.smack.packet.PacketExtension;

public class MediaExtension
    implements PacketExtension
{
    public static final String ELEMENT_NAME = "media";

    public static final String NAMESPACE = "http://estos.de/ns/mjs";

    private Map<String, String> ssrcs = new HashMap<String, String>();

    private Map<String, String> directions = new HashMap<String, String>();

    public MediaExtension()
    {
    }

    @Override
    public String getElementName()
    {
        return ELEMENT_NAME;
    }

    @Override
    public String getNamespace()
    {
        return NAMESPACE;
    }

    @Override
    public String toXML()
    {
        StringBuilder builder = new StringBuilder();

        builder.append("<").append(getElementName()).append(" xmlns='")
            .append(getNamespace()).append("'>");
        for (Entry<String, String> e : ssrcs.entrySet())
        {
            String type = e.getKey();
            String ssrc = e.getValue();
            String direction = directions.get(type);
            builder.append("<source type='").append(type).append("' ssrc='")
                .append(ssrc).append("' direction='").append(direction)
                .append("' />");
        }
        builder.append("</").append(getElementName()).append(">");

        return builder.toString();
    }

    public void setSsrc(String type, String ssrc)
    {
        ssrcs.put(type, ssrc);
    }

    public String getSsrc(String type)
    {
        return ssrcs.get(type);
    }

    public void setDirection(String type, String direction)
    {
        directions.put(type, direction);
    }

    public String getDirection(String type)
    {
        return directions.get(type);
    }
}