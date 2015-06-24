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
package org.jitsi.jirecon.utils;

import java.util.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
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
     * Get the content packet extension from a <tt>JingleIQ</tt> of specified
     * <tt>MediaType</tt>.
     * 
     * @param jiq <tt>JingleIQ</tt>
     * @param mediaType <tt>MediaType</tt>
     * @return <tt>ContentPacketExtension</tt>. Null if no associated packet was
     *         found.
     */
    public static ContentPacketExtension getContentPacketExt(JingleIQ jiq,
        MediaType mediaType)
    {
        if (null == jiq || null == mediaType)
            return null;

        for (ContentPacketExtension c : jiq.getContentList())
        {
            if (mediaType.toString().equalsIgnoreCase(c.getName()))
                return c;
        }

        return null;
    }

    /**
     * Get the <tt>RtpDescriptionPacketExtension</tt> from a
     * <tt>ContentPacketExtension</tt>.
     * 
     * @param content <tt>ContentPacketExtension</tt>
     * @return <tt>RtpDescriptionPacketExtension</tt>. Null if no associated
     *         packet was found.
     */
    public static RtpDescriptionPacketExtension getDescriptionPacketExt(
        ContentPacketExtension content)
    {
        return content == null ? null : content
            .getFirstChildOfType(RtpDescriptionPacketExtension.class);
    }

    /**
     * Get <tt>IceUdpTransportPacketExtension</tt> from a <tt>JingleIQ</tt> of
     * specified <tt>MediaType</tt>.
     * 
     * @param jiq The Jingle packet.
     * @param mediaType is the specified <tt>MediaType</tt>.
     * @return <tt>IceUdpTransportPacketExtension</tt>. Null if no associated
     *         packet was found.
     */
    public static IceUdpTransportPacketExtension getTransportPacketExt(
        JingleIQ jiq, MediaType mediaType)
    {
        if (null == jiq || null == mediaType)
            return null;

        for (ContentPacketExtension c : jiq.getContentList())
        {
            if (mediaType.toString().equalsIgnoreCase(c.getName()))
            {
                return c
                    .getFirstChildOfType(IceUdpTransportPacketExtension.class);
            }
        }

        return null;
    }

    /**
     * Get maps between <tt>MediaFormat</tt> and dynamic payload type id from a
     * specified <tt>JingleIQ</tt> and <tt>MediaType</tt>.
     * <p>
     * <strong>Warning: </strong>Libjitsi must be started.
     * 
     * @param jiq
     * @return map between <tt>MediaFormat</tt> and dynamic payload type id.
     *         Null if no associated packet was found.
     */
    public static Map<MediaFormat, Byte> getFormatAndDynamicPTs(JingleIQ jiq,
        MediaType mediaType)
    {
        if (null == jiq || null == mediaType)
            return null;

        final Map<MediaFormat, Byte> formatAndPTs =
            new HashMap<MediaFormat, Byte>();
        final MediaFormatFactoryImpl fmtFactory = new MediaFormatFactoryImpl();

        List<PayloadTypePacketExtension> pts
            = getPayloadTypePacketExts(jiq, mediaType);
        if (pts == null)
            return null;

        for (PayloadTypePacketExtension payloadTypePacketExt : pts)
        {
            MediaFormat format =
                fmtFactory.createMediaFormat(payloadTypePacketExt.getName(),
                    payloadTypePacketExt.getClockrate(),
                    payloadTypePacketExt.getChannels());
            if (format != null)
                formatAndPTs.put(format, (byte) (payloadTypePacketExt.getID()));
        }

        return formatAndPTs;
    }

    /**
     * Get <tt>DtlsFingerprintPacketExtension</tt> from a <tt>JingleIQ</tt> with
     * specified <tt>MediaType</tt>.
     * 
     * @param jiq
     * @param mediaType
     * @return Null if no associated packet was found.
     */
    public static DtlsFingerprintPacketExtension getFingerprintPacketExt(
        JingleIQ jiq, MediaType mediaType)
    {
        if (null == jiq || null == mediaType)
            return null;

        IceUdpTransportPacketExtension transport = null;

        transport = getTransportPacketExt(jiq, mediaType);
        if (null == transport)
            return null;

        return transport
            .getFirstChildOfType(DtlsFingerprintPacketExtension.class);
    }

    /**
     * Get <tt>MediaType</tt>s that appeared in <tt>JingleIQ</tt>, we think
     * those appeared <tt>MediaType</tt>s are supported by remote peer.
     * 
     * @param jiq
     * @return Null if no associated packet was found.
     */
    public static MediaType[] getSupportedMediaTypes(JingleIQ jiq)
    {
        if (null == jiq)
            return null;

        MediaType[] mediaTypes = new MediaType[jiq.getContentList().size()];

        for (int i = 0; i < mediaTypes.length; i++)
        {
            mediaTypes[i] =
                MediaType.parseString(jiq.getContentList().get(i).getName());
        }

        return mediaTypes;
    }

    /**
     * Get a list of <tt>PayloadTypePacketExtension</tt> from a
     * <tt>RtpDescriptionPacketExtension</tt>.
     * 
     * @param description The description packet extension.
     * @return List of payloadtype packet extensions. Null if no associated
     *         packet was found.
     */
    private static List<PayloadTypePacketExtension> getPayloadTypePacketExts(
        RtpDescriptionPacketExtension description)
    {
        return description == null ? null : description.getPayloadTypes();
    }

    /**
     * Get a list of <tt>PayloadTypePacketExtension</tt> from a
     * <tt>JingleIQ</tt> with specified <tt>MediaType</tt>.
     * 
     * @param jiq The Jingle packet.
     * @param mediaType The media type.
     * @return List of payloadtype packet extensions. Null if no associated
     *         packet was found.
     */
    private static List<PayloadTypePacketExtension> getPayloadTypePacketExts(
        JingleIQ jiq, MediaType mediaType)
    {
        return getPayloadTypePacketExts(getDescriptionPacketExt(jiq, mediaType));
    }

    /**
     * Get the <tt>RtpDescriptionPacketExtension</tt> from a <tt>JingleIQ</tt>
     * of specified <tt>MediaType</tt>.
     * 
     * @param jiq The Jingle packet.
     * @param mediaType The media type.
     * @return Description packet extension. Null if no associated packet was
     *         found.
     */
    private static RtpDescriptionPacketExtension getDescriptionPacketExt(
        JingleIQ jiq, MediaType mediaType)
    {
        return getDescriptionPacketExt(getContentPacketExt(jiq, mediaType));
    }
}
