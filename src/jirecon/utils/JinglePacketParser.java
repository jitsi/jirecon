package jirecon.utils;

import java.util.ArrayList;
import java.util.List;

import org.ice4j.Transport;
import org.ice4j.ice.RemoteCandidate;
import org.jitsi.impl.neomedia.format.MediaFormatFactoryImpl;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jivesoftware.smack.packet.IQ;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidatePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.PayloadTypePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.RtpDescriptionPacketExtension;

import javax.media.Format;

public class JinglePacketParser
{
    public static String getFrom(JingleIQ jiq)
    {
        return jiq.getFrom();
    }

    public static String getTo(JingleIQ jiq)
    {
        return jiq.getTo();
    }

    public static IQ.Type getType(JingleIQ jiq)
    {
        return jiq.getType();
    }

    public static JingleAction getAction(JingleIQ jiq)
    {
        return jiq.getAction();
    }

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
     * Get the RtpDescriptionPacketExtension
     * @param content
     * @return
     */
    public static RtpDescriptionPacketExtension getDescriptionPacketExt(
        ContentPacketExtension content)
    {
        return content.getFirstChildOfType(RtpDescriptionPacketExtension.class);
    }

    /**
     * Get the RtpDescriptionPacketExtension
     * @param jiq
     * @param media
     * @return
     */
    public static RtpDescriptionPacketExtension getDescriptionPacketExt(
        JingleIQ jiq, MediaType media)
    {
        return getDescriptionPacketExt(getContentPacketExt(jiq, media));
    }
    
    /**
     * Get list of PayloadTypePacketExtension
     * @param description
     * @return
     */
    public static List<PayloadTypePacketExtension> getPayloadTypePacketExts(RtpDescriptionPacketExtension description)
    {
        return description.getPayloadTypes();
    }
    
    /**
     * Get list of PayloadTypePacketExtension
     * @param jiq
     * @param media
     * @return
     */
    public static List<PayloadTypePacketExtension> getPayloadTypePacketExts(JingleIQ jiq, MediaType media)
    {
        return getPayloadTypePacketExts(getDescriptionPacketExt(jiq, media));
    }

    public static IceUdpTransportPacketExtension getTransportPacketExt(JingleIQ jiq, MediaType media)
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

    public static String getTransportUfrag(JingleIQ jiq, MediaType media)
    {
        return getTransportUfrag(getTransportPacketExt(jiq, media));
    }

    public static String getTransportUfrag(
        IceUdpTransportPacketExtension transport)
    {
        return transport.getUfrag();
    }

    public static List<CandidatePacketExtension> getCandidatePacketExt(
        IceUdpTransportPacketExtension transport)
    {
        return transport.getCandidateList();
    }
    
    public static List<CandidatePacketExtension> getCandidatePacketExt(JingleIQ jiq, MediaType media)
    {
        return getCandidatePacketExt(getTransportPacketExt(jiq, media));
    }
    
    public static String getTransportPassword(IceUdpTransportPacketExtension transport)
    {
        return transport.getPassword();
    }
    
    public static String getTransportPassword(JingleIQ jiq, MediaType media)
    {
        return getTransportPassword(getTransportPacketExt(jiq, media));
    }
}
