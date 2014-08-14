/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.protocol.extension;

import java.util.*;
import java.util.Map.*;
import org.jivesoftware.smack.packet.*;

/**
 * Media extension in presence packet.
 * 
 * @author lishunyang
 */
public class MediaExtension
    implements PacketExtension
{
    /**
     * The name of the "media" element.
     */
    public static final String ELEMENT_NAME = "media";

    /**
     * The namespace for the "media" element.
     */
    public static final String NAMESPACE = "http://estos.de/ns/mjs";

    /**
     * The map between attribute "type" and "ssrc".
     */
    private Map<String, String> ssrcs = new HashMap<String, String>();

    /**
     * The map between attribute "type" and "direction".
     */
    private Map<String, String> directions = new HashMap<String, String>();

    /**
     * {@inheritDoc}
     */
    @Override
    public String getElementName()
    {
        return ELEMENT_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getNamespace()
    {
        return NAMESPACE;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * Set attribute "ssrc" of specified "type".
     * 
     * @param type The specified "type"
     * @param ssrc The "ssrc" to be added.
     */
    public void setSsrc(String type, String ssrc)
    {
        ssrcs.put(type, ssrc);
    }

    /**
     * Get attribute "ssrc" of specified "type".
     * 
     * @param type The specified "type".
     * @return Attribute "ssrc".
     */
    public String getSsrc(String type)
    {
        return ssrcs.get(type);
    }

    /**
     * Set attribute "direction" of specified "type".
     * 
     * @param type The specified "type".
     * @param direction The "direction" to be added.
     */
    public void setDirection(String type, String direction)
    {
        directions.put(type, direction);
    }

    /**
     * Get attribute "direction" of specified "type".
     * 
     * @param type The specified "type".
     * @return Attribute "direction".
     */
    public String getDirection(String type)
    {
        return directions.get(type);
    }
}