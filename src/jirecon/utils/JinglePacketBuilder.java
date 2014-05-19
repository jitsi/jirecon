package jirecon.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ice4j.TransportAddress;
import org.ice4j.ice.Candidate;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.format.AudioMediaFormat;
import org.jitsi.service.neomedia.format.MediaFormat;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidatePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidateType;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ParameterPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.PayloadTypePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.RtpDescriptionPacketExtension;

public class JinglePacketBuilder
{
    public static JingleIQ createJingleSessionAcceptPacket(String from,
        String to, String sid, List<ContentPacketExtension> contents)
    {
        JingleIQ accept =
            JinglePacketFactory.createSessionAccept(from, to, sid, contents);

        return accept;
    }

    public static ContentPacketExtension createContentPacketExt(
        RtpDescriptionPacketExtension description,
        IceUdpTransportPacketExtension transport)
    {
        final ContentPacketExtension content = new ContentPacketExtension();

        content.setCreator(CreatorEnum.responder);
        content.setName(description.getMedia());
        content.setSenders(SendersEnum.initiator);
        content.addChildExtension(description);
        content.addChildExtension(transport);

        return content;
    }

    /**
     * Create RtpDescriptionPacketExtension. We only add one payloadtype.
     * @param media
     * @param payloadType
     * @return
     */
    public static RtpDescriptionPacketExtension createDescriptionPacketExt(
        MediaType media, PayloadTypePacketExtension payloadType)
    {
        final RtpDescriptionPacketExtension description =
            new RtpDescriptionPacketExtension();

        description.setMedia(media.toString());
        description.addPayloadType(payloadType);

        return description;
    }

    public static PayloadTypePacketExtension createPayloadTypePacketExt(
        byte dynamicPayloadTypeId, MediaFormat format)
    {
        final PayloadTypePacketExtension payloadType =
            new PayloadTypePacketExtension();

        payloadType.setId(dynamicPayloadTypeId);
        payloadType.setName(format.getEncoding());

        if (format instanceof AudioMediaFormat)
        {
            payloadType.setChannels(((AudioMediaFormat) format).getChannels());
        }
        payloadType.setClockrate((int) format.getClockRate());

        for (Map.Entry<String, String> e : format.getFormatParameters()
            .entrySet())
        {
            final ParameterPacketExtension parameter =
                new ParameterPacketExtension();
            parameter.setName(e.getKey());
            parameter.setValue(e.getValue());
            payloadType.addParameter(parameter);
        }

        return payloadType;
    }

    public static IceUdpTransportPacketExtension createTransportPacketExt(
        String password, String ufrag, List<CandidatePacketExtension> candidates)
    {
        IceUdpTransportPacketExtension transport =
            new IceUdpTransportPacketExtension();

        transport.setPassword(password);
        transport.setUfrag(ufrag);
        for (CandidatePacketExtension c : candidates)
        {
            transport.addCandidate(c);
        }

        return transport;
    }

    public static List<CandidatePacketExtension> createCandidatePacketExtList(
        List<Component> components, int generation)
    {
        List<CandidatePacketExtension> candidates =
            new ArrayList<CandidatePacketExtension>();

        int id = 1;
        for (Component c : components)
        {
            for (Candidate<?> can : c.getLocalCandidates())
            {
                CandidatePacketExtension candidate =
                    new CandidatePacketExtension();
                candidate.setComponent(c.getComponentID());
                candidate.setFoundation(can.getFoundation());
                candidate.setGeneration(generation);
                candidate.setID(String.valueOf(id++));
                candidate.setNetwork(1);
                TransportAddress ta = can.getTransportAddress();
                candidate.setIP(ta.getHostAddress());
                candidate.setPort(ta.getPort());
                candidate.setPriority(can.getPriority());
                candidate.setProtocol(can.getTransport().toString());
                candidate.setType(CandidateType.valueOf(can.getType()
                    .toString()));
                candidates.add(candidate);
            }
        }
        // DtlsFingerprintPacketExtension
        // Do nothing here

        return candidates;
    }
}
