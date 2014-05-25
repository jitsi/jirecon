/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.utils;

// TODO: Rewrite those import statements to package import statement.
import java.util.List;

import org.jitsi.service.neomedia.MediaType;
import org.jivesoftware.smack.packet.IQ;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidatePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.PayloadTypePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.RtpDescriptionPacketExtension;

/**
 * This class only has static method. It is used for extract information from
 * Jingle related packet.
 * 
 * @author lishunyang
 * 
 */
public class JinglePacketParser
{
    /**
     * Get who sended this Jingle packet.
     * 
     * @param jiq The Jingle packet.
     * @return The sender's user name
     */
    public static String getFrom(JingleIQ jiq)
    {
        return jiq.getFrom();
    }

    /**
     * Get who will receive this Jingle packet.
     * 
     * @param jiq The Jingle packet.
     * @return The receiver's uer name.
     */
    public static String getTo(JingleIQ jiq)
    {
        return jiq.getTo();
    }

    /**
     * Get the type of a Jingle packet.
     * 
     * @param jiq The Jingle packet.
     * @return The type of this Jingle packet.
     */
    public static IQ.Type getType(JingleIQ jiq)
    {
        return jiq.getType();
    }

    /**
     * Get the action of a Jingle packet.
     * 
     * @param jiq The jingle packet.
     * @return The action of this Jingle packet.
     */
    public static JingleAction getAction(JingleIQ jiq)
    {
        return jiq.getAction();
    }

    /**
     * Get the content packet extension of a Jingle packet with speficied type
     * of media.
     * 
     * @param jiq The Jingle packet.
     * @param media The media type.
     * @return Content packet extension.
     */
    public static ContentPacketExtension getContentPacketExt(JingleIQ jiq,
        MediaType media)
    {
        for (ContentPacketExtension c : jiq.getContentList())
        {
            if (media.toString().equalsIgnoreCase(c.getName()))
                return c;
        }

        return null;
    }

    /**
     * Get the description packet extension from a content packet packet
     * extension.
     * 
     * @param content The content packet extension.
     * @return Description packet extension.
     */
    public static RtpDescriptionPacketExtension getDescriptionPacketExt(
        ContentPacketExtension content)
    {
        return content.getFirstChildOfType(RtpDescriptionPacketExtension.class);
    }

    /**
     * Get the description packet extension from a Jingle packet with specified
     * type of media.
     * 
     * @param jiq The Jingle packet.
     * @param media The media type.
     * @return Description packet extension.
     */
    public static RtpDescriptionPacketExtension getDescriptionPacketExt(
        JingleIQ jiq, MediaType media)
    {
        return getDescriptionPacketExt(getContentPacketExt(jiq, media));
    }

    /**
     * Get a list of payloadtype packet extensions from a description packet
     * extension.
     * 
     * @param description The description packet extension.
     * @return List of payloadtype packet extensions.
     */
    public static List<PayloadTypePacketExtension> getPayloadTypePacketExts(
        RtpDescriptionPacketExtension description)
    {
        return description.getPayloadTypes();
    }

    /**
     * Get a list of payloadtype packet extension from a Jingle packet with
     * specified type of media.
     * 
     * @param jiq The Jingle packet.
     * @param media The media type.
     * @return List of payloadtype packet extensions.
     */
    public static List<PayloadTypePacketExtension> getPayloadTypePacketExts(
        JingleIQ jiq, MediaType media)
    {
        return getPayloadTypePacketExts(getDescriptionPacketExt(jiq, media));
    }

    /**
     * Get transport packet extension from a Jingle packet with specified type
     * of media.
     * 
     * @param jiq The Jingle packet.
     * @param media The type of media.
     * @return Transport packet extension.
     */
    public static IceUdpTransportPacketExtension getTransportPacketExt(
        JingleIQ jiq, MediaType media)
    {
        for (ContentPacketExtension c : jiq.getContentList())
        {
            if (media.toString().equalsIgnoreCase(c.getName()))
            {
                return c
                    .getFirstChildOfType(IceUdpTransportPacketExtension.class);
            }
        }

        return null;
    }

    /**
     * Get ufrag from an Jingle packet with specified type of media.
     * 
     * @param jiq The Jingle packet.
     * @param media The type of media.
     * @return Ufrag.
     */
    public static String getTransportUfrag(JingleIQ jiq, MediaType media)
    {
        return getTransportUfrag(getTransportPacketExt(jiq, media));
    }

    /**
     * Get ufrag from an transport packet extension.
     * 
     * @param transport The transport packet extension.
     * @return Ufrag.
     */
    public static String getTransportUfrag(
        IceUdpTransportPacketExtension transport)
    {
        return transport.getUfrag();
    }

    /**
     * Get a list of candidate packet extension from a transport packet
     * extension.
     * 
     * @param transport The transport packet extension.
     * @return List of candidate packet extensions.
     */
    public static List<CandidatePacketExtension> getCandidatePacketExt(
        IceUdpTransportPacketExtension transport)
    {
        return transport.getCandidateList();
    }

    /**
     * Get a list of candidate packet extension from a Jingle packet with
     * specified type of media.
     * 
     * @param jiq The Jingle packet.
     * @param media The type of media.
     * @return List of candidate packet extensions.
     */
    public static List<CandidatePacketExtension> getCandidatePacketExt(
        JingleIQ jiq, MediaType media)
    {
        return getCandidatePacketExt(getTransportPacketExt(jiq, media));
    }

    /**
     * Get password from a transport packet extension.
     * 
     * @param transport The transport packet extension.
     * @return Password.
     */
    public static String getTransportPassword(
        IceUdpTransportPacketExtension transport)
    {
        return transport.getPassword();
    }

    /**
     * Get password from a Jingle packet with specified type of media.
     * 
     * @param jiq The Jingle packet.
     * @param media The type of media.
     * @return Password.
     */
    public static String getTransportPassword(JingleIQ jiq, MediaType media)
    {
        return getTransportPassword(getTransportPacketExt(jiq, media));
    }

    /**
     * Get conference id from a Jingle packet.
     * 
     * @param jiq The Jingle packet.
     * @param isReceived Wether this Jingle packet is received.
     * @return
     */
    public static String getConferenceId(JingleIQ jiq, boolean isReceived)
    {
        if (isReceived)
        {
            return jiq.getFrom();
        }
        else
        {
            return jiq.getTo();
        }
    }
    
    public static String getSid(JingleIQ jiq)
    {
        return jiq.getSID();
    }
}
