/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.datachannel;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.media.rtp.SessionAddress;

import net.java.sip.communicator.util.Logger;

import org.jitsi.impl.neomedia.RTPConnectorUDPImpl;
import org.jitsi.impl.neomedia.RawPacket;
import org.jitsi.impl.neomedia.transform.dtls.DtlsPacketTransformer;
import org.jitsi.impl.neomedia.transform.dtls.DtlsTransformEngine;
import org.jitsi.sctp4j.NetworkLink;
import org.jitsi.sctp4j.Sctp;
import org.jitsi.sctp4j.SctpDataCallback;
import org.jitsi.sctp4j.SctpNotification;
import org.jitsi.sctp4j.SctpSocket;
import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.DtlsControl;
import org.jitsi.service.neomedia.MediaStreamTarget;
import org.jitsi.service.neomedia.StreamConnector;
import org.jitsi.service.packetlogging.PacketLoggingService;
import org.jitsi.util.ExecutorUtils;

public class WebRtcDataStreamManager
{
    /**
     * Message type used to acknowledge WebRTC data channel allocation on SCTP
     * stream ID on which <tt>MSG_OPEN_CHANNEL</tt> message arrives.
     */
    private final static int MSG_CHANNEL_ACK = 0x2;

    private static final byte[] MSG_CHANNEL_ACK_BYTES = new byte[]
    { MSG_CHANNEL_ACK };

    /**
     * Message with this type sent over control PPID in order to open new WebRTC
     * data channel on SCTP stream ID that this message is sent.
     */
    private final static int MSG_OPEN_CHANNEL = 0x3;

    /**
     * The <tt>String</tt> value of the <tt>Protocol</tt> field of the
     * <tt>DATA_CHANNEL_OPEN</tt> message.
     */
    private static final String WEBRTC_DATA_CHANNEL_PROTOCOL =
        "http://jitsi.org/protocols/colibri";

    private static final Logger logger = Logger
        .getLogger(WebRtcDataStreamManager.class);

    /**
     * Indicates whether the STCP association is ready and has not been ended by
     * a subsequent state change.
     */
    private boolean assocIsUp = false;

    /**
     * The indicator which determines whether an SCTP peer address has been
     * confirmed.
     */
    private boolean peerAddrIsConfirmed = false;

    /**
     * <tt>SctpSocket</tt> used for sending SCTP data.
     */
    private SctpSocket sctpSocket;

    private String endpointId;

    private Map<Integer, WebRtcDataStream> channels =
        new HashMap<Integer, WebRtcDataStream>();
    
    private SctpPacketReceiver packetDispatcher = new SctpPacketReceiver();

    public WebRtcDataStreamManager(String endpointId)
    {
        this.endpointId = endpointId;
    }
    
    public void runAsServer()
    {
        /*
         * TODO: Create SctpSocket and accept.
         */
    }
    
    public void runAsClient()
    {
        /*
         * TODO: Create SctpSocket and connect.
         */
    }

