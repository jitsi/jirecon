/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.extension;

import org.jivesoftware.smack.packet.PacketExtension;

public class SctpMapExtension
    implements PacketExtension
{
    /**
     * The name of the "sctpmap" element.
     */
    public static final String ELEMENT_NAME = "sctpmap";

    /**
     * The namespace for the "sctpmap" element.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:transports:dtls-sctp:1";
    
    public static final String PORT_ATTR_NAME = "number";
    
    public static final String PROTOCOL_ATTR_NAME = "protocol";
    
    public static final String STREAMS_ATTR_NAME = "streams";
    
    private int port = -1;
    
    private String protocol = "";
    
    private int streams = -1;
    
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

        builder.append("<").append(getElementName());
        builder.append(" ").append("xmlns").append("='").append(getNamespace()).append("'");
        builder.append(" ").append(PORT_ATTR_NAME).append("='").append(port).append("'");
        builder.append(" ").append(PROTOCOL_ATTR_NAME).append("='").append(protocol).append("'");
        builder.append(" ").append(STREAMS_ATTR_NAME).append("='").append(streams).append("'");
        builder.append("/>");
        
        return builder.toString();
    }

    public void setPort(int port)
    {
        this.port = port;
    }
    
    public int getPort()
    {
        return port;
    }
    
    public void setProtocol(String protocol)
    {
        this.protocol = protocol;
    }
    
    public String getProtocol()
    {
        return protocol;
    }
    
    public void setStreams(int streams)
    {
        this.streams = streams;
    }
    
    public int getStreams()
    {
        return streams;
    }
}
