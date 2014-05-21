package org.jitsi.jirecon.extension;

import java.util.ArrayList;

import org.jivesoftware.smack.packet.PacketExtension;

public class MediaExtension
    implements PacketExtension
{
    public static final String ELEMENT_NAME = "media";

    public static final String NAMESPACE = "http://estos.de/ns/mjs";

    public ArrayList<Source> list;

    public MediaExtension()
    {
        list = new ArrayList<Source>();
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
        for (Source s : list)
        {
            builder.append("<source type='").append(s.type).append("' ssrc='")
                .append(s.ssrc).append("' direction='").append(s.direction)
                .append("' />");
        }
        builder.append("</").append(getElementName()).append(">");

        return builder.toString();
    }

    public void addSource(String type, String ssrc, String direction)
    {
        list.add(new Source(type, ssrc, direction));
    }
}

class Source
{
    public String type;

    public String ssrc;

    public String direction;

    public Source()
    {
    }

    public Source(String type, String ssrc, String direction)
    {
        this.type = type;
        this.ssrc = ssrc;
        this.direction = direction;
    }
}