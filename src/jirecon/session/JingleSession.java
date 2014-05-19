package jirecon.session;

import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jirecon.utils.JinglePacketBuilder;
import jirecon.utils.JinglePacketParser;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.util.Logger;

import org.ice4j.*;
import org.ice4j.ice.*;
import org.jitsi.impl.neomedia.format.MediaFormatFactoryImpl;
import org.jitsi.service.neomedia.MediaType;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smackx.muc.MultiUserChat;

public class JingleSession
{
    private String conferenceId;

    private XMPPConnection connection;

    private Agent iceAgent;

    private MultiUserChat muc;

    private JingleSessionInfo info;

    private Logger logger;

    public JingleSession(String conferenceId, XMPPConnection connection)
    {
        this.conferenceId = conferenceId;
        this.connection = connection;
        this.logger = Logger.getLogger("JingleSession(" + conferenceId + ")");
        this.info = new JingleSessionInfoImpl();
    }

    public void open() throws XMPPException
    {
        iceAgent = new Agent();
        muc =
            new MultiUserChat(connection, conferenceId
                + "@conference.example.com");
        muc.join("JitMeetConnector");
    }

    public void close()
    {
        if (null != muc)
            muc.leave();
        iceAgent.free();
    }

    public void handleJinglePacket(JingleIQ jiq)
    {
        if (IQ.Type.SET == JinglePacketParser.getType(jiq))
        {
            handleSetPacket(jiq);
        }
    }

    private void handleSetPacket(JingleIQ jiq)
    {
        sendAck(jiq);

        if (JingleAction.SESSION_INITIATE == JinglePacketParser.getAction(jiq))
        {
            harvestLocalCandidates();
            harvestRemoteCandidates(jiq);
            harvestDynamicPayload(jiq);
            sendAccept(jiq);
            checkIceConnectivity();
        }
    }
    
    private void checkIceConnectivity()
    {
        // ICE check
        iceAgent.startConnectivityEstablishment();

        // TODO: When check is finished, send a message to ConferenceRecorderManager
    }

    private void sendAccept(JingleIQ jiq)
    {
        final List<ContentPacketExtension> contents =
            new ArrayList<ContentPacketExtension>();
        // Audio content & Video content
        for (MediaType media : MediaType.values())
        {
            final List<CandidatePacketExtension> candidates =
                JinglePacketBuilder.createCandidatePacketExtList(iceAgent
                    .getStream(media.toString()).getComponents(), iceAgent
                    .getGeneration());

            final IceUdpTransportPacketExtension transport =
                JinglePacketBuilder.createTransportPacketExt(
                    iceAgent.getLocalPassword(), iceAgent.getLocalUfrag(),
                    candidates);

            final PayloadTypePacketExtension payloadType =
                JinglePacketBuilder.createPayloadTypePacketExt(
                    info.getDynamicPayloadTypeId(media), info.getFormat(media));

            final RtpDescriptionPacketExtension description =
                JinglePacketBuilder.createDescriptionPacketExt(media,
                    payloadType);

            final ContentPacketExtension content =
                JinglePacketBuilder.createContentPacketExt(description,
                    transport);

            contents.add(content);
        }

        JingleIQ acceptJiq =
            JinglePacketBuilder.createJingleSessionAcceptPacket(jiq.getTo(),
                jiq.getFrom(), jiq.getSID(), contents);

        connection.sendPacket(acceptJiq);
    }

    private void sendAck(JingleIQ jiq)
    {
        connection.sendPacket(IQ.createResultIQ(jiq));
    }

    private void harvestDynamicPayload(JingleIQ jiq)
    {
        final MediaFormatFactoryImpl fmtFactory = new MediaFormatFactoryImpl();

        for (MediaType media : MediaType.values())
        {
            // TODO: Video format has some problem, RED payload
            // FIXME: We only choose the first payloadtype
            final PayloadTypePacketExtension payloadType =
                JinglePacketParser.getPayloadTypePacketExts(jiq, media).get(0);
            info.addFormat(
                media,
                fmtFactory.createMediaFormat(payloadType.getName(),
                    payloadType.getClockrate(), payloadType.getChannels()));
            info.addDynamicPayloadTypeId(media, (byte) payloadType.getID());
            info.addRemoteSsrc(media, JinglePacketParser
                .getDescriptionPacketExt(jiq, media).getSsrc());
        }
    }

    private IceMediaStream getIceMediaStream(MediaType media)
    {
        if (null == iceAgent.getStream(media.toString()))
        {
            iceAgent.createMediaStream(media.toString());
        }
        return iceAgent.getStream(media.toString());
    }

    private void harvestLocalCandidates()
    {
        final int MIN_STREAM_PORT = 7000;
        final int MAX_STREAM_PORT = 9000;

        for (MediaType media : MediaType.values())
        {
            final IceMediaStream stream = getIceMediaStream(media);

            try
            {
                iceAgent.createComponent(stream, Transport.UDP,
                    MIN_STREAM_PORT, MIN_STREAM_PORT, MAX_STREAM_PORT);
                iceAgent.createComponent(stream, Transport.UDP,
                    MIN_STREAM_PORT, MIN_STREAM_PORT, MAX_STREAM_PORT);
            }
            catch (BindException e)
            {
                e.printStackTrace();
            }
            catch (IllegalArgumentException e)
            {
                e.printStackTrace();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void harvestRemoteCandidates(JingleIQ jiq)
    {
        for (MediaType media : MediaType.values())
        {
            final IceMediaStream stream = getIceMediaStream(media);
            final String ufrag =
                JinglePacketParser.getTransportUfrag(jiq, media);
            if (null != ufrag)
            {
                stream.setRemoteUfrag(ufrag);
            }

            final String password =
                JinglePacketParser.getTransportPassword(jiq, media);
            if (null != password)
            {
                stream.setRemotePassword(password);
            }

            List<CandidatePacketExtension> candidates =
                JinglePacketParser.getCandidatePacketExt(jiq, media);
            // Sort the remote candidates (host < reflexive < relayed) in order
            // to create first the host, then the reflexive, the relayed
            // candidates and thus be able to set the relative-candidate
            // matching the rel-addr/rel-port attribute.
            Collections.sort(candidates);
            for (CandidatePacketExtension c : candidates)
            {
                if (c.getGeneration() != iceAgent.getGeneration())
                    continue;
                final Component component =
                    stream.getComponent(c.getComponent());

                // FIXME: Add support for not-host address
                final RemoteCandidate remoteCandidate =
                    new RemoteCandidate(new TransportAddress(c.getIP(),
                        c.getPort(), Transport.parse(c.getProtocol())),
                        component, org.ice4j.ice.CandidateType.parse(c
                            .getType().toString()), c.getFoundation(),
                        c.getPriority(), getRelatedCandidate(c, component));

                component.addRemoteCandidate(remoteCandidate);
            }
        }
    }

    private RemoteCandidate getRelatedCandidate(
        CandidatePacketExtension candidate, Component component)
    {
        if ((candidate.getRelAddr() != null) && (candidate.getRelPort() != -1))
        {
            final String relAddr = candidate.getRelAddr();
            final int relPort = candidate.getRelPort();
            final TransportAddress relatedAddress =
                new TransportAddress(relAddr, relPort,
                    Transport.parse(candidate.getProtocol()));
            return component.findRemoteCandidate(relatedAddress);
        }
        return null;
    }
}
