/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidateType;
import org.ice4j.*;
import org.ice4j.ice.*;
import org.jitsi.jirecon.utils.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;

/**
 * Transport manager that under ICE/UDP protocol.
 * <p>
 * 1. Establish ICE connectivity.
 * <p>
 * 2. Create <tt>IceUdpTransportPacketExtension</tt>
 * 
 * @author lishunyang
 */
public class IceUdpTransportManager
{
    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger
        .getLogger(IceUdpTransportManager.class.getName());

    /**
     * The minimum time (second) when wait for something.
     */
    private static int MIN_WAIT_TIME = 1;

    /**
     * The maximum time (second) when wait for something.
     */
    private static int MAX_WAIT_TIME = 10;

    /**
     * Instance of <tt>Agent</tt>.
     */
    private Agent iceAgent;

    /**
     * Map between <tt>MediaType</tt> and <tt>StreamConnector</tt>. It is used
     * for caching <tt>StreamConnector</tt>.
     */
    private Map<MediaType, StreamConnector> streamConnectors =
        new HashMap<MediaType, StreamConnector>();

    /**
     * Map between <tt>MediaType</tt> and <tt>MediaStreamTarget</tt>. It is used
     * for caching <tt>MediaStreamTarget</tt>.
     */
    private Map<MediaType, MediaStreamTarget> mediaStreamTargets =
        new HashMap<MediaType, MediaStreamTarget>();

    /**
     * The minimum stream port.
     */
    private final int MIN_STREAM_PORT;

    /**
     * The maximum stream port.
     */
    private final int MAX_STREAM_PORT;

    public IceUdpTransportManager()
    {
        iceAgent = new Agent();
        /*
         * We should set "controlling" to "false", becase iceAgent is act as an
         * client. See Interactive Connectivity Establishment (ICE): A Protocol
         * for Network Address Translator (NAT) Traversal for Offer/Answer
         * Protocols(http://tools.ietf.org/html/rfc5245#section-7.1.2.2)
         */
        iceAgent.setControlling(false);
        
        LibJitsi.start();
        ConfigurationService configuration = LibJitsi.getConfigurationService();
        MIN_STREAM_PORT =
            configuration.getInt(ConfigurationKey.MIN_STREAM_PORT_KEY,
                -1);
        MAX_STREAM_PORT =
            configuration.getInt(ConfigurationKey.MAX_STREAM_PORT_KEY,
                -1);
    }

    /**
     * Free the resources holded by <tt>JireconTransportManager</tt>.
     */
    public void free()
    {
        iceAgent.free();
    }

    /**
     * Create a <tt>IceUdpTransportPacketExtension</tt>.
     * 
     * @param mediaType Indicate which media type do you want.
     * @return
     */
    public IceUdpTransportPacketExtension createTransportPacketExt(MediaType mediaType)
    {
        IceUdpTransportPacketExtension transportPE =
            new IceUdpTransportPacketExtension();
        
        transportPE.setPassword(iceAgent.getLocalPassword());
        transportPE.setUfrag(iceAgent.getLocalUfrag());

        for (CandidatePacketExtension candidatePE : createLocalCandidatePacketExts(mediaType))
        {
            transportPE.addCandidate(candidatePE);
        }

        return transportPE;
    }

    /**
     * Start establishing ICE connectivity.
     * <p>
     * <strong>Warning:</strong> This method is asynchronous, it will return
     * immediately while it doesn't means the ICE connectivity has been
     * established successfully. On the contrary, sometime it will never
     * finished. Fortunately, we need only one selected candidate pair, so we
     * don't care whether it terminates.
     * 
     * @throws Exception If any internal error happens.
     */
    public void startConnectivityEstablishment()
    {
        logger.debug("startConnectivityEstablishment");
        
        iceAgent.startConnectivityEstablishment();
    }

    /**
     * Harvest local candidates of specified <tt>MediaType</tt>.
     * 
     * @mediaType
     * @throws Exception if we can't create ice component.
     */
    public void harvestLocalCandidates(MediaType mediaType) 
        throws Exception
    {
        logger.debug("harvestLocalCandidates");

        final IceMediaStream stream = getIceMediaStream(mediaType);

        try
        {
            /*
             * As for "data" type, we only need to create one component for
             * establish connection, while as for "audio" and "video", we need
             * two components, one for RTP transmission and one for RTCP
             * transmission.
             */
            iceAgent.createComponent(stream, Transport.UDP, MIN_STREAM_PORT,
                MIN_STREAM_PORT, MAX_STREAM_PORT);

            if (MediaType.AUDIO == mediaType || MediaType.VIDEO == mediaType)
            {
                iceAgent.createComponent(stream, Transport.UDP,
                    MIN_STREAM_PORT, MIN_STREAM_PORT, MAX_STREAM_PORT);
            }
        }
        catch (Exception e)
        {
            throw new Exception("Could not create ICE component, "
                + e.getMessage());
        }
    }

