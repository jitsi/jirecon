/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.data;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import javax.media.rtp.SessionAddress;

import org.jitsi.impl.neomedia.RTPConnectorUDPImpl;
import org.jitsi.impl.neomedia.RawPacket;
import org.jitsi.impl.neomedia.transform.dtls.DtlsControlImpl;
import org.jitsi.impl.neomedia.transform.dtls.DtlsPacketTransformer;
import org.jitsi.impl.neomedia.transform.dtls.DtlsTransformEngine;
import org.jitsi.sctp4j.NetworkLink;
import org.jitsi.sctp4j.Sctp;
import org.jitsi.sctp4j.SctpDataCallback;
import org.jitsi.sctp4j.SctpNotification;
import org.jitsi.sctp4j.SctpSocket;
import org.jitsi.sctp4j.SctpSocket.NotificationListener;
import org.jitsi.service.neomedia.DtlsControl;
import org.jitsi.service.neomedia.MediaStreamTarget;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.StreamConnector;
import org.jitsi.util.Logger;

public class JireconSctpConnection
    implements SctpDataCallback, NotificationListener
{
    private static final Logger logger = Logger
        .getLogger(JireconSctpConnection.class);

    public JireconSctpConnection()
    {
    }

    public void start(StreamConnector connector, MediaStreamTarget streamTarget)
    {
        dtlsStart(connector, streamTarget);
    }

    public void dtlsStart(final StreamConnector connector,
        final MediaStreamTarget streamTarget)
    {
        final DtlsControlImpl dtlsControl = new DtlsControlImpl(true);

        dtlsControl.setSetup(DtlsControl.Setup.ACTIVE);
        dtlsControl.start(MediaType.DATA);

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    Sctp.init();

                    RTPConnectorUDPImpl rtpConnector =
                        new RTPConnectorUDPImpl(connector);

                    rtpConnector.addTarget(new SessionAddress(streamTarget
                        .getDataAddress().getAddress(), streamTarget
                        .getDataAddress().getPort()));

                    dtlsControl.setConnector(rtpConnector);

                    DtlsTransformEngine engine =
                        dtlsControl.getTransformEngine();
                    final DtlsPacketTransformer transformer =
                        (DtlsPacketTransformer) engine.getRTPTransformer();

                    final SctpSocket sctpSocket = Sctp.createSocket(5000);
                    sctpSocket.setNotificationListener(JireconSctpConnection.this);

                    // Notify that from now on SCTP connection is considered
                    // functional
                    sctpSocket.setDataCallback(JireconSctpConnection.this);

                    // Receive loop, breaks when SCTP socket is closed
                    DatagramSocket iceUdpSocket = rtpConnector.getDataSocket();
                    byte[] receiveBuffer = new byte[2035];
                    DatagramPacket rcvPacket =
                        new DatagramPacket(receiveBuffer, 0,
                            receiveBuffer.length);

                    sctpSocket.setLink(new NetworkLink()
                    {
                        private final RawPacket rawPacket = new RawPacket();

                        @Override
                        public void onConnOut(org.jitsi.sctp4j.SctpSocket s,
                            byte[] packet) throws IOException
                        {
                            System.out.println(packet);

                            // Send through DTLS transport
                            rawPacket.setBuffer(packet);
                            rawPacket.setLength(packet.length);

                            transformer.transform(rawPacket);
                        }
                    });

                    sctpSocket.connect(5000);

                    int sent = sctpSocket.send(new byte[200], false, 0, 0);

                    System.out.println("HELLO, " + sent);

                    try
                    {
                        while (true)
                        {
                            iceUdpSocket.receive(rcvPacket);

                            RawPacket raw =
                                new RawPacket(rcvPacket.getData(), rcvPacket
                                    .getOffset(), rcvPacket.getLength());

                            raw = transformer.reverseTransform(raw);

                            // Check for app data
                            if (raw == null)
                                continue;

                            // Pass network packet to SCTP stack
                            sctpSocket.onConnIn(raw.getBuffer(),
                                raw.getOffset(), raw.getLength());

                        }
                    }
                    finally
                    {
                        // Eventually close the socket, although it should
                        // happen from
                        // expire()
                        if (sctpSocket != null)
                            sctpSocket.close();
                    }
                }
                catch (IOException e)
                {
                    logger.error(e, e);
                }
                finally
                {
                    try
                    {
                        Sctp.finish();
                    }
                    catch (IOException e)
                    {
                        logger.error("Failed to shutdown SCTP stack", e);
                    }
                }
            }
        }, "SctpConnectionReceiveThread").start();
    }

    @Override
    public void onSctpPacket(byte[] data, int sid, int ssn, int tsn, long ppid,
        int context, int flags)
    {
        System.out.println("SCTP: " + data);
    }

    @Override
    public void onSctpNotification(SctpSocket socket,
        SctpNotification notification)
    {
        System.out.println("SCTP: Notification");
    }
}
