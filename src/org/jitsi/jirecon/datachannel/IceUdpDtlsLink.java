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
import java.util.concurrent.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.dtls.*;
import org.jitsi.sctp4j.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.packetlogging.*;

/**
 * An implementation of <tt>NetworkLink</tt> which is used for receiving and
 * sending packet under SCTP connection.
 * <p>
 * This link will use ICE-UDP and DTLS protocol.
 * 
 * @author lishunyang
 * 
 */
public class IceUdpDtlsLink
    implements NetworkLink
{
    /**
     * DTLS transport buffer size. Note: randomly chosen.
     */
    private final static int DTLS_BUFFER_SIZE = 2048;

    /**
     * SCTP transport buffer size.
     */
    private final static int SCTP_BUFFER_SIZE = DTLS_BUFFER_SIZE - 13;

    /**
     * <tt>SctpSocket</tt> instance that is used in this connection.
     */
    private SctpSocket sctpSocket;

    /**
     * ICE-UDP socket, used for receiving packets.
     */
    private DatagramSocket datagramSocket;

    /**
     * DTLS transform engine, used for sending packets.
     */
    private DtlsPacketTransformer transformer;

    private ExecutorService executorService = Executors
        .newSingleThreadExecutor();

    /**
     * Switch used for debugging SCTP traffic purposes. FIXME to be removed
     */
    private final static boolean LOG_SCTP_PACKETS = false;

    /**
     * Generator used to track debug IDs.
     */
    private static int debugIdGen = -1;

    /**
     * Debug ID used to distinguish SCTP sockets in packet logs.
     */
    private int debugId = generateDebugId();

    /**
     * 
     * @param sctpSocket Indicate which <tt>SctpSocket</tt> this link will bind to.
     * @param datagramSocket ICE-UDP socket which is used for receiving packets.
     * @param transformer DTLS transformer which is used for sending packets.
     */
    public IceUdpDtlsLink(SctpSocket sctpSocket, DatagramSocket datagramSocket,
        DtlsPacketTransformer transformer)
    {
        this.sctpSocket = sctpSocket;
        this.datagramSocket = datagramSocket;
        this.transformer = transformer;

        startReceiving();
    }

    private void startReceiving()
    {
        executorService.execute(new Runnable()
        {
            public void run()
            {
                byte[] receiveBuffer = new byte[SCTP_BUFFER_SIZE];
                DatagramPacket rcvPacket =
                    new DatagramPacket(receiveBuffer, 0, receiveBuffer.length);

                try
                {
                    /*
                     * Once SctpSocket is closed, this loop will be broken down,
                     * so we don't have to worry about closing this link.
                     */
                    do
                    {
                        datagramSocket.receive(rcvPacket);

                        RawPacket raw =
                            new RawPacket(rcvPacket.getData(), rcvPacket
                                .getOffset(), rcvPacket.getLength());

                        raw = transformer.reverseTransform(raw);
                        // Check for app data
                        if (raw == null)
                            continue;

                        if (LOG_SCTP_PACKETS)
                        {
                            LibJitsi.getPacketLoggingService().logPacket(
                                PacketLoggingService.ProtocolName.ICE4J,
                                new byte[]
                                { 0, 0, 0, (byte) (debugId + 1) },
                                datagramSocket.getPort(), new byte[]
                                { 0, 0, 0, (byte) debugId }, 5000,
                                PacketLoggingService.TransportName.UDP, false,
                                raw.getBuffer(), raw.getOffset(),
                                raw.getLength());
                        }

                        // Pass network packet to SCTP stack
                        sctpSocket.onConnIn(raw.getBuffer(), raw.getOffset(),
                            raw.getLength());
                    }
                    while (true);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onConnOut(final SctpSocket s, final byte[] packetData)
        throws IOException
    {
        RawPacket rawPacket = new RawPacket();

        // Send through DTLS transport
        rawPacket.setBuffer(packetData);
        rawPacket.setLength(packetData.length);

        transformer.transform(rawPacket);
    }

    private static synchronized int generateDebugId()
    {
        debugIdGen += 2;
        return debugIdGen;
    }
}
