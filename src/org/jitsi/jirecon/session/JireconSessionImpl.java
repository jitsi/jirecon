/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

// TODO: Rewrite those import statements to package import statement.
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.impl.protocol.jabber.IceUdpTransportManager;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.SourcePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;
import net.java.sip.communicator.service.protocol.media.SrtpControls;
import net.java.sip.communicator.util.Logger;

import org.ice4j.ice.*;
import org.jitsi.impl.neomedia.format.MediaFormatFactoryImpl;
import org.jitsi.jirecon.JireconEvent;
import org.jitsi.jirecon.JireconEventId;
import org.jitsi.jirecon.JireconEventListener;
import org.jitsi.jirecon.dtlscontrol.JireconSrtpControlManager;
import org.jitsi.jirecon.extension.MediaExtension;
import org.jitsi.jirecon.recorder.JireconRecorderInfo;
import org.jitsi.jirecon.transport.JireconTransportManager;
import org.jitsi.jirecon.utils.JinglePacketParser;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.DtlsControl;
import org.jitsi.service.neomedia.MediaDirection;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.format.AudioMediaFormat;
import org.jitsi.service.neomedia.format.MediaFormat;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.packet.MUCUser;

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
    List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    private JireconTransportManager transportManager;

    private JireconSrtpControlManager srtpControlManager;

    private XMPPConnection connection;

    private MultiUserChat conference;

    private JireconSessionInfo info = new JireconSessionInfo();

    private Logger logger;

    private final String NICK_KEY = "JIRECON_NICKNAME";

    private String nick = "default";

    public JireconSessionImpl()
    {
        logger = Logger.getLogger(JireconSessionImpl.class);
    }

    @Override
    public void init(JireconConfiguration configuration,
        XMPPConnection connection, String conferenceJid,
        JireconTransportManager transportManager,
        JireconSrtpControlManager srtpControlManager)
    {
        this.nick = configuration.getProperty(NICK_KEY);
        this.connection = connection;
        this.info.setConferenceJid(conferenceJid);
        this.transportManager = transportManager;
        this.srtpControlManager = srtpControlManager;
        updateState(JireconSessionState.INITIATING);

        // TODO: Add init check

        addPacketSendingListener();
        addPacketReceivingListener();
    }

    @Override
    public void uninit()
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void start()
    {
        try
        {

            joinConference();
        }
        catch (XMPPException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Leave this JitsiMeet conference
     */
    @Override
    public void stop()
    {
        closeSession();
        leaveConference();
    }

    public void handlePacket(Packet packet)
    {
        if (JingleIQ.class == packet.getClass())
        {
            handleJinglePacket((JingleIQ) packet);
        }
        else if (Presence.class == packet.getClass())
        {
            handlePresencePacket((Presence) packet);
        }
        // TODO: This is ugly, but I can't find other way to resolve it.
        else if (packet.toXML().indexOf("type=\"result\"") >= 0)
        {
            handleAckPacket();
        }
    }

    private void handlePresencePacket(Presence p)
    {
        PacketExtension packetExt = p.getExtension(MediaExtension.NAMESPACE);
        MUCUser userExt =
            (MUCUser) p
                .getExtension("x", "http://jabber.org/protocol/muc#user");
        String remoteJid = userExt.getItem().getJid();
        if (null != remoteJid && null != packetExt)
        {
            MediaExtension mediaExt = (MediaExtension) packetExt;
            for (MediaType mediaType : MediaType.values())
            {
                // Make sure that we only handle audio or video type.
                if (MediaType.AUDIO != mediaType
                    && MediaType.VIDEO != mediaType)
                {
                    continue;
                }

                MediaDirection direction =
                    MediaDirection.parseString(mediaExt.getDirection(mediaType
                        .toString()));
                String ssrc = mediaExt.getDirection(mediaType.toString());
                if (direction == MediaDirection.SENDONLY
                    || direction == MediaDirection.SENDRECV)
                {
                    info.addRemoteSsrc(mediaType, remoteJid, ssrc);
                }
            }
        }
    }

    private void handleJinglePacket(JingleIQ jiq)
    {
        if (IQ.Type.SET == jiq.getType())
        {
            info.setLocalJid(jiq.getTo());
            info.setRemoteJid(jiq.getFrom());
            info.setSid(jiq.getSID());
            handleSetPacket(jiq);
        }
    }

    private void handleSetPacket(JingleIQ jiq)
    {
        // System.out.println("receive session init");
        sendAck(jiq);

        if (JingleAction.SESSION_INITIATE == jiq.getAction())
        {
            harvestDynamicPayload(jiq);
            harvestFingerprints(jiq);
            transportManager.harvestRemoteCandidates(jiq);
            fireEvent(new JireconEvent(this,
                JireconEventId.SESSION_RECEIVE_INIT));
        }
    }

    private void startConnectivityEstablishment()
    {
        PropertyChangeListener stateChangeListener =
            new PropertyChangeListener()
            {
                public void propertyChange(PropertyChangeEvent evt)
                {
                    Object newValue = evt.getNewValue();

                    if (IceProcessingState.COMPLETED.equals(newValue)
                        || IceProcessingState.FAILED.equals(newValue)
                        || IceProcessingState.TERMINATED.equals(newValue))
                    {
                        if (logger.isTraceEnabled())
                            logger.trace("ICE " + newValue);

                        transportManager.removeStateChangeListener(this);

                        if (IceProcessingState.FAILED.equals(newValue))
                        {
                            fireEvent(new JireconEvent(this,
                                JireconEventId.SESSION_ABORTED));
                        }
                        else if (IceProcessingState.COMPLETED.equals(newValue)
                            || IceProcessingState.TERMINATED.equals(newValue))
                        {
                            fireEvent(new JireconEvent(this,
                                JireconEventId.SESSION_CONSTRUCTED));
                        }
                    }
                    
                    if (IceProcessingState.WAITING.equals(newValue))
                    {
                        System.out.println("Ice check waiting.");
                    }
                }
            };

        transportManager.addStateChangeListener(stateChangeListener);
        transportManager.startConnectivityEstablishment();
    }

    private void harvestFingerprints(JingleIQ jiq)
    {
        for (MediaType mediaType : MediaType.values())
        {
            if (mediaType != MediaType.AUDIO && mediaType != MediaType.VIDEO)
                continue;
            srtpControlManager.addRemoteFingerprint(mediaType, "sha-1",
                JinglePacketParser.getTransportPacketExt(jiq, mediaType)
                    .getText());
        }
    }

    private void handleAckPacket()
    {
        startConnectivityEstablishment();
    }

    private ContentPacketExtension createContentPacketExtension(
        MediaType mediaType, JireconSessionInfo sessionInfo,
        JireconRecorderInfo recorderInfo)
    {
        logger.debug(this.getClass() + " createContentPacketExtension");
        IceUdpTransportPacketExtension transportPE =
            transportManager.getTransportPacketExt();

        // DTLS stuff, fingerprint packet extension
        String fingerprint = srtpControlManager.getLocalFingerprint(mediaType);
        String hash =
            srtpControlManager.getLocalFingerprintHashFunction(mediaType);
        DtlsFingerprintPacketExtension fingerprintPE =
            transportPE
                .getFirstChildOfType(DtlsFingerprintPacketExtension.class);
        if (fingerprintPE == null)
        {
            fingerprintPE = new DtlsFingerprintPacketExtension();
            transportPE.addChildExtension(fingerprintPE);
        }
        fingerprintPE.setFingerprint(fingerprint);
        fingerprintPE.setHash(hash);

        List<PayloadTypePacketExtension> payloadTypes =
            new ArrayList<PayloadTypePacketExtension>();
        for (Map.Entry<MediaFormat, Byte> e : sessionInfo.getPayloadTypes(
            mediaType).entrySet())
        {
            PayloadTypePacketExtension payloadType =
                new PayloadTypePacketExtension();
            payloadType.setId(e.getValue());
            payloadType.setName(e.getKey().getEncoding());
            if (e.getKey() instanceof AudioMediaFormat)
            {
                payloadType.setChannels(((AudioMediaFormat) e.getKey())
                    .getChannels());
            }
            payloadType.setClockrate((int) e.getKey().getClockRate());
            for (Map.Entry<String, String> en : e.getKey()
                .getFormatParameters().entrySet())
            {
                ParameterPacketExtension parameter =
                    new ParameterPacketExtension();
                parameter.setName(en.getKey());
                parameter.setValue(en.getValue());
                payloadType.addParameter(parameter);
            }
            payloadTypes.add(payloadType);
        }

        RtpDescriptionPacketExtension description =
            new RtpDescriptionPacketExtension();
        description.setMedia(mediaType.toString());
        for (PayloadTypePacketExtension p : payloadTypes)
        {
            description.addPayloadType(p);
        }
        SourcePacketExtension sourcePacketExtension =
            new SourcePacketExtension();
        description.setSsrc(recorderInfo.getLocalSsrc(mediaType).toString());

        sourcePacketExtension.setSSRC(recorderInfo.getLocalSsrc(mediaType));
        // sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
        // "cname", LibJitsi.getMediaService().getRtpCname()));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "msid", recorderInfo.getMsid(mediaType)));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "mslabel", recorderInfo.getMsLabel()));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "label", recorderInfo.getLabel(mediaType)));
        description.addChildExtension(sourcePacketExtension);

        ContentPacketExtension content = new ContentPacketExtension();
        content.setCreator(CreatorEnum.responder);
        content.setName(description.getMedia());
        content.setSenders(SendersEnum.initiator);
        content.addChildExtension(description);
        content.addChildExtension(transportPE);

        return content;
    }

    private JingleIQ createSessionAcceptPacket(JireconSessionInfo sessionInfo,
        JireconRecorderInfo recorderInfo)
    {
        logger.debug(this.getClass() + " createSessionAcceptPacket");
        final List<ContentPacketExtension> contents =
            new ArrayList<ContentPacketExtension>();
        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
            {
                continue;
            }

            contents.add(createContentPacketExtension(mediaType, sessionInfo,
                recorderInfo));
        }

        JingleIQ acceptJiq =
            JinglePacketFactory.createSessionAccept(sessionInfo.getLocalJid(),
                sessionInfo.getRemoteJid(), sessionInfo.getSid(), contents);

        return acceptJiq;
    }

    private void joinConference() throws XMPPException
    {
        conference = new MultiUserChat(connection, info.getConferenceJid());
        conference.join(nick);
        updateState(JireconSessionState.JOIN_CONFERENCE);
    }

    private void leaveConference()
    {
        if (null != conference)
        {
            conference.leave();
        }
    }

    private void closeSession()
    {
        if (JireconSessionState.CONSTRUCTED == info.getState())
        {
            sendTerminate(Reason.SUCCESS, "OK, gotta go!");
        }
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
            info.getLocalJid(), info.getRemoteJid(), info.getSid(), reason,
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

        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
            {
                continue;
            }

            // TODO: Video format has some problem, RED payload
            // FIXME: We only choose the first payloadtype
            for (PayloadTypePacketExtension payloadTypePacketExt : JinglePacketParser
                .getPayloadTypePacketExts(jiq, mediaType))
            {
                MediaFormat format =
                    fmtFactory.createMediaFormat(
                        payloadTypePacketExt.getName(),
                        payloadTypePacketExt.getClockrate(),
                        payloadTypePacketExt.getChannels());
                if (format != null)
                {
                    info.addPayloadType(mediaType, format,
                        (byte) (payloadTypePacketExt.getID()));
                }
            }

            // Collect the focus' SSRC
            // info.addRemoteSsrc(media, jiq.getInitiator(), JinglePacketParser
            // .getDescriptionPacketExt(jiq, media).getSsrc());
            // Collect remote fingerprints
            IceUdpTransportPacketExtension transport =
                JinglePacketParser.getTransportPacketExt(jiq, mediaType);
            info.setRemoteFingerprint(mediaType, transport.getText());
        }
        logger.info("harvestDynamicPayload finished");
    }

    @Override
    public void addEventListener(JireconEventListener listener)
    {
        listeners.add(listener);
    }

    @Override
    public void removeEventListener(JireconEventListener listener)
    {
        listeners.remove(listener);
    }

    private void addPacketSendingListener()
    {
        connection.addPacketSendingListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                System.out.println("--->: " + packet.toXML());
            }
        }, new PacketFilter()
        {
            @Override
            public boolean accept(Packet packet)
            {
                return true;
            }
        });
    }

    private void fireEvent(JireconEvent evt)
    {
        for (JireconEventListener l : listeners)
        {
            l.handleEvent(evt);
        }
    }

    private void addPacketReceivingListener()
    {
        connection.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                System.out.println(packet.getClass() + "<---: "
                    + packet.toXML());
                handlePacket(packet);
            }
        }, new PacketFilter()
        {
            @Override
            public boolean accept(Packet packet)
            {
                if (null != info.getLocalJid()
                    && !packet.getTo().equals(info.getLocalJid()))
                {
                    logger.fatal("packet failed: to " + packet.getTo()
                        + ", but we are " + info.getLocalJid());
                    return false;
                }
                return true;
            }
        });
    }

    @Override
    public JireconSessionInfo getSessionInfo()
    {
        return info;
    }

    private void updateState(JireconSessionState state)
    {
        switch (state)
        {
        case JOIN_CONFERENCE:
            fireEvent(new JireconEvent(this, JireconEventId.SESSION_BUILDING));
            break;
        case CONSTRUCTED:
            fireEvent(new JireconEvent(this, JireconEventId.SESSION_CONSTRUCTED));
            break;
        case ABORTED:
            fireEvent(new JireconEvent(this, JireconEventId.SESSION_ABORTED));
            break;
        default:
            break;
        }

        info.setState(state);
    }

    @Override
    public void sendAcceptPacket(JireconRecorderInfo recorderInfo)
    {
        connection.sendPacket((createSessionAcceptPacket(info, recorderInfo)));
    }
}
