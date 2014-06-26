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

import org.jitsi.jirecon.dtlscontrol.SrtpControlManager;
import org.jitsi.jirecon.extension.MediaExtension;
import org.jitsi.jirecon.task.*;
import org.jitsi.jirecon.transport.JireconTransportManager;
import org.jitsi.jirecon.utils.JinglePacketParser;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.packet.MUCUser;

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
     * Map between <tt>MediaFormat</tt> and dynamic payload type id which is
     * used for making <tt>JingleIQ</tt>.
     */
    private Map<MediaFormat, Byte> formatAndPayloadTypes;

    /**
     * Map between <tt>MediaType</tt> and local ssrc which is used for making
     * <tt>JingleIQ</tt>.
     */
    private Map<MediaType, Long> localSsrcs = new HashMap<MediaType, Long>();

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
    private Map<String, List<String>> associatedSsrcs =
        new HashMap<String, List<String>>();

    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger
        .getLogger(JireconSessionImpl.class);

    /**
     * The list of <tt>JireconSessionPacketListener</tt> which is used for
     * handling kinds of XMPP packet.
     */
    private List<JireconSessionPacketListener> packetListeners =
        new ArrayList<JireconSessionPacketListener>();

    /**
     * Maximum wait time(microsecond).
     */
    private final int MAX_WAIT_TIME = 5000;

    /**
     * Minimum wait time(microsecond).
     */
    private final int MIN_WAIT_TIME = 1000;

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
    public JingleIQ connect(JireconTransportManager transportManager,
        SrtpControlManager srtpControlManager, String mucJid, String nickname)
        throws OperationFailedException
    {
        joinMUC(mucJid, nickname);
        JingleIQ initIq = waitForInitPacket();
        recordSessionInfo(initIq);
        sendAck(initIq);
        sendAccpetPacket(initIq, transportManager, srtpControlManager);
        waitForAckPacket();

        return initIq;
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
    public Map<MediaFormat, Byte> getFormatAndPayloadType()
    {
        return formatAndPayloadTypes;
    }

    /**
     * Join a Multi-User-Chat of specified MUC jid.
     * 
     * @param mucJid The specified MUC jid.
     * @param nickname The name in MUC.
     * @throws OperationFailedException if failed to join MUC.
     */
    private void joinMUC(String mucJid, String nickname)
        throws OperationFailedException
    {
        logger.info("joinMUC");

        muc = new MultiUserChat(connection, mucJid);
        try
        {
            muc.join(nickname);
        }
        catch (XMPPException e)
        {
            throw new OperationFailedException("Could not join MUC, "
                + e.getMessage(), OperationFailedException.GENERAL_ERROR);
        }
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
     * Send Jingle session-accept packet to the remote peer.
     * 
     * @param initIq is the session-init packet that we've gotten.
     * @param transportManager is used for ICE connectivity establishment.
     * @param srtpControlManager is used for SRTP transform.
     * @throws OperationFailedException if something failed and session-accept
     *             packet can't be sent.
     */
    private void sendAccpetPacket(JingleIQ initIq,
        JireconTransportManager transportManager,
        SrtpControlManager srtpControlManager) 
            throws OperationFailedException
    {
        logger.info("sendAcceptPacket");

        JingleIQ acceptPacket =
            createAcceptPacket(initIq, transportManager, srtpControlManager);
        connection.sendPacket(acceptPacket);
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
        formatAndPayloadTypes =
            JinglePacketParser.getFormatAndDynamicPTs(initJiq);
    }

    /**
     * Wait for Jingle session-init packet after join the MUC.
     * <p>
     * <strong>Warning:</strong> This method will block for at most
     * <tt>MAX_WAIT_TIME</tt> ms if there isn't init packet.
     * 
     * @return Jingle session-init packet that we get.
     * @throws OperationFailedException if the method time out.
     */
    private JingleIQ waitForInitPacket() 
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

        return resultList.get(0);
    }

    /**
     * Wait for ack packet.
     * <p>
     * <strong>Warning:</strong> This method will block for at most
     * <tt>MAX_WAIT_TIME</tt> ms if there isn't ack packet.
     * 
     * @throws OperationFailedException if the method time out.
     */
    private void waitForAckPacket() 
        throws OperationFailedException
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
                    if (packet.toXML().indexOf("type=\"result\"") >= 0)
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
            throw new OperationFailedException("Could not get ack packet",
                OperationFailedException.GENERAL_ERROR);
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
        String participantJid = userExt.getItem().getJid();
        if (null != participantJid && null != packetExt)
        {
            MediaExtension mediaExt = (MediaExtension) packetExt;
            List<String> ssrcs = new ArrayList<String>();
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
                String ssrc = mediaExt.getSsrc(mediaType.toString());
                if (direction == MediaDirection.SENDONLY
                    || direction == MediaDirection.SENDRECV)
                {
                    ssrcs.add(ssrc);
                }
            }
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
    private JingleIQ createAcceptPacket(JingleIQ initIq,
        JireconTransportManager transportManager,
        SrtpControlManager srtpControlManager) 
            throws OperationFailedException
    {
        logger.info("createSessionAcceptPacket");
        final List<ContentPacketExtension> contents =
            new ArrayList<ContentPacketExtension>();
        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
                continue;

            ContentPacketExtension initIqContent =
                JinglePacketParser.getContentPacketExt(initIq, mediaType);
            contents.add(createContentPacketExtension(mediaType, initIqContent,
                transportManager, srtpControlManager));
        }

        JingleIQ acceptJiq =
            JinglePacketFactory.createSessionAccept(localFullJid,
                remoteFullJid, sid, contents);

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
        MediaType mediaType, ContentPacketExtension initIqContent,
        JireconTransportManager transportManager,
        SrtpControlManager srtpControlManager) 
            throws OperationFailedException
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

        // harvest payload types information.
        List<PayloadTypePacketExtension> payloadTypes =
            new ArrayList<PayloadTypePacketExtension>();
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
            payloadTypes.add(payloadType);
        }

        // Description packet extension stuff
        RtpDescriptionPacketExtension description =
            new RtpDescriptionPacketExtension();
        description.setMedia(mediaType.toString());
        for (PayloadTypePacketExtension p : payloadTypes)
        {
            description.addPayloadType(p);
        }
        SourcePacketExtension sourcePacketExtension =
            new SourcePacketExtension();

        int sumWaitTime = 0;
        while (sumWaitTime <= MAX_WAIT_TIME)
        {
            try
            {
                synchronized (localSsrcs)
                {
                    if (localSsrcs.containsKey(mediaType))
                        break;
                }
                logger
                    .info("Local ssrcs not found, wait for a while. Already sleep for "
                        + sumWaitTime / 1000 + " s.");
                sumWaitTime += MIN_WAIT_TIME;
                Thread.sleep(MIN_WAIT_TIME);
            }
            catch (InterruptedException e1)
            {
                e1.printStackTrace();
            }
        }
        if (!localSsrcs.containsKey(mediaType))
        {
            throw new OperationFailedException(
                "Failed to create content packet extension, local ssrc unknown.",
                OperationFailedException.GENERAL_ERROR);
        }

        description.setSsrc(localSsrcs.get(mediaType).toString());

        final String label = mediaType.toString();
        sourcePacketExtension.setSSRC(localSsrcs.get(mediaType));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "cname", LibJitsi.getMediaService().getRtpCname()));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "msid", msLabel + " " + label));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "mslabel", msLabel));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "label", label));
        description.addChildExtension(sourcePacketExtension);

        ContentPacketExtension content = new ContentPacketExtension();
        content.setCreator(CreatorEnum.responder);
        content.setName(initIqContent.getName());
        content.setSenders(SendersEnum.initiator);
        content.addChildExtension(description);
        content.addChildExtension(transportPE);

        return content;
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
     * {@inheritDoc}
     */
    @Override
    public void setLocalSsrcs(Map<MediaType, Long> ssrcs)
    {
        synchronized (localSsrcs)
        {
            localSsrcs = ssrcs;
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
                // logger.debug("--->: " + packet.toXML());
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

    // TODO: What if a participant leave the MUC, is there any terminate packet?
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
                // logger.debug(packet.getClass() + "<---: " + packet.toXML());
                System.out.println("<---: " + packet.toXML());
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
    public Map<String, List<String>> getAssociatedSsrcs()
    {
        return associatedSsrcs;
    }

    /**
     * Add new participant's associated ssrc. If this participant's record has
     * existed, it will override it.
     */
    private void addAssociatedSsrc(String jid, List<String> ssrcs)
    {
        synchronized (associatedSsrcs)
        {
            associatedSsrcs.remove(jid);
            associatedSsrcs.put(jid, ssrcs);
        }
    }
}
