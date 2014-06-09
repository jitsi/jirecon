/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.utils;

// TODO: Rewrite those import statements to package import statement.
import java.util.*;

import org.jitsi.impl.neomedia.format.MediaFormatFactoryImpl;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jivesoftware.smack.packet.IQ;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;

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

    public static Map<MediaType, IceUdpTransportPacketExtension> getTransportPacketExts(
        JingleIQ jiq)
    {
        Map<MediaType, IceUdpTransportPacketExtension> transportPEs =
            new HashMap<MediaType, IceUdpTransportPacketExtension>();

        for (MediaType mediaType : MediaType.values())
        {
            if (mediaType != MediaType.AUDIO && mediaType != MediaType.VIDEO)
                continue;

            transportPEs.put(mediaType, getTransportPacketExt(jiq, mediaType));
        }

        return transportPEs;
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

    public static Map<MediaFormat, Byte> getFormatAndDynamicPTs(JingleIQ jiq)
    {
        Map<MediaFormat, Byte> formatAndDynamicPTs =
            new HashMap<MediaFormat, Byte>();
        final MediaFormatFactoryImpl fmtFactory = new MediaFormatFactoryImpl();

        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
            {
                continue;
            }

            // TODO: Video format has some problem, RED only
            // FIXME: There, it only choose the first payloadtype
            for (PayloadTypePacketExtension payloadTypePacketExt : getPayloadTypePacketExts(
                jiq, mediaType))
            {
                MediaFormat format =
                    fmtFactory.createMediaFormat(
                        payloadTypePacketExt.getName(),
                        payloadTypePacketExt.getClockrate(),
                        payloadTypePacketExt.getChannels());
                if (format != null)
                {
                    formatAndDynamicPTs.put(format,
                        (byte) (payloadTypePacketExt.getID()));
                }
            }

            // TODO Fingerprint stuff, where should it be?
            // IceUdpTransportPacketExtension transport =
            // JinglePacketParser.getTransportPacketExt(jiq, mediaType);
            // info.setRemoteFingerprint(mediaType, transport.getText());
        }

        return formatAndDynamicPTs;
    }

    public static Map<MediaType, String> getFingerprint(JingleIQ jiq)
    {
        Map<MediaType, String> fingerprints = new HashMap<MediaType, String>();
        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
            {
                continue;
            }
            IceUdpTransportPacketExtension transport =
                JinglePacketParser.getTransportPacketExt(jiq, mediaType);
            fingerprints.put(mediaType, transport.getText());
        }

        return fingerprints;
    }
}
