/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.SourcePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.*;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.util.Logger;

import org.jitsi.jirecon.dtlscontrol.JireconSrtpControlManager;
import org.jitsi.jirecon.extension.MediaExtension;
import org.jitsi.jirecon.recorder.JireconRecorderInfo;
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
 * This class is responsible for managing a Jingle session and extract some
 * information which could be used by others.
 * 
 * @author lishunyang
 * 
 */
public class JireconSessionImpl
    implements JireconSession
{
    private XMPPConnection connection;

    private MultiUserChat conference;

    private JireconSessionInfo sessionInfo;

    private JireconRecorderInfo recorderInfo;

    private JireconSessionState state = JireconSessionState.INIT;

    private static final Logger logger = Logger
        .getLogger(JireconSessionImpl.class);

    private final static String NICK_KEY = "JIRECON_NICKNAME";

    private String NICK = "default";

    private String SAVING_DIR;

    private List<JireconSessionPacketListener> packetListeners =
        new ArrayList<JireconSessionPacketListener>();

    public JireconSessionImpl(XMPPConnection connection, String conferenceJid,
        String SAVING_DIR, JireconSessionInfo sessionInfo,
        JireconRecorderInfo recorderInfo)
    {
        logger.setLevelDebug();
        this.SAVING_DIR = SAVING_DIR;
        this.sessionInfo = sessionInfo;
        this.recorderInfo = recorderInfo;
        ConfigurationService configuration = LibJitsi.getConfigurationService();
        this.NICK = configuration.getString(NICK_KEY);
        this.connection = connection;
        this.sessionInfo.setMucJid(conferenceJid);

        addPacketSendingListener();
        addPacketReceivingListener();

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

    @Override
    public JingleIQ connect(JireconSessionInfo sessionInfo,
        JireconRecorderInfo recorderInfo,
        JireconTransportManager transportManager,
        JireconSrtpControlManager srtpControlManager)
        throws XMPPException,
        OperationFailedException,
        IOException
    {
        joinMUC();
        JingleIQ initIq = waitForInitPacket();
        recordSessionInfo(initIq);
        sendAck(initIq);
        sendAccpetPacket(initIq, sessionInfo, recorderInfo, transportManager,
            srtpControlManager);
        waitForAckPacket();

        return initIq;
    }

    @Override
    public void disconnect(Reason reason, String reasonText)
    {
        try
        {
            sendByePacket(reason, reasonText);
        }
        catch (OperationFailedException e)
        {
            e.printStackTrace();
        }

        try
        {
            leaveMUC();
        }
        catch (OperationFailedException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public JireconSessionInfo getSessionInfo()
    {
        return sessionInfo;
    }

    private void joinMUC() throws XMPPException, OperationFailedException
    {
        logger.info("joinMUC");
        if (!readyTo(JireconSessionEvent.JOIN_MUC))
        {
            throw new OperationFailedException(
                "Could not join conference, other reason.",
                OperationFailedException.GENERAL_ERROR);
        }

        conference = new MultiUserChat(connection, sessionInfo.getMucJid());
        conference.join(NICK);
        updateState(JireconSessionEvent.JOIN_MUC);
    }

    private void leaveMUC() throws OperationFailedException
    {
        logger.info("leaveMUC");
        if (!readyTo(JireconSessionEvent.LEAVE_MUC))
        {
            throw new OperationFailedException(
                "Could not leave conference, not in conference.",
                OperationFailedException.GENERAL_ERROR);
        }

        if (null != conference)
        {
            conference.leave();
        }
        updateState(JireconSessionEvent.LEAVE_MUC);
    }

    private void sendAccpetPacket(JingleIQ initIq,
        JireconSessionInfo sessionInfo, JireconRecorderInfo recorderInfo,
        JireconTransportManager transportManager,
        JireconSrtpControlManager srtpControlManager)
        throws OperationFailedException
    {
        logger.info("sendAcceptPacket");
        if (!readyTo(JireconSessionEvent.SEND_SESSION_ACCEPT))
        {
            throw new OperationFailedException(
                "Could not send session-accept, haven't gotten session-init.",
                OperationFailedException.GENERAL_ERROR);
        }

        JingleIQ acceptPacket =
            createAcceptPacket(initIq, sessionInfo, recorderInfo,
                transportManager, srtpControlManager);
        connection.sendPacket(acceptPacket);
        updateState(JireconSessionEvent.SEND_SESSION_ACCEPT);
    }

    private void sendAck(JingleIQ jiq)
    {
        logger.info("sendAck");
        connection.sendPacket(IQ.createResultIQ(jiq));
    }

    private void sendByePacket(Reason reason, String reasonText)
        throws OperationFailedException
    {
        logger.info("sendByePacket");
        if (!readyTo(JireconSessionEvent.SEND_SESSION_TERMINATE))
        {
            throw new OperationFailedException(
                "Could not send session-terminate, session hasn't been built.",
                OperationFailedException.GENERAL_ERROR);
        }

        connection.sendPacket(JinglePacketFactory.createSessionTerminate(
            sessionInfo.getLocalJid(), sessionInfo.getRemoteJid(),
            sessionInfo.getSid(), reason, reasonText));
        updateState(JireconSessionEvent.SEND_SESSION_TERMINATE);
    }

    private void recordSessionInfo(JingleIQ jiq)
    {
        sessionInfo.setLocalJid(jiq.getTo());
        sessionInfo.setRemoteJid(jiq.getFrom());
        sessionInfo.setSid(jiq.getSID());
        sessionInfo.setFormatAndPayloadTypes(JinglePacketParser
            .getFormatAndDynamicPTs(jiq));
    }

    private JingleIQ waitForInitPacket() throws OperationFailedException
    {
        logger.info("waitForInitPacket");
        if (!readyTo(JireconSessionEvent.WAIT_SESSION_INIT))
        {
            throw new OperationFailedException(
                "Could not wait for session-init, hasn't joined conference.",
                OperationFailedException.GENERAL_ERROR);
        }

        final List<JingleIQ> resultList = new ArrayList<JingleIQ>();
        final Object waitForInitPacketSyncRoot = new Object();
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
                    waitForInitPacketSyncRoot.wait();
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

        updateState(JireconSessionEvent.WAIT_SESSION_INIT);
        return resultList.get(0);
    }

    private void waitForAckPacket() throws OperationFailedException
    {
        logger.info("waitForAckPacket");
        if (!readyTo(JireconSessionEvent.WAIT_SESSION_ACK))
        {
            throw new OperationFailedException(
                "Could not wait for session-ack, hasn't sent session-init.",
                OperationFailedException.GENERAL_ERROR);
        }

        final List<Packet> resultList = new ArrayList<Packet>();
        final Object waitForAckPacketSyncRoot = new Object();
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
                    waitForAckPacketSyncRoot.wait();
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

        updateState(JireconSessionEvent.WAIT_SESSION_ACK);
    }

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
            sessionInfo.setParticipantSsrcs(participantJid, participantSsrcs);
        }
    }

    private JingleIQ createAcceptPacket(JingleIQ initIq,
        JireconSessionInfo sessionInfo, JireconRecorderInfo recorderInfo,
        JireconTransportManager transportManager,
        JireconSrtpControlManager srtpControlManager)
    {
        logger.info("createSessionAcceptPacket");
        final List<ContentPacketExtension> contents =
            new ArrayList<ContentPacketExtension>();
        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
            {
                continue;
            }

            ContentPacketExtension initIqContent =
                JinglePacketParser.getContentPacketExt(initIq, mediaType);
            contents
                .add(createContentPacketExtension(mediaType, initIqContent,
                    sessionInfo, recorderInfo, transportManager,
                    srtpControlManager));
        }

        JingleIQ acceptJiq =
            JinglePacketFactory.createSessionAccept(sessionInfo.getLocalJid(),
                sessionInfo.getRemoteJid(), sessionInfo.getSid(), contents);

        return acceptJiq;
    }

    private ContentPacketExtension createContentPacketExtension(
        MediaType mediaType, ContentPacketExtension initIqContent,
        JireconSessionInfo sessionInfo, JireconRecorderInfo recorderInfo,
        JireconTransportManager transportManager,
        JireconSrtpControlManager srtpControlManager)
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
        for (Map.Entry<MediaFormat, Byte> e : sessionInfo
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
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "cname", LibJitsi.getMediaService().getRtpCname()));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "msid", recorderInfo.getMsid(mediaType)));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "mslabel", recorderInfo.getMsLabel()));
        sourcePacketExtension.addChildExtension(new ParameterPacketExtension(
            "label", recorderInfo.getLabel(mediaType)));
        description.addChildExtension(sourcePacketExtension);

        ContentPacketExtension content = new ContentPacketExtension();
        content.setCreator(CreatorEnum.responder);
        content.setName(initIqContent.getName());
        content.setSenders(SendersEnum.initiator);
        content.addChildExtension(description);
        content.addChildExtension(transportPE);

        return content;
    }

    private void handlePacket(Packet packet)
    {
        for (JireconSessionPacketListener l : packetListeners)
        {
            l.handlePacket(packet);
        }
    }

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

    private void addPacketReceivingListener()
    {
        connection.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                // logger.debug(packet.getClass() + "<---: " + packet.toXML());
                System.out.println(packet.getClass() + "<---: "
                    + packet.toXML());
                handlePacket(packet);
            }
        }, new PacketFilter()
        {
            @Override
            public boolean accept(Packet packet)
            {
                if (null != sessionInfo.getLocalJid()
                    && !packet.getTo().equals(sessionInfo.getLocalJid()))
                {
                    logger.fatal("packet failed: to " + packet.getTo()
                        + ", but we are " + sessionInfo.getLocalJid());
                    return false;
                }
                return true;
            }
        });
    }

    public void writeMetaData() throws IOException
    {
        // TODO
        new File(SAVING_DIR).mkdir();
        File metaFile = new File(SAVING_DIR + "/meta");
        if (!metaFile.createNewFile())
            throw new IOException("File exists or cannot be created: "
                + metaFile);

        if (!metaFile.canWrite())
            throw new IOException("Cannot write to file: " + metaFile);

        FileWriter metaFileWriter = null;
        metaFileWriter = new FileWriter(metaFile, false);

        try
        {
            Map<String, Map<MediaType, String>> participantsSscrs =
                sessionInfo.getParticipantsSsrcs();
            if (null != participantsSscrs)
            {
                int i = 0;
                for (Entry<String, Map<MediaType, String>> e : participantsSscrs
                    .entrySet())
                {
                    metaFileWriter.write("participant_" + i++ + ": "
                        + e.getKey() + "\n");
                    for (Entry<MediaType, String> el : e.getValue().entrySet())
                    {
                        metaFileWriter.write("\t" + el.getKey() + ": "
                            + el.getValue() + "\n");
                    }
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();

        }
        finally
        {
            metaFileWriter.close();
        }
    }

    private boolean readyTo(JireconSessionEvent evt)
    {
        switch (evt)
        {
        case JOIN_MUC:
            if (JireconSessionState.INIT != state)
                return false;
            break;
        case LEAVE_MUC:
            if (JireconSessionState.IN_CONFERENCE != state)
                return false;
            break;
        case SEND_SESSION_ACCEPT:
            if (JireconSessionState.GOT_SESSION_INIT != state)
                return false;
            break;
        case SEND_SESSION_TERMINATE:
            if (JireconSessionState.CONNECTED != state)
                return false;
            break;
        case WAIT_SESSION_ACK:
            if (JireconSessionState.SENT_SESSION_ACCEPT != state)
                return false;
            break;
        case WAIT_SESSION_INIT:
            if (JireconSessionState.IN_CONFERENCE != state)
                return false;
            break;
        }
        return true;
    }

    private void updateState(JireconSessionEvent evt)
    {
        switch (evt)
        {
        case JOIN_MUC:
            state = JireconSessionState.IN_CONFERENCE;
            break;
        case LEAVE_MUC:
            state = JireconSessionState.INIT;
            break;
        case SEND_SESSION_ACCEPT:
            state = JireconSessionState.SENT_SESSION_ACCEPT;
            break;
        case SEND_SESSION_TERMINATE:
            state = JireconSessionState.IN_CONFERENCE;
            break;
        case WAIT_SESSION_ACK:
            state = JireconSessionState.CONNECTED;
            break;
        case WAIT_SESSION_INIT:
            state = JireconSessionState.GOT_SESSION_INIT;
            break;
        }
    }

    private enum JireconSessionEvent
    {
        JOIN_MUC,
        LEAVE_MUC,
        SEND_SESSION_ACCEPT,
        SEND_SESSION_TERMINATE,
        WAIT_SESSION_INIT,
        WAIT_SESSION_ACK,
    }

    private enum JireconSessionState
    {
        INIT, IN_CONFERENCE, GOT_SESSION_INIT, SENT_SESSION_ACCEPT, CONNECTED,
    }

    private interface JireconSessionPacketListener
    {
        public void handlePacket(Packet packet);
    }

    private void addPacketListener(JireconSessionPacketListener listener)
    {
        packetListeners.add(listener);
    }

    private void removePacketListener(JireconSessionPacketListener listener)
    {
        packetListeners.remove(listener);
    }
}
