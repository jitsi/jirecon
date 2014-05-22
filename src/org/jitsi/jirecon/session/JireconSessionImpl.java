package org.jitsi.jirecon.session;

// TODO: Rewrite those import statements to package import statement.
import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.util.Logger;

import org.ice4j.*;
import org.ice4j.ice.*;
import org.jitsi.impl.neomedia.format.MediaFormatFactoryImpl;
import org.jitsi.jirecon.utils.JinglePacketBuilder;
import org.jitsi.jirecon.utils.JinglePacketParser;
import org.jitsi.jirecon.utils.JireconMessageReceiver;
import org.jitsi.jirecon.utils.JireconMessageSender;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.MediaType;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.MultiUserChat;


/**
 * This class is responsible for managing a Jingle session and extract some
 * information which could be used by others.
 * 
 * @author lishunyang
 * 
 */
public class JireconSessionImpl
    implements JireconSession, JireconMessageSender
{
    Set<JireconMessageReceiver> msgReceivers;

    /**
     * The XMPP connection, it is used to send Jingle packet.
     */
    private XMPPConnection connection;

    /**
     * The ICE agent, it is used to create ICE media stream and check ICE
     * connectivity.
     */
    private Agent iceAgent;

    /**
     * The conference which is bined with this Jingle session.
     */
    private MultiUserChat conference;

    /**
     * Some information about this Jingle session. It can be accessed by the
     * outside through getJingleSessionInfo method.
     */
    private SessionInfo info;

    private String from;

    private String to;

    private String sid;

    /**
     * The log generator.
     */
    private Logger logger;

    /**
     * The conference hostname.
     */
    private final String CONFERENCE_ADDR = "conference.example.com";

    /**
     * This will be used when join a conference.
     */
    private final String NICKNAME = "Jirecon";

    /**
     * Constructor, need an XMPP connection
     * 
     * @param connection An active XMPP connection.
     */
    public JireconSessionImpl(XMPPConnection connection)
    {
        msgReceivers = new HashSet<JireconMessageReceiver>();
        this.connection = connection;
        logger = Logger.getLogger(JireconSessionImpl.class);
        logger.setLevelInfo();
        this.info = new SessionInfo();
    }

    /**
     * Join a JitsiMeet conference, prepare for building Jingle session.
     * 
     * @throws XMPPException
     */
    @Override
    public void startSession(String conferenceId) throws XMPPException
    {
        info.setConferenceId(conferenceId);
        LibJitsi.start();
        iceAgent = new Agent();
        joinConference();
    }

    /**
     * Leave this JitsiMeet conference
     */
    @Override
    public void terminateSession()
    {
        closeSession();
        leaveConference();
        iceAgent.free();
        LibJitsi.stop();
    }
    
    /**
     * Get key information about this Jingle session.
     * 
     * @return JingleSessionInfo.
     */
    @Override
    public SessionInfo getSessionInfo()
    {
        return info;
    }
    
    /**
     * Receive Jingle packet and handle them.
     * 
     * @param jiq The Jingle packet
     */
    public void handleSessionPacket(Packet packet)
    {
        if (JingleIQ.class == packet.getClass())
        {
            handleJingleSessionPacket((JingleIQ) packet);
        }
        // TODO: This is ugly, but I can't find other way to resolve it.
        else if (packet.toXML().indexOf("type=\"result\"") >= 0)
        {
            handleAckPacket();
        }
    }
    
    private void handleJingleSessionPacket(JingleIQ jiq)
    {
        if (IQ.Type.SET == JinglePacketParser.getType(jiq))
        {
            from = JinglePacketParser.getFrom(jiq);
            to = JinglePacketParser.getTo(jiq);
            sid = JinglePacketParser.getSid(jiq);
            handleSetPacket(jiq);
        }
    }

    private void handleAckPacket()
    {
        if (JireconSessionStatus.INITIATING == info.getJingleSessionStatus())
        {
            info.setJingleSessionStatus(JireconSessionStatus.CONSTRUCTED);
            logger.info("JingleSession session constructed.");
            sendMsg(info.getConferenceId());
        }
    }

    private void joinConference() throws XMPPException
    {
        conference =
            new MultiUserChat(connection, info.getConferenceId() + "@" + CONFERENCE_ADDR);
        conference.join(NICKNAME);
    }

    private void leaveConference()
    {
        if (null != conference)
            conference.leave();
    }

    private void closeSession()
    {
        if (JireconSessionStatus.CONSTRUCTED == info.getJingleSessionStatus())
            sendTerminate(Reason.SUCCESS, "OK, gotta go!");
    }

    /**
     * Only handle the Jingle Set packet. Building the Jingle session.
     * 
     * @param jiq The Jingle set packet.
     */
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
            harvestCandidatePairs();
        }
    }

    private void harvestCandidatePairs()
    {
        logger.info("harvestCandidatePairs begin");
        for (MediaType media : MediaType.values())
        {
            IceMediaStream iceStream = iceAgent.getStream(media.toString());
            Component rtpComponent =
                iceStream.getComponent(org.ice4j.ice.Component.RTP);
            Component rtcpComponent =
                iceStream.getComponent(org.ice4j.ice.Component.RTCP);
            info.addRtpCandidatePair(media, rtpComponent.getSelectedPair());
            info.addRtcpCandidatePair(media, rtcpComponent.getSelectedPair());
        }
        logger.info("harvestCandidatePairs finished");
    }

    /**
     * Check ICE connectivity
     */
    private void checkIceConnectivity()
    {
        logger.info("checkIceConnectivity begin");
        // ICE check
        iceAgent.startConnectivityEstablishment();
        // TODO: When check is finished, send a message to
        // ConferenceRecorderManager
        while (iceAgent.getState() != IceProcessingState.TERMINATED)
        {
            try
            {
                Thread.sleep(1000);
                logger.info(IceProcessingState.TERMINATED);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        logger.info("checkIceConnectivity finished");
    }

    /**
     * Send session-accept packet according to an session-init packet
     * 
     * @param jiq The session-init packet
     */
    private void sendAccept(JingleIQ jiq)
    {
        logger.info("sendAccept begin");
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

        logger.info("sendAccept finished");
    }

    /**
     * Send a ack packet according to an Jingle packet
     * 
     * @param jiq The Jingle packet that will be sent a ack to.
     */
    private void sendAck(JingleIQ jiq)
    {
        connection.sendPacket(IQ.createResultIQ(jiq));
    }

    public void sendTerminate(Reason reason, String text)
    {
        connection.sendPacket(JinglePacketBuilder
            .createJingleSessionTerminatePacket(from, to, sid, reason, text));
    }

    /**
     * Harvest dynamic payloadtype id according to an Jingle session-init packet
     * 
     * @param jiq The Jingle session-init packet
     */
    private void harvestDynamicPayload(JingleIQ jiq)
    {
        logger.info("harvestDynamicPayload begin");
        final MediaFormatFactoryImpl fmtFactory = new MediaFormatFactoryImpl();

        for (MediaType media : MediaType.values())
        {
            // TODO: Video format has some problem, RED payload
            // FIXME: We only choose the first payloadtype
            final PayloadTypePacketExtension payloadType =
                JinglePacketParser.getPayloadTypePacketExts(jiq, media).get(0);
//            MediaFormat f =
//                fmtFactory.createMediaFormat(payloadType.getName(),
//                    payloadType.getClockrate(), payloadType.getChannels());
            info.addFormat(
                media,
                fmtFactory.createMediaFormat(payloadType.getName(),
                    payloadType.getClockrate(), payloadType.getChannels()));
            info.addDynamicPayloadTypeId(media, (byte) payloadType.getID());
            info.addRemoteSsrc(media, JinglePacketParser
                .getDescriptionPacketExt(jiq, media).getSsrc());
        }

        logger.info("harvestDynamicPayload finished");
    }

    /**
     * Get the ICE media stream, video or audio.
     * 
     * @param media Which type of media stream that you want.
     * @return ICE media stream
     */
    private IceMediaStream getIceMediaStream(MediaType media)
    {
        if (null == iceAgent.getStream(media.toString()))
        {
            iceAgent.createMediaStream(media.toString());
        }
        return iceAgent.getStream(media.toString());
    }

    /**
     * Harvest local candidates.
     */
    private void harvestLocalCandidates()
    {
        logger.info("harvestLocalCandidates begin");
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
        logger.info("harvestLocalCandidates finished");
    }

    /**
     * Harvest remote candidates according to a Jingle session-init packet.
     * 
     * @param jiq The Jingle session-init packet.
     */
    private void harvestRemoteCandidates(JingleIQ jiq)
    {
        logger.info("harvestRemoteCandidates begin");
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

        logger.info("harvestRemoteCandidates finished");
    }

    /**
     * Get related candidate from an stream component according to a candidate
     * packet extension.
     * 
     * @param candidate The candidate packet extension.
     * @param component The media component.
     * @return
     */
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

    @Override
    public void addReceiver(JireconMessageReceiver receiver)
    {
        msgReceivers.add(receiver);
    }

    @Override
    public void removeReceiver(JireconMessageReceiver receiver)
    {
        msgReceivers.remove(receiver);
    }

    @Override
    public void sendMsg(String msg)
    {
        for (JireconMessageReceiver r : msgReceivers)
        {
            r.receiveMsg(this, msg);
        }
    }
}
