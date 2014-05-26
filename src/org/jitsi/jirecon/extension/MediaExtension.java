/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.extension;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.jivesoftware.smack.packet.PacketExtension;

public class MediaExtension
    implements PacketExtension
{
    public static final String ELEMENT_NAME = "media";

    public static final String NAMESPACE = "http://estos.de/ns/mjs";

    //public ArrayList<Source> list;
    
    private Map<String, String> ssrcs = new HashMap<String, String>();
    private Map<String, String> directions = new HashMap<String, String>();

    public MediaExtension()
    {
        //list = new ArrayList<Source>();
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

//    public void addSource(String type, String ssrc, String direction)
//    {
//        list.add(new Source(type, ssrc, direction));
//    }
}

//class Source
//{
//    public String type;
//
//    public String ssrc;
//
//    public String direction;
//
//    public Source()
//    {
//    }
//
//    public Source(String type, String ssrc, String direction)
//    {
//        this.type = type;
//        this.ssrc = ssrc;
//        this.direction = direction;
//    }
//}