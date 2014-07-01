/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.session;

import java.util.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.SourcePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.*;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.util.Logger;

import org.jitsi.jirecon.extension.MediaExtension;
import org.jitsi.jirecon.task.*;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.packet.MUCUser;
import org.jivesoftware.smackx.packet.Nick;

/**
 * An implementation of <tt>JireconSessoin</tt>.
 * 
 * @author lishunyang
 * 
 */
public class JireconSessionImpl
    implements JireconSession
{
    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger
        .getLogger(JireconSessionImpl.class);
    
    /**
     * Maximum wait time(microsecond).
     */
    private static final int MAX_WAIT_TIME = 5000;

    /**
     * The <tt>XMPPConnection</tt> is used to send/receive XMPP packet.
     */
    private XMPPConnection connection;

    /**
     * The <tt>JireconTaskEventListener</tt>, if <tt>JireconRecorder</tt> has
     * something important, it will notify them.
     */
    private List<JireconTaskEventListener> listeners =
        new ArrayList<JireconTaskEventListener>();

    /**
     * The instance of a <tt>MultiUserChat</tt>. <tt>JireconSessionImpl</tt>
     * will join it as the first step.
     */
    private MultiUserChat muc;

    /**
     * Local node full jid which is used for making <tt>JingleIQ</tt>.
     */
    private String localFullJid;

    /**
     * Remote node full jid which is used for making <tt>JingleIq</tt>.
     */
    private String remoteFullJid;

    /**
     * Jingle sessoin id which is used for making <tt>JingleIq</tt>.
     */
    private String sid;

    /**
     * Attribute "mslable" in source packet extension.
     */
    private String msLabel = UUID.randomUUID().toString();

    /**
     * Map between participant's jid and their associated ssrcs.
     * <p>
     * Every participant usually has two ssrc(one for audio and one for video),
     * these two ssrc are associated.
     */
    private Map<String, Map<MediaType, Long>> associatedSsrcs =
        new HashMap<String, Map<MediaType, Long>>();

    /**
     * The list of <tt>JireconSessionPacketListener</tt> which is used for
     * handling kinds of XMPP packet.
     */
    private List<JireconSessionPacketListener> packetListeners =
        new ArrayList<JireconSessionPacketListener>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(XMPPConnection connection)
    {
        logger.setLevelDebug();

        this.connection = connection;

        addPacketSendingListener();
        addPacketReceivingListener();

        // Register the packet listener to handle presence packet.
        JireconSessionPacketListener packetListener =
            new JireconSessionPacketListener()
            {
                @Override
                public void handlePacket(Packet packet)
                {
                    if (Presence.class == packet.getClass())
                        handlePresencePacket((Presence) packet);
                }
            };

        addPacketListener(packetListener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnect(Reason reason, String reasonText)
    {
        sendByePacket(reason, reasonText);
        leaveMUC();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void joinMUC(String mucJid, String nickname)
        throws OperationFailedException
    {
        logger.info("joinMUC");

        muc = new MultiUserChat(connection, mucJid);
        int suffix = 1;
        String finalNickname = nickname;
        while (true)
        {
            try
            {
                muc.join(finalNickname);
                break;
            }
            catch (XMPPException e)
            {
                if (409 == e.getXMPPError().getCode() && suffix < 10)
                {
                    finalNickname = nickname + "_" + suffix++;
                    continue;
                }
                throw new OperationFailedException("Could not join MUC, "
                    + e.getMessage(), OperationFailedException.GENERAL_ERROR);
            }
        }

        Packet presence = new Presence(Presence.Type.available);
        presence.setTo(mucJid);
        presence.addExtension(new Nick(finalNickname));
        connection.sendPacket(presence);
    }

    /**
     * Leave the Multi-User-Chat
     */
    private void leaveMUC()
    {
        logger.info("leaveMUC");

        if (null != muc)
            muc.leave();
    }

    /**
     * {@inheritDoc}
     * @throws OperationFailedException 
     */
    @Override
    public void sendAcceptPacket(
        Map<MediaType, Map<MediaFormat, Byte>> formatAndPTs,
        Map<MediaType, Long> localSsrcs,
        Map<MediaType, IceUdpTransportPacketExtension> transportPEs,
        Map<MediaType, DtlsFingerprintPacketExtension> fingerprintPEs)
    {
        logger.info("sendAcceptPacket");
        JingleIQ acceptIq = createAcceptPacket(formatAndPTs, localSsrcs, transportPEs, fingerprintPEs);
        connection.sendPacket(acceptIq);
    }

    /**
     * Send Jingle ack packet to remote peer.
     * 
     * @param jiq is the Jingle IQ packet that we've got.
     */
    private void sendAck(JingleIQ jiq)
    {
        logger.info("sendAck");
        connection.sendPacket(IQ.createResultIQ(jiq));
    }

    /**
     * Send Jingle session-terminate packet.
     * 
     * @param reason is the <tt>Reason</tt> type of the termination packet.
     * @param reasonText is the human-read text.
     */
    private void sendByePacket(Reason reason, String reasonText)
    {
        logger.info("sendByePacket");

        connection.sendPacket(JinglePacketFactory.createSessionTerminate(
            localFullJid, remoteFullJid, sid, reason, reasonText));
    }

    /**
     * Record some session information according Jingle session-init packet.
     * It's convenient to parse local jid, remote jid, sid, and so on, thouth
     * this may seems weird.
     * 
     * @param initJiq is the Jingle session-init packet.
     */
    private void recordSessionInfo(JingleIQ initJiq)
    {
        localFullJid = initJiq.getTo();
        remoteFullJid = initJiq.getFrom();
        sid = initJiq.getSID();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Once We got session-init packet, send back ack packet.
     */
    @Override
    public JingleIQ waitForInitPacket() 
        throws OperationFailedException
    {
        logger.info("waitForInitPacket");

        final List<JingleIQ> resultList = new ArrayList<JingleIQ>();
        final Object waitForInitPacketSyncRoot = new Object();

        // Register a packet listener for handling Jingle session-init packet.
        JireconSessionPacketListener packetListener =
            new JireconSessionPacketListener()
            {
                @Override
                public void handlePacket(Packet packet)
                {
                    if (packet instanceof JingleIQ)
                    {
                        final JingleIQ jiq = (JingleIQ) packet;
                        if (JingleAction.SESSION_INITIATE.equals(jiq
                            .getAction()))
                        {
                            resultList.add(jiq);
                            JireconSessionImpl.this.removePacketListener(this);

                            synchronized (waitForInitPacketSyncRoot)
                            {
                                waitForInitPacketSyncRoot.notify();
                            }
                        }
                    }
                }
            };

        addPacketListener(packetListener);
        boolean interrupted = false;

        synchronized (waitForInitPacketSyncRoot)
        {
            while (resultList.isEmpty())
            {
                try
                {
                    waitForInitPacketSyncRoot.wait(MAX_WAIT_TIME);
                    break;
                }
                catch (InterruptedException ie)
                {
                    interrupted = true;
                }
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();

        removePacketListener(packetListener);
        if (resultList.isEmpty())
        {
            throw new OperationFailedException(
                "Could not get session-init packet, maybe the MUC has locked.",
                OperationFailedException.GENERAL_ERROR);
        }

        final JingleIQ initIq = resultList.get(0);
        recordSessionInfo(initIq);
        sendAck(initIq);

        return initIq;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitForResultPacket() 
    {
        logger.info("waitForAckPacket");

        final List<Packet> resultList = new ArrayList<Packet>();
        final Object waitForAckPacketSyncRoot = new Object();

        // Register a packet listener for handling ack packet.
        JireconSessionPacketListener packetListener =
            new JireconSessionPacketListener()
            {
                @Override
                public void handlePacket(Packet packet)
                {
                    if (packet instanceof IQ && IQ.Type.RESULT.equals(((IQ)packet).getType()))
                    {
                        resultList.add(packet);
                        synchronized (waitForAckPacketSyncRoot)
                        {
                            waitForAckPacketSyncRoot.notify();
                        }
                    }
                }
            };

        addPacketListener(packetListener);
        boolean interrupted = false;

        synchronized (waitForAckPacketSyncRoot)
        {
            while (resultList.isEmpty())
            {
                try
                {
                    waitForAckPacketSyncRoot.wait(MAX_WAIT_TIME);
                }
                catch (InterruptedException ie)
                {
                    interrupted = true;
                }
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();

        removePacketListener(packetListener);
        if (resultList.isEmpty())
        {
//            throw new OperationFailedException("Could not get ack packet",
//                OperationFailedException.GENERAL_ERROR);
            logger.info("Couldn't receive result packet from remote peer.");
        }
    }

    /**
     * Handle the Jingle presence packet, record the partcipant's information
     * like jid, ssrc.
     * 
     * @param p is the presence packet.
     */
    private void handlePresencePacket(Presence p)
    {
        PacketExtension packetExt = p.getExtension(MediaExtension.NAMESPACE);
        MUCUser userExt =
            (MUCUser) p
                .getExtension("x", "http://jabber.org/protocol/muc#user");
        // In case of presence packet isn't sent by participant, here I get
        // participant id from p.getFrom() 
        String participantJid = userExt.getItem().getJid();

        // Jitsi-meeting presence packet should contain participant jid and
        // media packet extension
        if (null == participantJid || null == packetExt)
            return;
        
        MediaExtension mediaExt = (MediaExtension) packetExt;
        Map<MediaType, Long> ssrcs = new HashMap<MediaType, Long>();
        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
            {
                continue;
            }

            // TODO: If someone only sends audio, this could be changed. 
            MediaDirection direction =
                MediaDirection.parseString(mediaExt.getDirection(mediaType
                    .toString()));
            
            if (direction.allowsSending())
            {
                ssrcs.put(mediaType,
                    Long.valueOf(mediaExt.getSsrc(mediaType.toString())));
            }
            
        }
        
        // Oh, it seems that some participant has left the MUC.
        if (p.getType() == Presence.Type.unavailable)
        {
            removeAssociatedSsrc(participantJid);
            fireEvent(new JireconTaskEvent(
                JireconTaskEvent.Type.PARTICIPANT_LEFT));
        }
        // Otherwise we think that some new participant has joined the MUC.
        else
        {
            addAssociatedSsrc(participantJid, ssrcs);
            fireEvent(new JireconTaskEvent(
                JireconTaskEvent.Type.PARTICIPANT_CAME));
        }
    }
    
    /**
     * Create Jingle session-accept packet.
     * 
     * @param initIq is the session-init packet, we need it to create
     *            session-accept packet.
     * @param transportManager is used for creating transport packet extension.
     * @param srtpControlManager is used to set fingerprint.
     * @return Jingle session-accept packet.
     * @throws OperationFailedException if something failed and session-accept
     *             packet can not be created.
     */
    private JingleIQ createAcceptPacket(
        Map<MediaType, Map<MediaFormat, Byte>> formatAndPTs,
        Map<MediaType, Long> localSsrcs,
        Map<MediaType, IceUdpTransportPacketExtension> transportPEs,
        Map<MediaType, DtlsFingerprintPacketExtension> fingerprintPEs)
    {
        logger.info("createSessionAcceptPacket");
        List<ContentPacketExtension> contentPEs =
            new ArrayList<ContentPacketExtension>();

        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
                continue;

            // 1. Create DescriptionPE.
            RtpDescriptionPacketExtension descriptionPE =
                createDescriptionPacketExt(mediaType,
                    formatAndPTs.get(mediaType), localSsrcs.get(mediaType));

            // 2. Create TransportPE, put FingerprintPE into it.
            IceUdpTransportPacketExtension transportPE =
                transportPEs.get(mediaType);
            DtlsFingerprintPacketExtension fingerprintPE =
                fingerprintPEs.get(mediaType);
            transportPE.addChildExtension(fingerprintPE);

            // 3. Create Content packet extension with DescriptionPE and
            // TransportPE above.
            ContentPacketExtension contentPE =
                createContentPacketExtension(mediaType.toString(),
                    descriptionPE, transportPE);

            contentPEs.add(contentPE);
        }

        JingleIQ acceptJiq =
            JinglePacketFactory.createSessionAccept(localFullJid,
                remoteFullJid, sid, contentPEs);
//        acceptJiq.setInitiator(remoteFullJid);

        return acceptJiq;
    }
    
    /**
     * Create content packet extension in Jingle session-accept packet.
     * 
     * @param mediaType indicates the media type.
     * @param initIqContent is the related content packet extension of Jingle
     *            session-init packet.
     * @param transportManager is used for creating transport packet extension.
     * @param srtpControlManager is used for add fingerprint.
     * @return content packet extension.
     * @throws OperationFailedException if something failed, and the content
     *             packet extension can not be created.
     */
    private ContentPacketExtension createContentPacketExtension(
        String name,
        RtpDescriptionPacketExtension descriptionPE,
        IceUdpTransportPacketExtension transportPE)
    {
        logger.debug(this.getClass() + " createContentPacketExtension");
        
        ContentPacketExtension content = new ContentPacketExtension();
        content.setCreator(CreatorEnum.responder);
        content.setName(name);
        content.setSenders(SendersEnum.initiator);
        content.addChildExtension(descriptionPE);
        content.addChildExtension(transportPE);

        return content;
    }

    private RtpDescriptionPacketExtension createDescriptionPacketExt(
        MediaType mediaType, Map<MediaFormat, Byte> formatAndPayloadTypes,
        Long localSsrc)
    {
        RtpDescriptionPacketExtension description =
            new RtpDescriptionPacketExtension();
        
        // 1. Set media type.
        description.setMedia(mediaType.toString());
        // 2. Set local ssrc.
        description.setSsrc(localSsrc.toString());

        // 3. Set payload type id.
        for (Map.Entry<MediaFormat, Byte> e : formatAndPayloadTypes.entrySet())
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
            description.addPayloadType(payloadType);
        }

        // 4. Set source information.
        SourcePacketExtension sourcePacketExtension =
            new SourcePacketExtension();
        final String label = mediaType.toString();
        sourcePacketExtension.setSSRC(localSsrc);
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "cname", LibJitsi.getMediaService().getRtpCname()));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "msid", msLabel + " " + label));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "mslabel", msLabel));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "label", label));
        description.addChildExtension(sourcePacketExtension);
        
        return description;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addTaskEventListener(JireconTaskEventListener listener)
    {
        synchronized (listeners)
        {
            listeners.add(listener);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeTaskEventListener(JireconTaskEventListener listener)
    {
        synchronized (listeners)
        {
            listeners.remove(listener);
        }
    }

    /**
     * Fire a <tt>JireconTaskEvent</tt>, notify listeners we've made new
     * progress which they may interest in.
     * 
     * @param event
     */
    private void fireEvent(JireconTaskEvent event)
    {
        synchronized (listeners)
        {
            for (JireconTaskEventListener l : listeners)
                l.handleTaskEvent(event);
        }
    }

    /**
     * Send the XMPP packet to the packet listeners to handle.
     * 
     * @param packet is the packet that we've gotten.
     */
    private void handlePacket(Packet packet)
    {
        for (JireconSessionPacketListener l : packetListeners)
        {
            l.handlePacket(packet);
        }
    }

    /**
     * Add packet sending listener to connection. This method is used just for
     * debugging.
     */
    private void addPacketSendingListener()
    {
        connection.addPacketSendingListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                logger.info("--->: " + packet.toXML());
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

    /**
     * Add packet receiving listener to connection.
     * <p>
     * <strong>Warning:</strong> Packet will be ignored if its destination jid
     * is not equal with local jid.
     */
    private void addPacketReceivingListener()
    {
        connection.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                logger.info(packet.getClass() + "<---: " + packet.toXML());
                handlePacket(packet);
            }
        }, new PacketFilter()
        {
            @Override
            public boolean accept(Packet packet)
            {
                if (null != localFullJid
                    && !packet.getTo().equals(localFullJid))
                {
                    logger.fatal("packet failed: to " + packet.getTo()
                        + ", but we are " + localFullJid);
                    return false;
                }
                return true;
            }
        });
    }

    /**
     * The packet listener interface in Observer pattern. Anyone who wants to
     * handle packet need to implement it.
     * 
     * @author lishunyang
     * 
     */
    private interface JireconSessionPacketListener
    {
        /**
         * Handle packet.
         * 
         * @param packet is the packet that we've gotten.
         */
        public void handlePacket(Packet packet);
    }

    /**
     * Register a packet listener to this <tt>JireconSessionImpl</tt>.
     * 
     * @param listener is the one that you want to add.
     */
    private void addPacketListener(JireconSessionPacketListener listener)
    {
        packetListeners.add(listener);
    }

    /**
     * Remove a packet listener from this <tt>JireconSessionImpl</tt>.
     * 
     * @param listener is the one that you want to remove.
     */
    private void removePacketListener(JireconSessionPacketListener listener)
    {
        packetListeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Map<MediaType, Long>> getAssociatedSsrcs()
    {
        return associatedSsrcs;
    }

    /**
     * Add new participant's associated ssrc, if this participant's record has
     * existed, it will override it.
     * 
     * @param jid
     * @param ssrcs
     */
    private void addAssociatedSsrc(String jid, Map<MediaType, Long> ssrcs)
    {
        synchronized (associatedSsrcs)
        {
            associatedSsrcs.remove(jid);
            associatedSsrcs.put(jid, ssrcs);
        }
    }

    /**
     * Remove a participant's associated ssrc, if this participant's record
     * hasn't existed, it will ignore it.
     * 
     * @param jid
     */
    private void removeAssociatedSsrc(String jid)
    {
        synchronized (associatedSsrcs)
        {
            associatedSsrcs.remove(jid);
        }
    }
}
