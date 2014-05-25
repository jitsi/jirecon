/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

// TODO: Rewrite those import statements to package import statement.
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.java.sip.communicator.impl.protocol.jabber.IceUdpTransportManager;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidateType;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;
import net.java.sip.communicator.impl.protocol.jabber.jinglesdp.JingleUtils;
import net.java.sip.communicator.util.Logger;

import org.ice4j.*;
import org.ice4j.ice.*;
import org.jitsi.impl.neomedia.format.MediaFormatFactoryImpl;
import org.jitsi.jirecon.JireconEventListener;
import org.jitsi.jirecon.utils.JinglePacketParser;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.format.AudioMediaFormat;
import org.jitsi.service.neomedia.format.MediaFormat;
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
    implements JireconSession
{
    List<JireconEventListener> listeners = new ArrayList<JireconEventListener>(); 

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
    private JireconSessionInfo info;

    /**
     * The log generator.
     */
    private Logger logger;

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
        this.connection = connection;
        logger = Logger.getLogger(JireconSessionImpl.class);
        logger.setLevelInfo();
        this.info = new JireconSessionInfo();
    }

    /**
     * Join a JitsiMeet conference, prepare for building Jingle session.
     * 
     * @throws XMPPException
     */
    @Override
    public void startSession(String conferenceJid) throws XMPPException
    {
        info.setConferenceJid(conferenceJid);
        initiateIceAgent();
        joinConference();
        // After joining the conference, JireconSessionImpl will automatically
        // build a Jingle session with remote peer.
    }

    /**
     * Leave this JitsiMeet conference
     */
    @Override
    public void terminateSession()
    {
        closeSession();
        leaveConference();
        uninitiateIceAgent();
    }

    /**
     * Get key information about this Jingle session.
     * 
     * @return JingleSessionInfo.
     */
    @Override
    public JireconSessionInfo getSessionInfo()
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

    private void initiateIceAgent()
    {
        iceAgent = new Agent();
        PropertyChangeListener stateChangeListener =
            new PropertyChangeListener()
            {
                public void propertyChange(PropertyChangeEvent evt)
                {
                    Object newValue = evt.getNewValue();
                    IceAgentStatusChanged((IceProcessingState) newValue);
                }
            };
        iceAgent.addStateChangeListener(stateChangeListener);
    }

    private void uninitiateIceAgent()
    {
        iceAgent.free();
    }

    private void handleJingleSessionPacket(JingleIQ jiq)
    {
        if (IQ.Type.SET == jiq.getType() && JingleAction.SESSION_INITIATE == jiq.getAction())
        {
            info.setLocalNode(jiq.getTo());
            info.setRemoteNode(jiq.getFrom());
            info.setSid(jiq.getSID());
            handleSetPacket(jiq);
        }
    }

    private void updateStatus(JireconSessionStatus status, String msg)
    {
        // TODO: fire some event
        info.setSessionStatus(status);
        if (status == JireconSessionStatus.CONSTRUCTED || status == JireconSessionStatus.ABORTED)
        {
            for (JireconEventListener listener : listeners)
            {
                listener.handleEvent(new JireconSessionEvent(this, info));
            }
        }
    }

    private void handleAckPacket()
    {
        switch (info.getSessionStatus())
        {
        case INITIATING:
            updateStatus(JireconSessionStatus.INTIATING_SESSION_OK,
                "Build Jingle complete.");
            checkIceConnectivity();
            break;
        default:
            break;
        }
    }

    private void joinConference() throws XMPPException
    {
        conference =
            new MultiUserChat(connection, info.getConferenceJid());
        conference.join(NICKNAME);
        updateStatus(JireconSessionStatus.INITIATING, "Start session.");
    }

    private void leaveConference()
    {
        if (null != conference)
            conference.leave();
    }

    private void closeSession()
    {
        if (JireconSessionStatus.CONSTRUCTED == info.getSessionStatus())
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

        if (JingleAction.SESSION_INITIATE == jiq.getAction())
        {
            harvestLocalCandidates();
            harvestRemoteCandidates(jiq);
            harvestDynamicPayload(jiq);
            sendAccept(jiq);
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
        updateStatus(JireconSessionStatus.CONSTRUCTED,
            "Jirecon session constructed.");
    }

    /**
     * Check ICE connectivity
     */
    private void checkIceConnectivity()
    {
        logger.info("checkIceConnectivity begin");
        iceAgent.startConnectivityEstablishment();
    }

    private void IceAgentStatusChanged(IceProcessingState state)
    {
        switch (state)
        {
        case TERMINATED:
            updateStatus(JireconSessionStatus.INITIATING_CONNECTIVITY_OK,
                "Check ICE connectivity OK.");
            harvestCandidatePairs();
            break;
        case FAILED:
            updateStatus(JireconSessionStatus.INITIATING_CONNECTIVITY_FAILED,
                "Check ICE connectivity failed.");
            break;
        default:
            break;
        }
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
        for (MediaType media : MediaType.values())
        {
            contents.add(createContentPacketExtension(media));
        }

        JingleIQ acceptJiq =
            JinglePacketFactory.createSessionAccept(info.getLocalNode(),
                info.getRemoteNode(), info.getSid(), contents);

        connection.sendPacket(acceptJiq);

        logger.info("sendAccept finished");
    }

    private ContentPacketExtension createContentPacketExtension(MediaType media)
    {
        List<CandidatePacketExtension> candidates =
            new ArrayList<CandidatePacketExtension>();
        int id = 1;
        for (Component c : iceAgent.getStream(media.toString()).getComponents())
        {
            for (Candidate<?> can : c.getLocalCandidates())
            {
                CandidatePacketExtension candidate =
                    new CandidatePacketExtension();
                candidate.setComponent(c.getComponentID());
                candidate.setFoundation(can.getFoundation());
                candidate.setGeneration(iceAgent.getGeneration());
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

        IceUdpTransportPacketExtension transport =
            new IceUdpTransportPacketExtension();
        transport.setPassword(iceAgent.getLocalPassword());
        transport.setUfrag(iceAgent.getLocalUfrag());
        for (CandidatePacketExtension c : candidates)
        {
            transport.addCandidate(c);
        }

        PayloadTypePacketExtension payloadType =
            new PayloadTypePacketExtension();
        MediaFormat format = info.getFormat(media);
        payloadType.setId(info.getDynamicPayloadTypeId(media));
        payloadType.setName(format.getEncoding());
        if (format instanceof AudioMediaFormat)
        {
            payloadType.setChannels(((AudioMediaFormat) format).getChannels());
        }
        payloadType.setClockrate((int) format.getClockRate());
        for (Map.Entry<String, String> e : format.getFormatParameters()
            .entrySet())
        {
            ParameterPacketExtension parameter = new ParameterPacketExtension();
            parameter.setName(e.getKey());
            parameter.setValue(e.getValue());
            payloadType.addParameter(parameter);
        }

        RtpDescriptionPacketExtension description =
            new RtpDescriptionPacketExtension();
        description.setMedia(media.toString());
        description.addPayloadType(payloadType);

        ContentPacketExtension content = new ContentPacketExtension();
        content.setCreator(CreatorEnum.responder);
        content.setName(description.getMedia());
        content.setSenders(SendersEnum.initiator);
        content.addChildExtension(description);
        content.addChildExtension(transport);

        return content;
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

    public void sendTerminate(Reason reason, String reasonText)
    {
        connection.sendPacket(JinglePacketFactory.createSessionTerminate(
            info.getLocalNode(), info.getRemoteNode(), info.getSid(), reason,
            reasonText));
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
    public void addEventListener(JireconEventListener listener)
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void removeEventListener(JireconEventListener listener)
    {
        // TODO Auto-generated method stub
        
    }
}