    /**
     * Handles control packet.
     * 
     * @param data raw packet data that arrived on control PPID.
     * @param sid SCTP stream id on which the data has arrived.
     */
    private synchronized void onCtrlPacket(byte[] data, int sid)
        throws IOException
    {
        System.out.print("Control Packet");
        for (byte b : data)
        {
            System.out.printf("%03d ", b);
        }
        System.out.println();

        ByteBuffer buffer = ByteBuffer.wrap(data);
        int messageType = /* 1 byte unsigned integer */0xFF & buffer.get();

        if (messageType == MSG_CHANNEL_ACK)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("ACK received SID: " + sid);
            }
            // Open channel ACK
            WebRtcDataStream channel = channels.get(sid);
            if (channel != null)
            {
                // Ack check prevents from firing multiple notifications
                // if we get more than one ACKs (by mistake/bug).
                if (!channel.isAcknowledged())
                {
                    channel.ackReceived();
                }
                else
                {
                    logger.warn("Redundant ACK received for SID: " + sid);
                }
            }
            else
            {
                logger.error("No channel exists on sid: " + sid);
            }
        }
        else if (messageType == MSG_OPEN_CHANNEL)
        {
            int channelType = /* 1 byte unsigned integer */0xFF & buffer.get();
            int priority =
                /* 2 bytes unsigned integer */0xFFFF & buffer.getShort();
            long reliability =
                /* 4 bytes unsigned integer */0xFFFFFFFFL & buffer.getInt();
            int labelLength =
                /* 2 bytes unsigned integer */0xFFFF & buffer.getShort();
            int protocolLength =
                /* 2 bytes unsigned integer */0xFFFF & buffer.getShort();
            String label;
            String protocol;

            if (labelLength == 0)
            {
                label = "";
            }
            else
            {
                byte[] labelBytes = new byte[labelLength];

                buffer.get(labelBytes);
                label = new String(labelBytes, "UTF-8");
            }
            if (protocolLength == 0)
            {
                protocol = "";
            }
            else
            {
                byte[] protocolBytes = new byte[protocolLength];

                buffer.get(protocolBytes);
                protocol = new String(protocolBytes, "UTF-8");
            }

            if (logger.isDebugEnabled())
            {
                logger.debug("!!! " + endpointId
                    + " data channel open request on SID: " + sid + " type: "
                    + channelType + " prio: " + priority + " reliab: "
                    + reliability + " label: " + label + " proto: " + protocol);
            }

            if (channels.containsKey(sid))
            {
                logger.error("Channel on sid: " + sid + " already exists");
            }

            WebRtcDataStream newChannel =
                new WebRtcDataStream(sctpSocket, sid, label, true);
            channels.put(sid, newChannel);

            sendOpenChannelAck(sid);

            /*
             * TODO: Should we notify someone that a data channel has been built?
             */
        }
        else
        {
            logger.error("Unexpected ctrl msg type: " + messageType);
        }
    }

    /**
     * Returns <tt>true</tt> if this <tt>SctpConnection</tt> is connected to
     * other peer and operational.
     * 
     * @return <tt>true</tt> if this <tt>SctpConnection</tt> is connected to
     *         other peer and operational.
     */
    public boolean isReady()
    {
        return assocIsUp && peerAddrIsConfirmed;
    }

    private synchronized WebRtcDataStream getChannel(int sid)
    {
        WebRtcDataStream channel = null;

        channel = channels.get(sid);
        if (null == channel)
        {
            logger.error("No channel found for sid: " + sid);
        }

        return channel;
    }

    private class SctpPacketReceiver
        implements SctpDataCallback, SctpSocket.NotificationListener
    {
        @Override
        public void onSctpPacket(byte[] data, int sid, int ssn, int tsn,
            long ppid, int context, int flags)
        {
            if (WebRtcDataStream.WEB_RTC_PPID_CTRL == ppid)
            {
                // Channel control PPID
                try
                {
                    onCtrlPacket(data, sid);
                }
                catch (IOException e)
                {
                    logger.error("IOException when processing ctrl packet", e);
                    e.printStackTrace();
                }
            }
            else if (WebRtcDataStream.WEB_RTC_PPID_STRING == ppid)
            {
                WebRtcDataStream channel = getChannel(sid);

                if (null == channel)
                    return;

                // WebRTC String
                String str;
                String charsetName = "UTF-8";

                try
                {
                    str = new String(data, charsetName);
                }
                catch (UnsupportedEncodingException uee)
                {
                    logger.error("Unsupported charset encoding/name "
                        + charsetName, uee);
                    str = null;
                }
                channel.onStringMsg(str);
            }
            else if (WebRtcDataStream.WEB_RTC_PPID_BIN == ppid)
            {
                WebRtcDataStream channel = getChannel(sid);

                if (null == channel)
                    return;

                // WebRTC Binary
                channel.onBinaryMsg(data);
            }
            else
            {
                logger.warn("Got message on unsupported PPID: " + ppid);
            }
        }

        @Override
        public void onSctpNotification(SctpSocket socket,
            SctpNotification notification)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("socket=" + socket + "; notification="
                    + notification);
            }
            switch (notification.sn_type)
            {
            case SctpNotification.SCTP_ASSOC_CHANGE:
                SctpNotification.AssociationChange assocChange =
                    (SctpNotification.AssociationChange) notification;

                switch (assocChange.state)
                {
                case SctpNotification.AssociationChange.SCTP_COMM_UP:
                    if (!assocIsUp)
                    {
                        assocIsUp = true;
                    }
                    break;

                case SctpNotification.AssociationChange.SCTP_COMM_LOST:
                    break;
                case SctpNotification.AssociationChange.SCTP_SHUTDOWN_COMP:
                    break;
                case SctpNotification.AssociationChange.SCTP_CANT_STR_ASSOC:
                    sctpSocket.close();
                    break;
                }
                break;

            case SctpNotification.SCTP_PEER_ADDR_CHANGE:
                SctpNotification.PeerAddressChange peerAddrChange =
                    (SctpNotification.PeerAddressChange) notification;

                switch (peerAddrChange.state)
                {
                case SctpNotification.PeerAddressChange.SCTP_ADDR_AVAILABLE:
                case SctpNotification.PeerAddressChange.SCTP_ADDR_CONFIRMED:
                    if (!peerAddrIsConfirmed)
                    {
                        peerAddrIsConfirmed = true;
                    }
                    break;
                }
                break;
            }
        }

    }

    /**
     * Opens new WebRTC data channel using specified parameters.
     * @param type channel type as defined in control protocol description.
     *             Use 0 for "reliable".
     * @param prio channel priority. The higher the number, the lower
     *             the priority.
     * @param reliab Reliability Parameter<br/>
     *
     * This field is ignored if a reliable channel is used.
     * If a partial reliable channel with limited number of
     * retransmissions is used, this field specifies the number of
     * retransmissions.  If a partial reliable channel with limited
     * lifetime is used, this field specifies the maximum lifetime in
     * milliseconds.  The following table summarizes this:<br/></br>

    +------------------------------------------------+------------------+
    | Channel Type                                   |   Reliability    |
    |                                                |    Parameter     |
    +------------------------------------------------+------------------+
    | DATA_CHANNEL_RELIABLE                          |     Ignored      |
    | DATA_CHANNEL_RELIABLE_UNORDERED                |     Ignored      |
    | DATA_CHANNEL_PARTIAL_RELIABLE_REXMIT           |  Number of RTX   |
    | DATA_CHANNEL_PARTIAL_RELIABLE_REXMIT_UNORDERED |  Number of RTX   |
    | DATA_CHANNEL_PARTIAL_RELIABLE_TIMED            |  Lifetime in ms  |
    | DATA_CHANNEL_PARTIAL_RELIABLE_TIMED_UNORDERED  |  Lifetime in ms  |
    +------------------------------------------------+------------------+
     * @param sid SCTP stream id that will be used by new channel
     *            (it must not be already used).
     * @param label text label for the channel.
     * @return new instance of <tt>WebRtcDataStream</tt> that represents opened
     *         WebRTC data channel.
     * @throws IOException if IO error occurs.
     */
    public synchronized WebRtcDataStream openChannel(int type, int prio,
        long reliab, int sid, String label) throws IOException
    {
        if (channels.containsKey(sid))
        {
            throw new IOException("Channel on sid: " + sid + " already exists");
        }

        // Label Length & Label
        byte[] labelBytes;
        int labelByteLength;

        if (label == null)
        {
            labelBytes = null;
            labelByteLength = 0;
        }
        else
        {
            labelBytes = label.getBytes("UTF-8");
            labelByteLength = labelBytes.length;
            if (labelByteLength > 0xFFFF)
                labelByteLength = 0xFFFF;
        }

        // Protocol Length & Protocol
        String protocol = WEBRTC_DATA_CHANNEL_PROTOCOL;
        byte[] protocolBytes;
        int protocolByteLength;

        if (protocol == null)
        {
            protocolBytes = null;
            protocolByteLength = 0;
        }
        else
        {
            protocolBytes = protocol.getBytes("UTF-8");
            protocolByteLength = protocolBytes.length;
            if (protocolByteLength > 0xFFFF)
                protocolByteLength = 0xFFFF;
        }

        ByteBuffer packet =
            ByteBuffer.allocate(12 + labelByteLength + protocolByteLength);

        // Message open new channel on current sid
        // Message Type
        packet.put((byte) MSG_OPEN_CHANNEL);
        // Channel Type
        packet.put((byte) type);
        // Priority
        packet.putShort((short) prio);
        // Reliability Parameter
        packet.putInt((int) reliab);
        // Label Length
        packet.putShort((short) labelByteLength);
        // Protocol Length
        packet.putShort((short) protocolByteLength);
        // Label
        if (labelByteLength != 0)
        {
            packet.put(labelBytes, 0, labelByteLength);
        }
        // Protocol
        if (protocolByteLength != 0)
        {
            packet.put(protocolBytes, 0, protocolByteLength);
        }

        System.out.print("openChannel: ");
        for (byte b : packet.array())
        {
            System.out.printf("%03d ", b);
        }
        System.out.println();

        int sentCount =
            sctpSocket.send(packet.array(), true, sid,
                WebRtcDataStream.WEB_RTC_PPID_CTRL);

        if (sentCount != packet.capacity())
        {
            throw new IOException("Failed to open new chanel on sid: " + sid);
        }

        WebRtcDataStream channel =
            new WebRtcDataStream(sctpSocket, sid, label, false);

        channels.put(sid, channel);

        return channel;
    }

    /**
     * Sends acknowledgment for open channel request on given SCTP stream ID.
     * 
     * @param sid SCTP stream identifier to be used for sending ack.
     */
    private void sendOpenChannelAck(int sid) throws IOException
    {
        // Send ACK
        byte[] ack = MSG_CHANNEL_ACK_BYTES;
        int sendAck =
            sctpSocket.send(ack, true, sid, WebRtcDataStream.WEB_RTC_PPID_CTRL);

        if (sendAck != ack.length)
        {
            logger.error("Failed to send open channel confirmation");
        }
    }
}
