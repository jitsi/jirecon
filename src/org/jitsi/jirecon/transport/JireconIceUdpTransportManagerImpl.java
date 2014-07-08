/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.transport;

import java.beans.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidateType;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.ice4j.*;
import org.ice4j.ice.*;
import org.jitsi.jirecon.utils.*;
import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.*;
import org.jitsi.util.Logger;

/**
 * An implementation of <tt>JireconTransportManager</tt>.
 * <p>
 * It mainly used for:
 * <p>
 * 1. Establish ICE connectivity.
 * <p>
 * 2. Create <tt>IceUdpTransportPacketExtension</tt>
 * 
 * @author lishunyang
 * @see JireconTransportManager
 * 
 */
public class JireconIceUdpTransportManagerImpl
    implements JireconTransportManager
{
    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger
        .getLogger(JireconIceUdpTransportManagerImpl.class.getName());

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

    /**
     * The construction method.
     */
    public JireconIceUdpTransportManagerImpl()
    {
        logger.info("init");
        
        iceAgent = new Agent();
        iceAgent.setControlling(false);
        
        ConfigurationService configuration = LibJitsi.getConfigurationService();
        MIN_STREAM_PORT =
            configuration.getInt(JireconConfigurationKey.MIN_STREAM_PORT_KEY,
                -1);
        MAX_STREAM_PORT =
            configuration.getInt(JireconConfigurationKey.MAX_STREAM_PORT_KEY,
                -1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void free()
    {
        iceAgent.free();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbstractPacketExtension getTransportPacketExt(MediaType mediaType)
    {
        logger.info("getTransportPacketExt");
        
        // Now, both audio and video's transport packet extension are same. So I
        // don't use mediaType.
        
        IceUdpTransportPacketExtension transportPE =
            new IceUdpTransportPacketExtension();
        
        transportPE.setPassword(iceAgent.getLocalPassword());
        transportPE.setUfrag(iceAgent.getLocalUfrag());
        
        for (CandidatePacketExtension candidatePE : getLocalCandidatePacketExts(mediaType))
        {
            transportPE.addCandidate(candidatePE);
        }
        
        return transportPE;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * <strong>Warning:</strong> This method is asynchronous, it will return
     * immediately while it doesn't means the ICE connectivity has been
     * established successfully. On the contrary, sometime it will never
     * finished and it doesn't matter only if at least one selected candidate
     * pair has been gotten.
     * 
     */
    @Override
    public void startConnectivityEstablishment()
    {
        logger.info("startConnectivityEstablishment");
        
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    startConnectivityCheck();
                }
                catch (OperationFailedException e)
                {
                    e.printStackTrace();
                }
            }
        }).start();
        
        iceAgent.startConnectivityEstablishment();
    }

    /**
     * Check whether the ICE connectivity has been established successfully.
     * This method seems meaningless.
     * 
     * @throws OperationFailedException if some fatal error occurs.
     */
    private void startConnectivityCheck() 
        throws OperationFailedException
    {
        logger.info("waitForCheckFinished");

        final Object iceProcessingStateSyncRoot = new Object();
        PropertyChangeListener stateChangeListener =
            new PropertyChangeListener()
            {
                public void propertyChange(PropertyChangeEvent evt)
                {
                    Object newValue = evt.getNewValue();

                    if (IceProcessingState.COMPLETED.equals(newValue)
                        || IceProcessingState.FAILED.equals(newValue)
                        || IceProcessingState.TERMINATED.equals(newValue))
                    {
                        if (logger.isTraceEnabled())
                            logger.info("ICE " + newValue);

                        Agent iceAgent = (Agent) evt.getSource();

                        iceAgent.removeStateChangeListener(this);

                        synchronized (iceProcessingStateSyncRoot)
                        {
                            iceProcessingStateSyncRoot.notify();
                        }
                    }
                }
            };

        iceAgent.addStateChangeListener(stateChangeListener);

        // Wait for the connectivity checks to finish if they have been started.
        boolean interrupted = false;

        synchronized (iceProcessingStateSyncRoot)
        {
            while (IceProcessingState.RUNNING.equals(iceAgent.getState()))
            {
                try
                {
                    iceProcessingStateSyncRoot.wait(1000);
                }
                catch (InterruptedException ie)
                {
                    interrupted = true;
                }
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();

        /*
         * Make sure stateChangeListener is removed from iceAgent in case its
         * #propertyChange(PropertyChangeEvent) has never been executed.
         */
        iceAgent.removeStateChangeListener(stateChangeListener);

        /* check the state of ICE processing and throw exception if failed */
        if (IceProcessingState.FAILED.equals(iceAgent.getState()))
        {
            throw new OperationFailedException(
                "Could not establish connection (ICE failed)",
                OperationFailedException.GENERAL_ERROR);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void harvestLocalCandidates() 
        throws OperationFailedException
    {
        logger.info("harvestLocalCandidates");
        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
            {
                continue;
            }

            final IceMediaStream stream = getIceMediaStream(mediaType);
            try
            {
                iceAgent.createComponent(stream, Transport.UDP,
                    MIN_STREAM_PORT, MIN_STREAM_PORT, MAX_STREAM_PORT);
                iceAgent.createComponent(stream, Transport.UDP,
                    MIN_STREAM_PORT, MIN_STREAM_PORT, MAX_STREAM_PORT);
            }
            catch (Exception e)
            {
                throw new OperationFailedException(
                    "Could not create ICE component, " + e.getMessage(),
                    OperationFailedException.GENERAL_ERROR);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void harvestRemoteCandidates(
        Map<MediaType, IceUdpTransportPacketExtension> transportPEs)
    {
        logger.info("harvestRemoteCandidates");
        for (java.util.Map.Entry<MediaType, IceUdpTransportPacketExtension> e : transportPEs
            .entrySet())
        {
            final MediaType mediaType = e.getKey();
            final IceUdpTransportPacketExtension transportPE = e.getValue();
            final IceMediaStream stream = getIceMediaStream(mediaType);

            final String ufrag =
                JinglePacketParser.getTransportUfrag(transportPE);
            if (null != ufrag)
                stream.setRemoteUfrag(ufrag);

            final String password =
                JinglePacketParser.getTransportPassword(transportPE);
            if (null != password)
                stream.setRemotePassword(password);

            List<CandidatePacketExtension> candidates =
                JinglePacketParser.getCandidatePacketExt(transportPE);
            // Sort the remote candidates (host < reflexive < relayed) in order
            // to create first the host, then the reflexive, the relayed
            // candidates and thus be able to set the relative-candidate
            // matching the rel-addr/rel-port attribute.
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

                if (!canReach(component, remoteCandidate))
                    continue;
                component.addRemoteCandidate(remoteCandidate);
            }
        }
    }

    /**
     * Get <tt>IceMediaStream</tt> of specified <tt>MediaType</tt>.
     * 
     * @param mediaType
     * @return <tt>IceMediaStream</tt>
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
    private List<CandidatePacketExtension> getLocalCandidatePacketExts(MediaType mediaType)
    {
        List<CandidatePacketExtension> candidatePEs =
            new ArrayList<CandidatePacketExtension>();

        int id = 1;
        for (LocalCandidate candidate : getLocalCandidates(mediaType))
        {
            CandidatePacketExtension candidatePE =
                new CandidatePacketExtension();
            candidatePE.setComponent(candidate.getParentComponent()
                .getComponentID());
            candidatePE.setFoundation(candidate.getFoundation());
            candidatePE.setGeneration(iceAgent.getGeneration());
            candidatePE.setID(String.valueOf(id++));
            candidatePE.setNetwork(0); // Why it is 0?
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

    // private RemoteCandidate getRelatedCandidate(
    // CandidatePacketExtension candidate, Component component)
    // {
    // if ((candidate.getRelAddr() != null) && (candidate.getRelPort() != -1))
    // {
    // final String relAddr = candidate.getRelAddr();
    // final int relPort = candidate.getRelPort();
    // final TransportAddress relatedAddress =
    // new TransportAddress(relAddr, relPort,
    // Transport.parse(candidate.getProtocol()));
    // return component.findRemoteCandidate(relatedAddress);
    // }
    // return null;
    // }

    /**
     * {@inheritDoc}
     */
    @Override
    public MediaStreamTarget getStreamTarget(MediaType mediaType)
    {
        logger.info("getStreamTarget");
        if (mediaStreamTargets.containsKey(mediaType))
            return mediaStreamTargets.get(mediaType);

        if (mediaType != MediaType.AUDIO && mediaType != MediaType.VIDEO)
            return null;

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
            if (streamTargetAddresses.size() >= 2)
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

    /**
     * {@inheritDoc}
     * <p>
     * <strong>Warning:</strong> This method will wait for the selected
     * candidate pair which should be generated during establish ICE
     * connectivity. However, sometimes selected pair can't be generated
     * forever, in this case, this method will hang.
     */
    @Override
    public StreamConnector getStreamConnector(MediaType mediaType)
        throws OperationFailedException
    {
        logger.info("getStreamConnector");
        if (streamConnectors.containsKey(mediaType))
            return streamConnectors.get(mediaType);
        if (mediaType != MediaType.AUDIO && mediaType != MediaType.VIDEO)
            return null;

        int sumWaitTime = 0;
        while (sumWaitTime <= MAX_WAIT_TIME)
        {
            try
            {
                if (IceProcessingState.TERMINATED == iceAgent.getState())
                    break;

                logger
                    .info("Could not get stream connector, sleep for a while. Already sleep for "
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
            throw new OperationFailedException(
                "Could not get stream connector, it seems that ICE connectivity establishment hung",
                OperationFailedException.GENERAL_ERROR);
        }

        StreamConnector streamConnector = null;
        IceMediaStream stream = getIceMediaStream(mediaType);
        if (null == stream)
        {
            throw new OperationFailedException(
                "Could not get stream connector, ICE media stream was not prepared.",
                OperationFailedException.GENERAL_ERROR);
        }

        final CandidatePair rtpPair =
            stream.getComponent(Component.RTP).getSelectedPair();
        final CandidatePair rtcpPair =
            stream.getComponent(Component.RTCP).getSelectedPair();

        final DatagramSocket rtpSocket =
            rtpPair.getLocalCandidate().getDatagramSocket();
        final DatagramSocket rtcpSocket =
            rtcpPair.getLocalCandidate().getDatagramSocket();

        streamConnector = new DefaultStreamConnector(rtpSocket, rtcpSocket);

        streamConnectors.put(mediaType, streamConnector);

        return streamConnector;
    }

    /**
     * Test whether the remote candidate can reach any local candidate in
     * <tt>Component</tt>.
     * 
     * @param component
     * @param remoteCandidate
     * @return
     */
    private boolean canReach(Component component,
        RemoteCandidate remoteCandidate)
    {
        for (LocalCandidate localCandidate : component.getLocalCandidates())
        {
            if (localCandidate.canReach(remoteCandidate))
                return true;
        }
        return false;
    }
}
