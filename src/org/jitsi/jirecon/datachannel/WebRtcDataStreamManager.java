/*
/*
 * Jirecon, the JItsi REcording COntainer.
 *
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.jirecon.datachannel;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import javax.media.rtp.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.dtls.*;
import org.jitsi.sctp4j.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Manage all <tt>WebRtcDataStream</tt>s in one SCTP sonnection.
 * <p>
 * See WebRTC Data Channel Establishment Protocol:
 * <p>
 * http://tools.ietf.org/html/draft-ietf-rtcweb-data-protocol-07
 * 
 * @author lishunyang
 * 
 */
public class WebRtcDataStreamManager
{
    private static final Logger logger = Logger
        .getLogger(WebRtcDataStreamManager.class);
    
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

    /**
     * The pool of <tt>Thread</tt>s which run <tt>SctpConnection</tt>s.
     */
    private static final ExecutorService threadPool = ExecutorUtils
        .newCachedThreadPool(true, WebRtcDataStreamManager.class.getName());

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

    /**
     * Owner endpoint id.
     */
    private String endpointId;

    /**
     * Map of "sid" and <tt>WebRtcDataStream</tt>.
     */
    private Map<Integer, WebRtcDataStream> channels =
        new HashMap<Integer, WebRtcDataStream>();

    /**
     * This receiver is used for handling control packets and forward message
     * packet to associated <tt>WebRtcDataStream</tt>.
     */
    private SctpPacketReceiver packetReceiver = new SctpPacketReceiver();
    
    private WebRtcDataStreamListener listener;

    /**
     * 
     * @param endpointId The ownder's endpoint id.
     */
    public WebRtcDataStreamManager(String endpointId)
    {
        this.endpointId = endpointId;
    }

    /**
     * Start <tt>WebRtcDataStreamManager</tt> as a SCTP server side. It will
     * start SCTP connection and wait for SCTP handshake packet sent from client
     * side.
     * <p>
     * This method will start <tt>DtlsControl</tt>.
     * 
     * @param connector We need this to receive packets.
     * @param streamTarget Indicate where should we send packet to.
     * @param dtlsControl
     */
    public void runAsServer(StreamConnector connector,
        MediaStreamTarget streamTarget, DtlsControl dtlsControl)
    {
        try
        {
            initSctp(connector, streamTarget, dtlsControl);

            // FIXME manage threads
            threadPool.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        while (!sctpSocket.accept())
                        {
                            Thread.sleep(100);
                        }
                    }
                    catch (Exception e)
                    {
                        logger.error("Error accepting SCTP connection", e);
                    }
                }
            });
        }
        catch (Exception e)
        {
            logger.error("Failed to start WebRtcDataStreamManager", e);
        }
    }

    /**
     * Start <tt>WebRtcDataStreamManager</tt> as a client side. It will start
     * SCTP connection and send SCTP handshake packet to server side.
     * <p>
     * This method will start <tt>DtlsControl</tt>.
     * 
     * @param connector We need this to receive packets.
     * @param streamTarget Indicate where should we send packet to.
     * @param dtlsControl
     */
    public void runAsClient(StreamConnector connector,
        MediaStreamTarget streamTarget, DtlsControl dtlsControl)
    {
        try
        {
            initSctp(connector, streamTarget, dtlsControl);
            sctpSocket.connect(5000);
        }
        catch (Exception e)
        {
            logger.error("Failed to start WebRtcDataStreamManager", e);
        }
    }

    /**
     * Shutdown the <tt>WebRtcDataStream</tt> and close SCTP connection. It will
     * *NOT* close <tt>DtlsControl</tt>.
     */
    public void shutdown()
    {
        try
        {
            uinitSctp();
        }
        catch (IOException e)
        {
            logger.error("Failed to stop sctp socket", e);
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

    /**
     * Get <tt>WebRtcDataStream</tt> with specified "sid". Null will be returned
     * if no <tt>WebRtcDataStream</tt> was found.
     * 
     * @param sid
     * @return
     */
    public synchronized WebRtcDataStream getChannel(int sid)
    {
        WebRtcDataStream channel = null;

        channel = channels.get(sid);
        if (null == channel)
        {
            logger.error("No channel found for sid: " + sid);
        }

        return channel;
    }
    
    public synchronized void setListener(WebRtcDataStreamListener listener)
    {
        this.listener = listener;
    }
    
    /**
     * Create <tt>SctpSocket</tt> and initialize it.
     * 
     * @param connector We need this to receive packets.
     * @param streamTarget Indicate where to send packet.
     * @param dtlsControl
     * @throws Exception
     */
    private void initSctp(StreamConnector connector,
        MediaStreamTarget streamTarget, DtlsControl dtlsControl)
        throws Exception
    {
        if (null != sctpSocket)
        {
            logger.warn("Sctp stuff has already been started.");
            return;
        }

        Sctp.init();
        
        dtlsControl.start(MediaType.DATA);

        RTPConnectorUDPImpl rtpConnector = new RTPConnectorUDPImpl(connector);

        rtpConnector.addTarget(new SessionAddress(streamTarget.getDataAddress()
            .getAddress(), streamTarget.getDataAddress().getPort()));

        dtlsControl.setConnector(rtpConnector);

        final DtlsTransformEngine engine =
            (DtlsTransformEngine) dtlsControl.getTransformEngine();
        final DtlsPacketTransformer transformer =
            (DtlsPacketTransformer) engine.getRTPTransformer();

        final DatagramSocket iceUdpSocket = rtpConnector.getDataSocket();

        sctpSocket = Sctp.createSocket(5000);
        sctpSocket.setLink(new IceUdpDtlsLink(sctpSocket, iceUdpSocket,
            transformer));
        sctpSocket.setNotificationListener(packetReceiver);
        sctpSocket.setDataCallback(packetReceiver);
    }

    private void uinitSctp() throws IOException
    {
        sctpSocket.close();
        // TODO: Don't we need to remove callback from SctpSocket?
        sctpSocket = null;
        Sctp.finish();
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
             * Notify listener that we have built a new channel
             */
            if (null != listener)
                listener.onChannelOpened(newChannel);
        }
        else
        {
            logger.error("Unexpected ctrl msg type: " + messageType);
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
    
    /**
     * Class in <tt>WebRtcDataStreamManager</tt> which is used for receiving
     * packets from <tt>SctpSocket</tt>. So all <tt>SctpSocket</tt> will be
     * captured by this receiver only. If it gets a control message, it will
     * forward the message to <tt>WebRtcDataStreamManager</tt> to handle it. If
     * it gets a string message or binary message, it will forward the message
     * to associated <tt>WebRtcDataStream</tt> according to "sid".
     * 
     * @author lishunyang
     * 
     */
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
}
