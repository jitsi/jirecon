/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.transport;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidatePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidateType;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.LocalCandidate;
import org.ice4j.ice.RemoteCandidate;
import org.jitsi.jirecon.JireconEvent;
import org.jitsi.jirecon.JireconEventListener;
import org.jitsi.jirecon.utils.JinglePacketParser;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.service.neomedia.DefaultStreamConnector;
import org.jitsi.service.neomedia.MediaStreamTarget;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.StreamConnector;
import org.jitsi.util.Logger;

public class JireconIceUdpTransportManagerImpl
    implements JireconTransportManager
{
    private Agent iceAgent;

    private Map<MediaType, StreamConnector> streamConnectors =
        new HashMap<MediaType, StreamConnector>();

    private Map<MediaType, MediaStreamTarget> mediaStreamTargets =
        new HashMap<MediaType, MediaStreamTarget>();

    private Logger logger;

    private String MIN_STREAM_PORT_KEY = "MIN_STREAM_PORT";

    private String MAX_STREAM_PORT_KEY = "MAX_STREAM_PORT";

    private int MIN_STREAM_PORT;

    private int MAX_STREAM_PORT;

    public JireconIceUdpTransportManagerImpl()
    {
        logger = Logger.getLogger(this.getClass());
    }

    @Override
    public void init(JireconConfiguration configuration)
    {
        iceAgent = new Agent();
        MIN_STREAM_PORT =
            Integer.valueOf(configuration.getProperty(MIN_STREAM_PORT_KEY));
        MAX_STREAM_PORT =
            Integer.valueOf(configuration.getProperty(MAX_STREAM_PORT_KEY));
    }

    @Override
    public void uninit()
    {
        iceAgent.free();
    }

    @Override
    public IceUdpTransportPacketExtension getTransportPacketExt()
    {
        IceUdpTransportPacketExtension transportPE =
            new IceUdpTransportPacketExtension();
        transportPE.setPassword(iceAgent.getLocalPassword());
        transportPE.setUfrag(iceAgent.getLocalUfrag());
        for (CandidatePacketExtension candidatePE : getLocalCandidatePacketExts())
        {
            transportPE.addCandidate(candidatePE);
        }
        return transportPE;
    }

    public void startConnectivityEstablishment()
    {

        iceAgent.startConnectivityEstablishment();
    }

    @Override
    public void harvestLocalCandidates()
        throws BindException,
        IllegalArgumentException,
        IOException
    {
        logger.info("harvestLocalCandidates begin");
        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
            {
                continue;
            }

            final IceMediaStream stream = getIceMediaStream(mediaType);
            iceAgent.createComponent(stream, Transport.UDP, MIN_STREAM_PORT,
                MIN_STREAM_PORT, MAX_STREAM_PORT);
            iceAgent.createComponent(stream, Transport.UDP, MIN_STREAM_PORT,
                MIN_STREAM_PORT, MAX_STREAM_PORT);
        }
    }

    public void harvestRemoteCandidates(JingleIQ jiq)
    {
        logger.info("harvestRemoteCandidates begin");
        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
            {
                continue;
            }

            final IceMediaStream stream = getIceMediaStream(mediaType);
            final String ufrag =
                JinglePacketParser.getTransportUfrag(jiq, mediaType);
            if (null != ufrag)
            {
                stream.setRemoteUfrag(ufrag);
            }

            final String password =
                JinglePacketParser.getTransportPassword(jiq, mediaType);
            if (null != password)
            {
                stream.setRemotePassword(password);
            }

            List<CandidatePacketExtension> candidates =
                JinglePacketParser.getCandidatePacketExt(jiq, mediaType);
            // Sort the remote candidates (host < reflexive < relayed) in order
            // to create first the host, then the reflexive, the relayed
            // candidates and thus be able to set the relative-candidate
            // matching the rel-addr/rel-port attribute.
            Collections.sort(candidates);
            for (CandidatePacketExtension c : candidates)
            {
                if (c.getGeneration() != iceAgent.getGeneration())
                    continue;
                final Component component =
                    stream.getComponent(c.getComponent());

                // FIXME: Add support for not-host address
                final RemoteCandidate remoteCandidate =
                    new RemoteCandidate(new TransportAddress(c.getIP(),
                        c.getPort(), Transport.parse(c.getProtocol())),
                        component, org.ice4j.ice.CandidateType.parse(c
                            .getType().toString()), c.getFoundation(),
                        c.getPriority(), getRelatedCandidate(c, component));

                component.addRemoteCandidate(remoteCandidate);
            }
        }

        logger.info("harvestRemoteCandidates finished");
    }

    public CandidatePair getCandidatePairs(MediaType mediaType, int componentId)
    {
        logger.info("harvestCandidatePairs, component " + componentId);
        final IceMediaStream iceStream = getIceMediaStream(mediaType);
        final Component component = iceStream.getComponent(componentId);
        return component.getSelectedPair();
    }

    public IceMediaStream getIceMediaStream(MediaType mediaType)
    {
        if (null == iceAgent.getStream(mediaType.toString()))
        {
            iceAgent.createMediaStream(mediaType.toString());
        }
        return iceAgent.getStream(mediaType.toString());
    }

    private List<CandidatePacketExtension> getLocalCandidatePacketExts()
    {
        List<CandidatePacketExtension> candidatePEs =
            new ArrayList<CandidatePacketExtension>();

        int id = 1;
        for (LocalCandidate candidate : getLocalCandidates())
        {
            CandidatePacketExtension candidatePE =
                new CandidatePacketExtension();
            candidatePE.setComponent(candidate.getParentComponent()
                .getComponentID());
            candidatePE.setFoundation(candidate.getFoundation());
            candidatePE.setGeneration(iceAgent.getGeneration());
            candidatePE.setID(String.valueOf(id++));
            candidatePE.setNetwork(1);
            candidatePE.setIP(candidate.getTransportAddress().getHostAddress());
            candidatePE.setPort(candidate.getTransportAddress().getPort());
            candidatePE.setPriority(candidate.getPriority());
            candidatePE.setProtocol(candidate.getTransport().toString());
            candidatePE.setType(CandidateType.valueOf(candidate.getType()
                .toString()));
            candidatePEs.add(candidatePE);
        }

        return candidatePEs;
    }

    private List<LocalCandidate> getLocalCandidates()
    {
        List<LocalCandidate> candidates = new ArrayList<LocalCandidate>();

        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
            {
                continue;
            }

            IceMediaStream stream = getIceMediaStream(mediaType);
            for (Component com : stream.getComponents())
            {
                candidates.addAll(com.getLocalCandidates());
            }
        }

        return candidates;
    }

    private RemoteCandidate getRelatedCandidate(
        CandidatePacketExtension candidate, Component component)
    {
        if ((candidate.getRelAddr() != null) && (candidate.getRelPort() != -1))
        {
            final String relAddr = candidate.getRelAddr();
            final int relPort = candidate.getRelPort();
            final TransportAddress relatedAddress =
                new TransportAddress(relAddr, relPort,
                    Transport.parse(candidate.getProtocol()));
            return component.findRemoteCandidate(relatedAddress);
        }
        return null;
    }

    @Override
    public void addStateChangeListener(PropertyChangeListener listener)
    {
        iceAgent.addStateChangeListener(listener);
    }

    @Override
    public MediaStreamTarget getStreamTarget(MediaType mediaType)
    {
        if (mediaStreamTargets.containsKey(mediaType))
        {
            return mediaStreamTargets.get(mediaType);
        }

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
            if (streamTargetAddresses.size() > 0)
            {
                streamTarget =
                    new MediaStreamTarget(
                        streamTargetAddresses.get(0) /* RTP */,
                        streamTargetAddresses.get(1) /* RTCP */);
                mediaStreamTargets.put(mediaType, streamTarget);
            }
        }
        return streamTarget;
    }

    @Override
    public StreamConnector getStreamConnector(MediaType mediaType)
    {
        if (streamConnectors.containsKey(mediaType))
        {
            return streamConnectors.get(mediaType);
        }

        StreamConnector streamConnector = null;
        IceMediaStream stream = getIceMediaStream(mediaType);
        if (stream != null)
        {
            List<DatagramSocket> datagramSockets =
                new ArrayList<DatagramSocket>();

            for (Component component : stream.getComponents())
            {
                if (component != null)
                {
                    CandidatePair selectedPair = component.getSelectedPair();

                    if (selectedPair != null)
                    {
                        datagramSockets.add(selectedPair.getLocalCandidate()
                            .getDatagramSocket());
                    }
                }
            }
            if (datagramSockets.size() > 0)
            {
                streamConnector =
                    new DefaultStreamConnector(
                        datagramSockets.get(0) /* RTP */,
                        datagramSockets.get(1) /* RTCP */);
                streamConnectors.put(mediaType, streamConnector);
            }
        }

        return streamConnector;
    }

}
