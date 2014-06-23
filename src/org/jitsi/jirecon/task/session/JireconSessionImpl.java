/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.session;

import java.io.IOException;
import java.util.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.SourcePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.*;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.util.Logger;

import org.jitsi.jirecon.dtlscontrol.SrtpControlManager;
import org.jitsi.jirecon.extension.MediaExtension;
import org.jitsi.jirecon.task.JireconTaskSharingInfo;
import org.jitsi.jirecon.transport.JireconTransportManager;
import org.jitsi.jirecon.utils.JinglePacketParser;
import org.jitsi.service.configuration.ConfigurationService;
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
     * The instance of a <tt>MultiUserChat</tt>. <tt>JireconSessionImpl</tt>
     * will join it as the first step.
     */
    private MultiUserChat muc;

    /**
     * The <tt>JireconTaskSharingInfo</tt> is used to share some necessary
     * information between <tt>JireconSessionImpl</tt> and other classes.
     */
    private JireconTaskSharingInfo sharingInfo;

    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger
        .getLogger(JireconSessionImpl.class);

    // TODO: This should be moved to upper class, add a parameter when
    // creating JireconSessionImpl
    /**
     * The hash nick name item key in configuration file.
     */
    private final static String NICK_KEY = "JIRECON_NICKNAME";

    /**
     * The nick name which will be showed up when joining a muc.
     */
    private String NICK = "default";

    /**
     * The list of <tt>JireconSessionPacketListener</tt> which is used for
     * handling kinds of XMPP packet.
     */
    private List<JireconSessionPacketListener> packetListeners =
        new ArrayList<JireconSessionPacketListener>();

    /**
     * Indicate how many ms <tt>JireconSessionImpl</tt> will wait for a XMPP
     * packet. For instance, wait for a session-init packet after joining muc.
     */
    private final long MAX_WAIT_TIME = 20000;

    // TODO: I think mucJid should be moved into connect method.
    /**
     * Construction method of <tt>JireconSessionImpl</tt>.
     * <p>
     * <strong>Warning:</strong> LibJitsi must be started before calling this
     * method.
     * 
     * @param connection is used for send/receive XMPP packet.
     * @param mucJid indicate which MUC we will connect.
     * @param sharingInfo includes some necessary information, it is shared with
     *            other classes.
     */
    public JireconSessionImpl(XMPPConnection connection, String mucJid,
        JireconTaskSharingInfo sharingInfo)
    {
        logger.setLevelDebug();

        this.sharingInfo = sharingInfo;
        ConfigurationService configuration = LibJitsi.getConfigurationService();
        this.NICK = configuration.getString(NICK_KEY);
        this.connection = connection;
        this.sharingInfo.setMucJid(mucJid);

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
        SrtpControlManager srtpControlManager)
        throws XMPPException,
        OperationFailedException,
        IOException
    {
        joinMUC();
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
     * Join a specified Multi-User-Chat.
     * 
     * @throws XMPPException if failed to join MUC.
     */
    private void joinMUC() throws XMPPException
    {
        logger.info("joinMUC");

        muc = new MultiUserChat(connection, sharingInfo.getMucJid());
        muc.join(NICK);
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
     */
    private void sendAccpetPacket(JingleIQ initIq,
        JireconTransportManager transportManager,
        SrtpControlManager srtpControlManager)
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
            sharingInfo.getLocalJid(), sharingInfo.getRemoteJid(),
            sharingInfo.getSid(), reason, reasonText));
    }

    // TODO: This is wired, I should change it.
    /**
     * Record some session information according Jingle session-init packet.
     * 
     * @param initJiq is the Jingle session-init packet.
     */
    private void recordSessionInfo(JingleIQ initJiq)
    {
        sharingInfo.setLocalJid(initJiq.getTo());
        sharingInfo.setRemoteJid(initJiq.getFrom());
        sharingInfo.setSid(initJiq.getSID());
        sharingInfo.setFormatAndPayloadTypes(JinglePacketParser
            .getFormatAndDynamicPTs(initJiq));
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
    private JingleIQ waitForInitPacket() throws OperationFailedException
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
                "Could not get session-init packet",
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
    private void waitForAckPacket() throws OperationFailedException
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
            Map<MediaType, String> participantSsrcs =
                new HashMap<MediaType, String>();
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
                    participantSsrcs.put(mediaType, ssrc);
                }
            }
            sharingInfo.setParticipantSsrcs(participantJid, participantSsrcs);
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
     */
    private JingleIQ createAcceptPacket(JingleIQ initIq,
        JireconTransportManager transportManager,
        SrtpControlManager srtpControlManager)
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
            JinglePacketFactory.createSessionAccept(sharingInfo.getLocalJid(),
                sharingInfo.getRemoteJid(), sharingInfo.getSid(), contents);

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
     */
    private ContentPacketExtension createContentPacketExtension(
        MediaType mediaType, ContentPacketExtension initIqContent,
        JireconTransportManager transportManager,
        SrtpControlManager srtpControlManager)
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
        for (Map.Entry<MediaFormat, Byte> e : sharingInfo
            .getFormatAndPayloadTypes(mediaType).entrySet())
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
        description.setSsrc(sharingInfo.getLocalSsrc(mediaType).toString());

        sourcePacketExtension.setSSRC(sharingInfo.getLocalSsrc(mediaType));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "cname", LibJitsi.getMediaService().getRtpCname()));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "msid", sharingInfo.getMsid(mediaType)));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "mslabel", sharingInfo.getMsLabel()));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "label", sharingInfo.getLabel(mediaType)));
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
                // System.out.println("--->: " + packet.toXML());
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
                // logger.debug(packet.getClass() + "<---: " + packet.toXML());
                // System.out.println(packet.getClass() + "<---: "
                // + packet.toXML());
                 handlePacket(packet);
            }
        }, new PacketFilter()
        {
            @Override
            public boolean accept(Packet packet)
            {
                if (null != sharingInfo.getLocalJid()
                    && !packet.getTo().equals(sharingInfo.getLocalJid()))
                {
                    logger.fatal("packet failed: to " + packet.getTo()
                        + ", but we are " + sharingInfo.getLocalJid());
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
}
