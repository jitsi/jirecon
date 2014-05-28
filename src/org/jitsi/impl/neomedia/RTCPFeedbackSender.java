/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia;

import org.jitsi.impl.neomedia.device.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.event.*;
import org.jitsi.util.*;

import javax.media.*;
import javax.media.protocol.*;
import javax.media.rtp.*;
import java.io.*;
import java.util.*;

/**
 * A class that attaches to an <tt>RTPTranslator</tt>, allows to send RTCP
 * feedback messages (for the moment only FIR), and needs a more appropriate
 * name.
 *
 * @author Boris Grozev
 */
public class RTCPFeedbackSender
{
    /**
     * The <tt>Logger</tt> used by the <tt>RTCPFeedbackSender</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
            = Logger.getLogger(RTCPFeedbackSender.class);

    /**
     * The <tt>RTPTranslator</tt> to which we have attached.
     */
    private RTPTranslator translator;

    /**
     * The "local" SSRC, e.g. the SSRC which we should use as "packet sender
     * SSRC" when we sent RTCP feedback packets.
     */
    private long ssrc;

    /**
     * Maps a media source SSRC to the command sequence number of the last
     * RTCP FIR message sent to this media source SSRC.
     */
    private Map<Long, Integer> seqNumbers = new HashMap<Long, Integer>();

    /**
     * The control input stream of {@link #connector}, which we will use to
     * send RTCP packets.
     */
    private PushSourceStream controlInputStream;

    /**
     * The <tt>SourceTransferHandler</tt> of {@link #controlInputStream},
     * which is set by the <tt>RTPManager</tt> when it initializes, and which is
     * used to trigger reading from {@link #controlInputStream}.
     */
    private SourceTransferHandler transferHandler;

    /**
     * The <tt>RTPConnector</tt> which we use to attach to an
     * <tt>RTPTranslator</tt>.
     */
    private RTPConnectorImpl connector;

    /**
     * The RTCP feedback packet which is scheduled to be read on the next
     * call to <tt>controlInputStream.read()</tt>.
     */
    private RTCPFeedbackMessagePacket packet;

    /**
     * Initializes a new instance.
     *
     * @param translator the <tt>RTPTranslator</tt> to attach to.
     */
    public RTCPFeedbackSender(RTPTranslator translator)
    {
        MediaService mediaService = LibJitsi.getMediaService();
        StreamRTPManager streamRTPManager = new StreamRTPManager(
                mediaService.createMediaStream(new MediaDeviceImpl(
                        new CaptureDeviceInfo(), MediaType.VIDEO)),
                translator);

        this.translator = translator;
        controlInputStream = new PushSourceStreamImpl();
        connector = new RTPConnectorImpl();

        streamRTPManager.initialize(connector);
        ssrc = streamRTPManager.getLocalSSRC();
    }

    /**
     * Sends an RTCP FIR message to <tt>mediaSourceSsrc</tt>.
     * @param mediaSourceSsrc the SSRC of the media source to which an RTCP
     * FIR is to be sent.
     * @return <tt>true</tt>.
     */
    public synchronized boolean requestFIR(long mediaSourceSsrc)
    {
        RTCPFeedbackMessagePacket firPacket
                = new RTCPFeedbackMessagePacket(
                    RTCPFeedbackMessageEvent.FMT_FIR,
                    RTCPFeedbackMessageEvent.PT_PS,
                    ssrc,
                    mediaSourceSsrc);

        int seq = 0;
        if (seqNumbers.containsKey(mediaSourceSsrc))
            seq = 1 + seqNumbers.get(mediaSourceSsrc);
        seqNumbers.put(mediaSourceSsrc, seq);
        firPacket.setSequenceNumber(seq);

        packet = firPacket;
        if (logger.isInfoEnabled())
            logger.info("Sending RTCP FIR, packet sender SSRC="
                                + (0xffffffffl & packet.getSenderSSRC())
                                + ", media source SSRC="
                                + (0xffffffffl & packet.getSourceSSRC())
                                + ", seq=" + packet.getSequenceNumber());

        if (transferHandler != null)
            transferHandler.transferData(controlInputStream);

        return true;
    }

    /**
     * Implements an <tt>RTPConnector</tt> for the purposes of this RTCPFeedbackSender.
     * It has no data (RTP) streams, and no control output stream.
     */
    private class RTPConnectorImpl
        implements RTPConnector
    {
        @Override
        public void close()
        {
        }

        @Override
        public PushSourceStream getControlInputStream()
        {
            return RTCPFeedbackSender.this.controlInputStream;
        }

        @Override
        public OutputDataStream getControlOutputStream() throws IOException
        {
            return null;
        }

        @Override
        public PushSourceStream getDataInputStream() throws IOException
        {
            return null;
        }

        @Override
        public OutputDataStream getDataOutputStream() throws IOException
        {
            return null;
        }

        @Override
        public int getReceiveBufferSize()
        {
            return 0;
        }

        @Override
        public double getRTCPBandwidthFraction()
        {
            return 0;
        }

        @Override
        public double getRTCPSenderBandwidthFraction()
        {
            return 0;
        }

        @Override
        public int getSendBufferSize()
        {
            return 0;
        }

        @Override
        public void setReceiveBufferSize(int i) throws IOException
        {

        }

        @Override
        public void setSendBufferSize(int i) throws IOException
        {

        }
    }

    /**
     * Implements <tt>PushSourceStream</tt> for the purpose of this RTCPFeedbackSender.
     */
    private class PushSourceStreamImpl
            implements PushSourceStream
    {

        @Override
        public int getMinimumTransferSize()
        {
            //XXX we don't need over 20 bytes at the moment, but could that
            //cause problems?
            return 20;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException
        {
            RTCPFeedbackMessagePacket packet = RTCPFeedbackSender.this.packet;
            byte[] packetBuffer = packet.getBuffer();
            int packetLength = packet.getLength();
            if (packetLength > length)
            {
                logger.warn("Not writing packet, because length too small.");
                return 0;
            }
            System.arraycopy(packetBuffer, 0, buffer, offset, packetLength);
            return packetLength;
        }

        @Override
        public void setTransferHandler(SourceTransferHandler sourceTransferHandler)
        {
            RTCPFeedbackSender.this.transferHandler = sourceTransferHandler;
        }

        @Override
        public boolean endOfStream()
        {
            return false;
        }

        @Override
        public ContentDescriptor getContentDescriptor()
        {
            return null;
        }

        @Override
        public long getContentLength()
        {
            return 0;
        }

        @Override
        public Object getControl(String s)
        {
            return null;
        }

        @Override
        public Object[] getControls()
        {
            return new Object[0];
        }
    }
}