    /**
     * Parse and harvest remote candidates from an incoming
     * <tt>IceUdpTransportPacketExtension</tt>.
     * 
     * @param transportPEs The <tt>IceUdpTransportPacketExtension</tt> to be
     *            parsed.
     */
    public void harvestRemoteCandidates(
        Map<MediaType, IceUdpTransportPacketExtension> transportPEs)
    {
        logger.debug("harvestRemoteCandidates");
        
        for (java.util.Map.Entry<MediaType, IceUdpTransportPacketExtension> e : transportPEs
            .entrySet())
        {
            final MediaType mediaType = e.getKey();
            final IceUdpTransportPacketExtension transportPE = e.getValue();
            final IceMediaStream stream = getIceMediaStream(mediaType);
            final String ufrag = transportPE.getUfrag();
            if (null != ufrag)
                stream.setRemoteUfrag(ufrag);

            final String password = transportPE.getPassword();
            if (null != password)
                stream.setRemotePassword(password);

            List<CandidatePacketExtension> candidates = transportPE.getCandidateList();
            /*
             * Sort the remote candidates (host < reflexive < relayed) in order
             * to create first the host, then the reflexive, the relayed
             * candidates and thus be able to set the relative-candidate
             * matching the rel-addr/rel-port attribute.
             */
            Collections.sort(candidates);
            
            for (CandidatePacketExtension candidate : candidates)
            {
                if (candidate.getGeneration() != iceAgent.getGeneration())
                    continue;

                final Component component =
                    stream.getComponent(candidate.getComponent());

                final String relAddr = candidate.getRelAddr();
                final int relPort = candidate.getRelPort();
                TransportAddress relatedAddress = null;

                if ((relAddr != null) && (relPort > 0))
                {
                    relatedAddress =
                        new TransportAddress(relAddr, relPort,
                            Transport.parse(candidate.getProtocol()));
                }

                final RemoteCandidate relatedCandidate =
                    component.findRemoteCandidate(relatedAddress);

                final TransportAddress mainAddress =
                    new TransportAddress(candidate.getIP(),
                        candidate.getPort(), Transport.parse(candidate
                            .getProtocol()));

                final RemoteCandidate remoteCandidate =
                    new RemoteCandidate(mainAddress, component,
                        org.ice4j.ice.CandidateType.parse(candidate.getType()
                            .toString()), candidate.getFoundation(),
                        candidate.getPriority(), relatedCandidate);

                component.addRemoteCandidate(remoteCandidate);
            }
        }
    }

    /**
     * Get <tt>IceMediaStream</tt> of specified <tt>MediaType</tt>.
     * <p>
     * If there is no specified <tt>IceMediaStream</tt>, we will create a new
     * one.
     * 
     * @param mediaType
     * @return
     */
    private IceMediaStream getIceMediaStream(MediaType mediaType)
    {
        if (null == iceAgent.getStream(mediaType.toString()))
        {
            iceAgent.createMediaStream(mediaType.toString());
        }
        
        return iceAgent.getStream(mediaType.toString());
    }

    /**
     * Create list of <tt>CandidatePacketExtension</tt> with specified <tt>MediaType</tt>.
     * 
     * @param mediaType
     * @return List of <tt>CandidatePacketExtension</tt>
     */
    private List<CandidatePacketExtension> createLocalCandidatePacketExts(MediaType mediaType)
    {
        List<CandidatePacketExtension> candidatePEs =
            new ArrayList<CandidatePacketExtension>();

        int id = 1;
        for (LocalCandidate candidate : getLocalCandidates(mediaType))
        {
            CandidatePacketExtension packetExt =
                new CandidatePacketExtension();
            packetExt.setComponent(candidate.getParentComponent()
                .getComponentID());
            packetExt.setFoundation(candidate.getFoundation());
            packetExt.setGeneration(iceAgent.getGeneration());
            packetExt.setID(String.valueOf(id++));
            packetExt.setNetwork(0); // Why it is 0?
            packetExt.setIP(candidate.getTransportAddress().getHostAddress());
            packetExt.setPort(candidate.getTransportAddress().getPort());
            packetExt.setPriority(candidate.getPriority());
            packetExt.setProtocol(candidate.getTransport().toString());
            packetExt.setType(CandidateType.valueOf(candidate.getType()
                .toString()));
            candidatePEs.add(packetExt);
        }

        return candidatePEs;
    }

