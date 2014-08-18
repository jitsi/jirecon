package org.jitsi.jirecon.datachannel;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.transform.dtls.*;
import org.jitsi.sctp4j.*;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.packetlogging.PacketLoggingService;

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

    private DatagramSocket datagramSocket;

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
