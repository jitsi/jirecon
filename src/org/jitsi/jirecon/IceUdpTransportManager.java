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
package org.jitsi.jirecon;

import java.beans.*;
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
 * @author Boris Grozev
 */
public class IceUdpTransportManager
{
    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger
        .getLogger(IceUdpTransportManager.class.getName());

    /**
     * The maximum time in milliseconds to wait for ICE to complete.
     */
    private static int MAX_WAIT_TIME = 10000;

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

    /**
     * Sync root for {@link #lastUsedPort}.
     */
    private static final Object lastUsedPortSyncRoot = new Object();

    /**
     * The last number used as preferred port number for candidate allocation.
     */
    private static int lastUsedPort = -1;

    public IceUdpTransportManager()
    {
        iceAgent = new Agent();

        // TODO: set the role of the Agent according to the offer we received.
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
     * Free the resources held by <tt>JireconTransportManager</tt>.
     */
    public void free()
    {
        iceAgent.free();
    }

    /**
     * Create a <tt>IceUdpTransportPacketExtension</tt>.
     * 
     * @param mediaType Indicate which media type do you want.
     * @return the created <tt>IceUdpTransportPacketExtension</tt>.
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
     * Starts ICE connectivity establishment and returns immediately.
     * <p>
     * <strong>Warning:</strong> This method is asynchronous, it will return
     * immediately while it doesn't means the ICE connectivity has been
     * established successfully.
     */
    public void startConnectivityEstablishment()
    {
        logger.debug("startConnectivityEstablishment");

        iceAgent.startConnectivityEstablishment();
    }

    /**
     * Waits until {@link #iceAgent} enters a final state (CONNECTED, TERMINATED,
     * or FAILED). Waits for at most 10 seconds.
     *
     * Note: connectivity establishment has to have been started using
     * {@link #startConnectivityEstablishment()} before this method is called.
     *
     * @return <tt>true</tt> if ICE connectivity has been established, and
     * <tt>false</tt> otherwise.
     */
    public boolean wrapupConnectivityEstablishment()
    {
        final Object syncRoot = new Object();
        PropertyChangeListener propertyChangeListener
                = new PropertyChangeListener()
        {
            @Override
            public void propertyChange(PropertyChangeEvent ev)
            {
                Object newValue = ev.getNewValue();

                if (IceProcessingState.COMPLETED.equals(newValue)
                        || IceProcessingState.FAILED.equals(newValue)
                        || IceProcessingState.TERMINATED.equals(newValue))
                {
                    Agent iceAgent = (Agent) ev.getSource();

                    iceAgent.removeStateChangeListener(this);
                    if (iceAgent == IceUdpTransportManager.this.iceAgent)
                    {
                        synchronized (syncRoot)
                        {
                            syncRoot.notify();
                        }
                    }
                }
            }
        };

        iceAgent.addStateChangeListener(propertyChangeListener);
        synchronized (syncRoot)
        {
            long startWait = System.currentTimeMillis();
            do
            {
                IceProcessingState iceState = iceAgent.getState();
                if (IceProcessingState.COMPLETED.equals(iceState)
                        || IceProcessingState.TERMINATED.equals(iceState)
                        || IceProcessingState.FAILED.equals(iceState))
                    break;

                if (System.currentTimeMillis() - startWait > MAX_WAIT_TIME)
                    break; // Don't run for more than 10 seconds

                try
                {
                    syncRoot.wait(MAX_WAIT_TIME);
                }
                catch (InterruptedException ie)
                {
                    logger.fatal("Interrupted: " + ie);
                    break;
                }
            }
            while (true);
        }

        return true;
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
            iceAgent.createComponent(stream, Transport.UDP, getPreferredPort(),
                MIN_STREAM_PORT, MAX_STREAM_PORT);

            // We don't need an RTCP component for DATA.
            if (MediaType.AUDIO == mediaType || MediaType.VIDEO == mediaType)
            {
                lastUsedPort += 1;
                iceAgent.createComponent(
                        stream,
                        Transport.UDP,
                        getPreferredPort(),
                        MIN_STREAM_PORT,
                        MAX_STREAM_PORT);
            }
        }
        catch (Exception e)
        {
            throw new Exception("Could not create ICE component, "
                + e.getMessage());
        }
    }

    /**
     * Gets the next port number to be used as a preferred port number for
     * candidate allocation.
     * @return the next port number to be used as a preferred port number for
     * candidate allocation.
     */
    private int getPreferredPort()
    {
        synchronized (lastUsedPortSyncRoot)
        {
            lastUsedPort++;
            if (lastUsedPort < MIN_STREAM_PORT || lastUsedPort > MAX_STREAM_PORT)
                lastUsedPort = MIN_STREAM_PORT;
            return lastUsedPort;
        }
    }

    /**
     * Add all remote candidates from the values of <tt>transportPEs</tt> to the
     * corresponding IceMediaStream.
     *
     * @param transportPEs The <tt>IceUdpTransportPacketExtension</tt> to be
     * parsed.
     */
    public void addRemoteCandidates(
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
        IceMediaStream stream = iceAgent.getStream(mediaType.toString());

        if (stream == null)
        {
            stream = iceAgent.createMediaStream(mediaType.toString());
        }

        return stream;
    }

    /**
     * Create list of <tt>CandidatePacketExtension</tt> with specified <tt>MediaType</tt>.
     * 
     * @param mediaType
     * @return List of <tt>CandidatePacketExtension</tt>
     */
    private List<CandidatePacketExtension>
        createLocalCandidatePacketExts(MediaType mediaType)
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
     * If there is no specified <tt>MediaStreamTarget</tt>, we will create a
     * new one.
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
            InetSocketAddress rtpAddress = null;
            InetSocketAddress rtcpAddress = null;

            for (Component component : stream.getComponents())
            {
                if (component != null)
                {
                    CandidatePair selectedPair = component.getSelectedPair();
                    int id = component.getComponentID();

                    if (selectedPair != null)
                    {
                        InetSocketAddress address =
                            selectedPair.getRemoteCandidate()
                                .getTransportAddress();

                        if (address != null)
                        {
                            if (id == Component.RTP)
                                rtpAddress = address;
                            else if (id == Component.RTCP)
                                rtcpAddress = address;
                        }
                    }
                }
            }

            streamTarget =
                new MediaStreamTarget(rtpAddress, rtcpAddress);
            mediaStreamTargets.put(mediaType, streamTarget);
        }

        return streamTarget;
    }

    /**
     * Get <tt>StreamConnector</tt> of specified <tt>MediaType</tt> created by
     * <tt>JireconTransportManager</tt>.
     * <p>
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
        rtpSocket = rtpPair.getIceSocketWrapper().getUDPSocket();

        if (MediaType.AUDIO == mediaType || MediaType.VIDEO == mediaType)
        {
            rtcpPair = stream.getComponent(Component.RTCP).getSelectedPair();
            rtcpSocket = rtcpPair.getIceSocketWrapper().getUDPSocket();
        }

        // We set 'rtcpmux' for the "DATA" connector, in order to prevent
        // attempts to connect a DTLS client for an nonexistent RTCP component.
        streamConnector
                = new DefaultStreamConnector(rtpSocket,
                                             rtcpSocket,
                                             MediaType.DATA.equals(mediaType));

        streamConnectors.put(mediaType, streamConnector);

        return streamConnector;
    }
}