    /**
     * Get local candidates of specified <tt>MediaType</tt>
     * 
     * @param mediaType
     * @return List of <tt>LocalCandidate</tt>
     */
    private List<LocalCandidate> getLocalCandidates(MediaType mediaType)
    {
        List<LocalCandidate> candidates = new ArrayList<LocalCandidate>();

        IceMediaStream stream = getIceMediaStream(mediaType);
        for (Component com : stream.getComponents())
        {
            candidates.addAll(com.getLocalCandidates());
        }

        return candidates;
    }

    /**
     * Get <tt>MediaStreamTarget</tt> of specified <tt>MediaType</tt>.
     * <p>
     * If there is no specified <tt>MediaStreamTarget</tt>, we will create a new one.
     * 
     * @param mediaType The specified <tt>MediaType</tt>.
     * @return
     */
    public MediaStreamTarget getStreamTarget(MediaType mediaType)
    {
        logger.debug("getStreamTarget");
        
        if (mediaStreamTargets.containsKey(mediaType))
            return mediaStreamTargets.get(mediaType);

        IceMediaStream stream = getIceMediaStream(mediaType);
        MediaStreamTarget streamTarget = null;
        if (stream != null)
        {
            List<InetSocketAddress> streamTargetAddresses =
                new ArrayList<InetSocketAddress>();

            for (Component component : stream.getComponents())
            {
                if (component != null)
                {
                    CandidatePair selectedPair = component.getSelectedPair();

                    if (selectedPair != null)
                    {
                        InetSocketAddress streamTargetAddress =
                            selectedPair.getRemoteCandidate()
                                .getTransportAddress();

                        if (streamTargetAddress != null)
                        {
                            streamTargetAddresses.add(streamTargetAddress);
                        }
                    }
                }
            }
            if (streamTargetAddresses.size() == 2)
            {
                streamTarget =
                    new MediaStreamTarget(
                        streamTargetAddresses.get(0) /* RTP */,
                        streamTargetAddresses.get(1) /* RTCP */);
                mediaStreamTargets.put(mediaType, streamTarget);
            }
            else if (streamTargetAddresses.size() == 1)
            {
                streamTarget =
                    new MediaStreamTarget(
                        streamTargetAddresses.get(0),
                        null);
                mediaStreamTargets.put(mediaType, streamTarget);
            }
        }
        
        return streamTarget;
    }

    /**
     * Get <tt>StreamConnector</tt> of specified <tt>MediaType</tt> created by
     * <tt>JireconTransportManager</tt>.
     * <p>
     * <strong>Warning:</strong> This method will wait for the selected
     * candidate pair which should be generated during establish ICE
     * connectivity. If selected candidate pair hasn'e been generated, it will
     * wait for at most MAX_WAIT_TIME. After that it will break and throw an
     * exception.
     * 
     * @param mediaType The specified <tt>MediaType</tt>
     * @return <tt>StreamConnector</tt>
     * @throws Exception if we can't get <tt>StreamConnector</tt>.
     */
    public StreamConnector getStreamConnector(MediaType mediaType)
        throws Exception
    {
        logger.debug("getStreamConnector " + mediaType);
        
        if (streamConnectors.containsKey(mediaType))
            return streamConnectors.get(mediaType);

        int sumWaitTime = 0;
        while (sumWaitTime <= MAX_WAIT_TIME)
        {
            try
            {
                if (IceProcessingState.TERMINATED == iceAgent.getState())
                    break;

                logger
                    .debug("Could not get stream connector, sleep for a while. Already sleep for "
                        + sumWaitTime + " seconds");
                sumWaitTime += MIN_WAIT_TIME;
                TimeUnit.SECONDS.sleep(MIN_WAIT_TIME);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        if (IceProcessingState.TERMINATED != iceAgent.getState())
        {
            throw new Exception(
                "Could not get stream connector, it seems that ICE connectivity establishment hung"
                );
        }

        StreamConnector streamConnector = null;
        IceMediaStream stream = getIceMediaStream(mediaType);
        if (null == stream)
        {
            throw new Exception(
                "Could not get stream connector, ICE media stream was not prepared.");
        }

        CandidatePair rtpPair = null;
        CandidatePair rtcpPair = null;
        DatagramSocket rtpSocket = null;
        DatagramSocket rtcpSocket = null;

        rtpPair = stream.getComponent(Component.RTP).getSelectedPair();
        rtpSocket = rtpPair.getLocalCandidate().getDatagramSocket();
        
        /*
         * Yeah, only "audio" and "video" type need the second candidate pair
         * for RTCP conenction.
         */
        if (MediaType.AUDIO == mediaType || MediaType.VIDEO == mediaType)
        {
            rtcpPair = stream.getComponent(Component.RTCP).getSelectedPair();
            rtcpSocket = rtcpPair.getLocalCandidate().getDatagramSocket();
        }

        streamConnector = new DefaultStreamConnector(rtpSocket, rtcpSocket);

        streamConnectors.put(mediaType, streamConnector);

        return streamConnector;
    }
}
