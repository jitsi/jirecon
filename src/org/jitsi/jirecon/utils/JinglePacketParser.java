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
 * A packet parser which is used to extract various information from
 * <tt>JingleIQ</tt>.
 * <p>
 * <strong>Warning:</strong> There are only static methods.
 * 
 * @author lishunyang
 * 
 */
public class JinglePacketParser
{
    /**
     * Get sender's jid of specified <tt>JingleIQ</tt>.
     * 
     * @param jiq
     * @return sender's jid.
     */
    public static String getFrom(JingleIQ jiq)
    {
        return jiq.getFrom();
    }

    /**
     * Get receiver's jid of specified <tt>JingleIQ</tt>.
     * 
     * @param jiq
     * @return The receiver's jid.
     */
    public static String getTo(JingleIQ jiq)
    {
        return jiq.getTo();
    }

    /**
     * Get the type of specified <tt>JingleIQ</tt>.
     * 
     * @param jiq
     * @return packet type.
     */
    public static IQ.Type getType(JingleIQ jiq)
    {
        return jiq.getType();
    }

    /**
     * Get the action of specified <tt>JingleIQ</tt>.
     * 
     * @param jiq
     * @return action
     */
    public static JingleAction getAction(JingleIQ jiq)
    {
        return jiq.getAction();
    }

    /**
     * Get the content packet extension from a <tt>JingleIQ</tt> of specified
     * <tt>MediaType</tt>.
     * 
     * @param jiq <tt>JingleIQ</tt>
     * @param media <tt>MediaType</tt>
     * @return <tt>ContentPacketExtension</tt>
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
     * Get the <tt>RtpDescriptionPacketExtension</tt> from a
     * <tt>ContentPacketExtension</tt>.
     * 
     * @param content <tt>ContentPacketExtension</tt>
     * @return <tt>RtpDescriptionPacketExtension</tt>
     */
    public static RtpDescriptionPacketExtension getDescriptionPacketExt(
        ContentPacketExtension content)
    {
        return content.getFirstChildOfType(RtpDescriptionPacketExtension.class);
    }

    /**
     * Get the <tt>RtpDescriptionPacketExtension</tt> from a <tt>JingleIQ</tt>
     * of specified <tt>MediaType</tt>.
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
     * Get a list of <tt>PayloadTypePacketExtension</tt> from a
     * <tt>RtpDescriptionPacketExtension</tt>.
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
     * Get a list of <tt>PayloadTypePacketExtension</tt> from a
     * <tt>JingleIQ</tt> with specified <tt>MediaType</tt>.
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
     * Get <tt>IceUdpTransportPacketExtension</tt> from a <tt>JingleIQ</tt> of
     * specified <tt>MediaType</tt>.
     * 
     * @param jiq The Jingle packet.
     * @param media is the specified <tt>MediaType</tt>.
     * @return <tt>IceUdpTransportPacketExtension</tt>.
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
     * Get all transport packet extensions from a Jingle packet.
     * 
     * @param jiq The Jingle packet.
     * @return Map between <tt>MediaType</tt> and
     *         <tt>IceUdpTransportPacketExtension</tt>.
     */
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
     * Get ufrag from a <tt>JingleIQ</tt> of specified <tt>MediaType</tt>.
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
     * Get ufrag from a <tt>IceUdpTransportPacketExtension</tt>.
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
     * Get a list of <tt>CandidatePacketExtension</tt> from a
     * <tt>IceUdpTransportPacketExtension</tt>.
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
     * Get a list of <tt>CandidatePacketExtension</tt> from a <tt>JingleIQ</tt>
     * of specified <tt>MediaType</tt>.
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
     * Get password from a <tt>IceUdpTransportPacketExtension</tt>.
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
     * Get password from a <tt>JingleIQ</tt> of specified <tt>MediaType</tt>.
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
     * Get MUC jid from a <tt>JingleIQ</tt>.
     * 
     * @param jiq The Jingle packet.
     * @param isReceived Whether this Jingle packet is received.
     * @return MUC jid
     */
    public static String getMucJid(JingleIQ jiq, boolean isReceived)
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

    /**
     * Get session id of a <tt>JingleIQ</tt>.
     * 
     * @param jiq
     * @return session id
     */
    public static String getSid(JingleIQ jiq)
    {
        return jiq.getSID();
    }

    /**
     * Get maps between <tt>MediaFormat</tt> and dynamic payload type id from a
     * specified <tt>JingleIQ</tt>.
     * 
     * @param jiq
     * @return map between <tt>MediaFormat</tt> and dynamic payload type id.
     */
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
        }

        return formatAndDynamicPTs;
    }

    /**
     * Get fingerprint from a specified <tt>JingleIQ</tt>.
     * 
     * @param jiq
     * @return fingerprint.
     */
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
