/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.neomedia.recording;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import javax.media.*;
import javax.media.control.*;
import javax.media.format.*;
import javax.media.protocol.*;
import javax.media.rtp.*;
import javax.media.rtp.event.*;

import net.sf.fmj.media.rtp.*;

import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.audiolevel.*;
import org.jitsi.impl.neomedia.codec.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.transform.*;
import org.jitsi.impl.neomedia.transform.fec.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.MediaException; //disambiguation
import org.jitsi.service.neomedia.codec.*;
import org.jitsi.service.neomedia.control.*;
import org.jitsi.service.neomedia.event.*;
import org.jitsi.service.neomedia.recording.*;
import org.jitsi.service.neomedia.recording.Recorder;
import org.jitsi.util.*;

/**
 *
 * @author Vladimir Marinov
 * @author Boris Grozev
 */
public class VideoRecorderImpl
        implements Recorder,
                   ReceiveStreamListener,
                   ActiveSpeakerChangedListener
{

    /**
     * The <tt>Logger</tt> used by the <tt>VideoRecorderImpl</tt> class and its
     * instances for logging output.
     */
    private static final Logger logger
        = Logger.getLogger(VideoRecorderImpl.class);

    //values hard-coded to match chrome
    private static final byte redPayloadType = 116;
    private static final byte ulpfecPayloadType = 117;
    private static final byte vp8PayloadType = 100;
    private static final byte opusPayloadType = 123;
    private static final Format redFormat = new VideoFormat(Constants.RED);
    private static final Format ulpfecFormat = new VideoFormat(Constants.ULPFEC);
    private static final Format vp8RtpFormat = new VideoFormat(Constants.VP8_RTP);
    private static final Format vp8Format = new VideoFormat(Constants.VP8);
    private static final Format opusFormat = new AudioFormat(Constants.OPUS_RTP);

    /**
     * The <tt>ContentDescriptor</tt> to use when saving audio.
     */
    private static final ContentDescriptor AUDIO_CONTENT_DESCRIPTOR
            = new ContentDescriptor(FileTypeDescriptor.MPEG_AUDIO);

    /**
     * The suffix for audio file names.
     */
    private static final String AUDIO_FILENAME_SUFFIX = ".mp3";

    /**
     * The suffix for video file names.
     */
    private static final String VIDEO_FILENAME_SUFFIX = ".webm";

    /**
     * Minimum number of consecutive RTP packets which need to be lost before
     * we restart the recording (in a new file) for a given SSRC.
     */
    private static final int TIMEOUT_PACKETS = 50;

    /**
     * Minimum amount of time (in milliseconds) which needs to pass with no
     * received RTP packets before we restart the recording (in a new file) for
     * a given SSRC.
     */
    private static final int TIMEOUT_MILLIS = 1000;


    /**
     * The RTPTranslator that feeds this video recorder with video signal.
     */
    private RTPTranslator videoRTPTranslator;
    
    /**
     * The custom RTPConnector this instance uses in order to write to output
     * video files.
     */
    private RTPConnectorImpl rtpConnector;
    
    /**
     * Path to the directory where the output video files will be stored.
     */
    private String path;

    /**
     * The <tt>RTCPFeedbackSender</tt> that we use to send RTCP FIR messages,
     * when one of our data sinks requests a keyframe.
     */
    private RTCPFeedbackSender rtcpFeedbackSender;
    
    /**
     * The {@link RTPManager} instance we use in order to get the RTP packets
     * coming from the {@link RTPTranslator} instance ordered by sequence
     * number and depacketized.
     */
    private RTPManager rtpManager;

    /**
     * The instance which should be notified when events related to recordings
     * (such as the start or end of a recording) occur.
     */
    private RecorderEventHandlerImpl eventHandler;

    /**
     * Holds the <tt>ReceiveStreams</tt> added to this instance by
     * {@link #rtpManager} and additional information associated with each one
     * (e.g. the <tt>Processor</tt>, if any, used for it).
     */
    private final HashSet<ReceiveStreamDesc> receiveStreams
            = new HashSet<ReceiveStreamDesc>();

    /**
     * Maps an SSRC to an object containing timing information about the SSRC.
     */
    private final Map<Long, SSRCDesc> ssrcs
        = new HashMap<Long, SSRCDesc>();

    /**
     * Maps an SSRC to an object containing timing information about an RTCP
     * Sender Report with this SSRC.
     */
    private final Map<Long, TimeDesc> senderReports
            = new HashMap<Long, TimeDesc>();

    /**
     * The <tt>ControllerListener</tt> which listens for {@link Processor}
     * <tt>ControllerEvent</tt>s.
     */
    private final ControllerListener processorControllerListener
            = new ControllerListener()
    {
        public void controllerUpdate(ControllerEvent event)
        {
            playerControllerUpdate(event);
        }
    };

    /**
     * The <tt>ActiveSpeakerDetector</tt> which will listen to the audio receive
     * streams of this <tt>VideoRecorderImpl</tt> and notify it about changes to
     * the active speaker via calls to {@link #activeSpeakerChanged(long)}
     */
    private ActiveSpeakerDetector activeSpeakerDetector = null;

    /**
     * Constructor.
     *
     * @param translator the <tt>RTPTranslator</tt> to which this instance will
     * attach in order to record media.
     */
    public VideoRecorderImpl(RTPTranslator translator)
    {
        videoRTPTranslator = translator;
        activeSpeakerDetector = new ActiveSpeakerDetectorImpl();
        activeSpeakerDetector.addActiveSpeakerChangedListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addListener(Listener listener)
    {
        // TODO Auto-generated method stub
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getSupportedFormats()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeListener(Listener listener)
    {
        // TODO Auto-generated method stub
    }

    /**
     * Gets the instance which should be notified when events related to
     * recordings (such as the start or end of a recording) occur.
     */
    public RecorderEventHandler getEventHandler()
    {
        return eventHandler;
    }

    /**
     * Sets the instance which should be notified when events related to
     * recordings (such as the start or end of a recording) occur.
     */
    public void setEventHandler(RecorderEventHandler eventHandler)
    {
        if (this.eventHandler == null
                || (this.eventHandler != eventHandler
                        && ((RecorderEventHandlerImpl)this.eventHandler).handler
                            != eventHandler))
        {
            this.eventHandler = new RecorderEventHandlerImpl(eventHandler);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param format unused, since this implementation records multiple streams
     * using potentially different formats.
     * @param dirname the path to the directory into which this <tt>Recorder</tt>
     * will store the recorded media files.
     *
     */
    @Override
    public void start(String format, String dirname)
            throws IOException,
                   MediaException
    {
        if (logger.isInfoEnabled())
            logger.info("Starting, format=" + format + " " + hashCode());
        path = dirname;

        MediaService mediaService = LibJitsi.getMediaService();

        /*
         * Note that we use only one RTPConnector for both the RTPTranslator 
         * and the RTPManager instances. The RTPTranslator will write to its
         * output streams, and this.rtpManager will read from its input streams.
         */
        rtpConnector = new RTPConnectorImpl(redPayloadType, ulpfecPayloadType);

        /*
         * Create the RTPManager which we will use to order the RTP packets and
         * depacketize the RTP stream
         */
        rtpManager = RTPManager.newInstance();
        rtpManager.addFormat(vp8RtpFormat, vp8PayloadType);
        rtpManager.addFormat(opusFormat, opusPayloadType);
        rtpManager.addReceiveStreamListener(this);

        /*
         * Note: When this.rtpManager sends RTCP sender/receiver reports, they
         * will end up being written to its own input stream. This is not
         * expected to cause problems, but might be something to keep an eye on.
         */
        rtpManager.initialize(rtpConnector);
        
        /*
         * Register a fake call participant that only records the video signal 
         * coming from other participants
         */
        StreamRTPManager streamRTPManager = new StreamRTPManager(
            mediaService.createMediaStream(new MediaDeviceImpl(
                    new CaptureDeviceInfo(), MediaType.VIDEO)), 
            videoRTPTranslator);

        streamRTPManager.initialize(rtpConnector);

        rtcpFeedbackSender = new RTCPFeedbackSender(videoRTPTranslator);

        // this makes videoTranslator change the PT of packets it thinks are
        // opus packets to opusPayloadType just before writing them to us.
        // Hopefully it doesn't have any effect on the other participants...
        ((RTPTranslatorImpl)videoRTPTranslator).addFormat(streamRTPManager, opusFormat, opusPayloadType);

        //((RTPTranslatorImpl)videoRTPTranslator).addFormat(streamRTPManager, redFormat, redPayloadType);
        //((RTPTranslatorImpl)videoRTPTranslator).addFormat(streamRTPManager, ulpfecFormat, ulpfecPayloadType);
        //((RTPTranslatorImpl)videoRTPTranslator).addFormat(streamRTPManager, mediaFormatImpl.getFormat(), vp8PayloadType);
    }

    @Override
    public void stop()
    {
        if (logger.isInfoEnabled())
            logger.info("Stopping " + hashCode());

        if (rtpManager != null)
        {
            rtpManager.dispose();
        }

        synchronized (receiveStreams)
        {
            Set<ReceiveStreamDesc> allStreams
                    = new HashSet<ReceiveStreamDesc>(receiveStreams);
            for (ReceiveStreamDesc desc : allStreams)
                removeReceiveStream(desc);
        }

        //remove yourself from videoRTPTransltor
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMute(boolean mute)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFilename()
    {
        return null;
    }

    /**
     * {@link #rtpManager} will use this to notify us of
     * <tt>ReceiveStreamEvent</tt>s.
     */
    @Override
    public void update(ReceiveStreamEvent event)
    {
        if (event instanceof NewReceiveStreamEvent)
        {
            ReceiveStream receiveStream = event.getReceiveStream();
            ReceiveStreamDesc receiveStreamDesc
                    = findReceiveStream(receiveStream);

            if (receiveStreamDesc != null)
            {
                logger.warn("NewReceiveStreamEvent for an existing stream.");
                return;
            }
            else
                receiveStreamDesc = new ReceiveStreamDesc(receiveStream);

            long ssrc = receiveStreamDesc.getSSRC();
            if (logger.isInfoEnabled())
                logger.info("New ReceiveStream, ssrc=" + ssrc);

            DataSource dataSource = receiveStream.getDataSource();
            if (dataSource instanceof PushBufferDataSource)
            {
                Format format = null;
                PushBufferDataSource pbds = (PushBufferDataSource) dataSource;
                for (PushBufferStream pbs : pbds.getStreams())
                {
                    if ((format = pbs.getFormat()) != null)
                        break;
                }

                if (format == null)
                {
                    logger.error("Failed to handle new ReceiveStream: "
                                 + "Failed to determine format");
                    return;
                }

                receiveStreamDesc.format = format;
            }
            else
            {
                logger.error("Failed to handle new ReceiveStream: "
                             + "Unsupported DataSource");
                return;
            }


            //create a Processor and configure it
            Processor processor = null;
            try
            {
                processor
                    = Manager.createProcessor(receiveStream.getDataSource());
            }
            catch (NoProcessorException npe)
            {
                logger.error("Failed to create Processor: ", npe);
                return;
            }
            catch (IOException ioe)
            {
                logger.error("Failed to create Processor: ", ioe);
                return;
            }

            processor.addControllerListener(processorControllerListener);
            receiveStreamDesc.processor = processor;

            final int streamCount;
            synchronized (receiveStreams)
            {
                receiveStreams.add(receiveStreamDesc);
                streamCount = receiveStreams.size();
            }

            /*
             * XXX TODO IRBABOON
             * This is a terrible hack which works around a failure to realize()
             * some of the Processor-s for audio streams, when multiple streams
             * start nearly simultaneously. The cause of the problem is currently
             * unknown (and synchronizing all FMJ calls in VideoRecorderImpl
             * does not help).
             * XXX TODO IRBABOON
             */
            if (receiveStreamDesc.format instanceof AudioFormat)
            {
                final Processor p = processor;
                new Thread(){
                    @Override
                    public void run()
                    {
                        // delay configuring the processors for the different
                        // audio streams to decrease the probability that they
                        // run together.
                        try
                        {
                            Thread.sleep(250 * (streamCount - 1));
                        }
                        catch (Exception e) {}

                        p.configure();
                    }
                }.run();
            }
            else
            {
                processor.configure();
            }
        }
        else if (event instanceof TimeoutEvent)
        {
            ReceiveStreamDesc receiveStreamDesc
                    = findReceiveStream(event.getReceiveStream());
            if (receiveStreamDesc != null)
            {
                if (logger.isInfoEnabled())
                {
                    logger.info("ReceiveStream timeout, ssrc="
                            + receiveStreamDesc.getSSRC());
                }

                removeReceiveStream(receiveStreamDesc);
            }
        }
        else if (event != null && logger.isInfoEnabled())
        {
            logger.info("Unhandled ReceiveStreamEvent ("
                    + event.getClass().getName()
                    + "): " + event);
        }
    }

    /**
     * Handles events from the <tt>Processor</tt>s that this instance uses to
     * transcode media.
     * @param ev the event to handle.
     */
    private void playerControllerUpdate(ControllerEvent ev)
    {
        if (ev == null || ev.getSourceController() == null)
        {
            return;
        }

        Processor processor = (Processor) ev.getSourceController();
        ReceiveStreamDesc desc = findReceiveStream(processor);

        if (desc == null)
        {
            logger.warn("Event from an orphaned processor, ignoring: " + ev);
            return;
        }

        if (ev instanceof ConfigureCompleteEvent)
        {
            if (logger.isInfoEnabled())
            {
                logger.info("Configured processor for ReceiveStream ssrc="
                                    + desc.getSSRC() + " (" + desc.format + ")");
            }

            boolean audio = desc.format instanceof AudioFormat;

            if (audio)
            {
                ContentDescriptor cd =
                        processor.setContentDescriptor(AUDIO_CONTENT_DESCRIPTOR);
                if (!AUDIO_CONTENT_DESCRIPTOR.equals(cd))
                {
                    logger.error("Failed to set the Processor content "
                        + "descriptor to " + AUDIO_CONTENT_DESCRIPTOR
                        + ". Actual result: " + cd);
                    removeReceiveStream(desc);
                    return;
                }
            }

            for (TrackControl track : processor.getTrackControls())
            {
                Format trackFormat = track.getFormat();

                if (audio)
                {
                    SilenceEffect silenceEffect;
                    if (Constants.OPUS_RTP.equals(desc.format.getEncoding()))
                    {
                        silenceEffect = new SilenceEffect(48000);
                    }
                    else
                    {
                        // We haven't tested that the RTP timestamps survive
                        // the journey through the chain when codecs other than
                        // opus are in use, so for the moment we rely on FMJ's
                        // timestamps for non-opus formats.
                        silenceEffect = new SilenceEffect();
                    }

                    desc.silenceEffect = silenceEffect;
                    final long ssrc = desc.getSSRC();
                    AudioLevelEffect audioLevelEffect = new AudioLevelEffect();
                    audioLevelEffect.setAudioLevelListener(
                            new SimpleAudioLevelListener()
                            {
                                @Override
                                public void audioLevelChanged(int level)
                                {
                                    activeSpeakerDetector.levelChanged(ssrc,level);
                                }
                            }
                    );

                    try
                    {
                        // We add an effect, which will insert "silence" in
                        // place of lost packets.
                        track.setCodecChain(new Codec[]{silenceEffect, audioLevelEffect});
                    }
                    catch (UnsupportedPlugInException upie)
                    {
                        logger.warn("Failed to insert silence effect: " + upie);
                        // But do go on, a recording without extra silence is
                        // better than nothing ;)
                    }
                }
                else
                {
                    // transcode vp8/rtp to vp8 (i.e. depacketize vp8)
                    if (trackFormat.matches(vp8RtpFormat))
                        track.setFormat(vp8Format);
                    else
                    {
                        logger.error("Unsupported track format: " + trackFormat
                                             + " for ssrc=" + desc.getSSRC());
                        // we currently only support vp8
                        removeReceiveStream(desc);
                        return;
                    }
                }
            }

            processor.realize();
        }
        else if (ev instanceof RealizeCompleteEvent)
        {
            desc.dataSource = processor.getDataOutput();

            long ssrc = desc.getSSRC();
            boolean audio = desc.format instanceof AudioFormat;
            String suffix = audio ? AUDIO_FILENAME_SUFFIX
                                  : VIDEO_FILENAME_SUFFIX;

            // XXX '\' on windows?
            String filename = getNextFilename(path + "/" + ssrc, suffix);
            desc.filename = filename;

            DataSink dataSink;
            if (audio)
            {
                try
                {
                    dataSink
                        = Manager.createDataSink(desc.dataSource,
                                                 new MediaLocator(
                                                         "file:" + filename));
                }
                catch (NoDataSinkException ndse)
                {
                    logger.error("Could not create DataSink: " + ndse);
                    removeReceiveStream(desc);
                    return;
                }

            }
            else
            {
                dataSink = new WebmDataSink(filename, desc.dataSource);
            }

            if (logger.isInfoEnabled())
                logger.info("Created DataSink (" + dataSink + ") for SSRC="
                            + ssrc + ". Output filename: " + filename);
            try
            {
                dataSink.open();
            }
            catch (IOException e)
            {
                logger.error("Failed to open DataSink (" + dataSink + ") for"
                             + " SSRC=" + ssrc + ": " + e);
                removeReceiveStream(desc);
                return;
            }

            if (!audio)
            {
                final WebmDataSink webmDataSink = (WebmDataSink) dataSink;
                webmDataSink.setSsrc(ssrc);
                webmDataSink.setEventHandler(eventHandler);
                webmDataSink.setKeyFrameControl(new KeyFrameControlAdapter()
                {
                    @Override
                    public boolean requestKeyFrame(
                            boolean urgent)
                    {
                        return requestFIR(webmDataSink);
                    }
                });
            }

            try
            {
                dataSink.start();
            }
            catch (IOException e)
            {
                logger.error("Failed to start DataSink (" + dataSink + ") for"
                             + " SSRC=" + ssrc + ". " + e);
                removeReceiveStream(desc);
                return;
            }

            if (logger.isInfoEnabled())
                logger.info("Started DataSink for SSRC=" + ssrc);

            desc.dataSink = dataSink;

            if (audio)
                audioRecordingStarted(ssrc, filename);

            processor.start();
        }
        else if (logger.isDebugEnabled())
        {
            logger.debug("Unhandled ControllerEvent from the Processor for ssrc="
                    + desc.getSSRC() + ": " + ev);
        }
    }
      
    private void audioRecordingStarted(long ssrc, String filename)
    {
        SSRCDesc ssrcDesc = ssrcs.get(ssrc);
        TimeDesc timeDesc = (ssrcDesc == null ? null : ssrcDesc.firstRtpTime);
        if (timeDesc == null)
        {
            logger.error("Unable to find timing information about SSRC: " + ssrc);
            return;
        }

        RecorderEvent event = new RecorderEvent();
        event.setType(RecorderEvent.Type.RECORDING_STARTED);
        event.setMediaType(MediaType.AUDIO);
        event.setSsrc(ssrc);
        event.setFilename(filename);

        event.setInstant(timeDesc.localTime);
        event.setRtpTimestamp(timeDesc.rtpTime);

        if (eventHandler != null)
        {
            eventHandler.handleEvent(event);
        }
    }

    /**
     * Handles a request from a specific <tt>DataSink</tt> to request a keyframe
     * by sending an RTCP feedback FIR message to the media source.
     *
     * @param dataSink the <tt>DataSink</tt> which requests that a keyframe be
     * requested with a FIR message.
     *
     * @return <tt>true</tt> if a keyframe was successfully requested,
     * <tt>false</tt> otherwise
     */
    private boolean requestFIR(WebmDataSink dataSink)
    {
        ReceiveStreamDesc desc = findReceiveStream(dataSink);
        if (desc != null && rtcpFeedbackSender != null)
        {
            return rtcpFeedbackSender.requestFIR(desc.getSSRC());
        }

        return false;
    }

    /**
     * Returns "prefix"+"suffix" if the file with this name does not exist.
     * Otherwise, returns the first inexistant filename of the form
     * "prefix-"+i+"suffix", for an integer i. i is bounded by 100 to prevent
     * hanging, and on failure to find an inexistant filename the method will
     * return null.
     *
     * @param prefix
     * @param suffix
     * @return
     */
    private String getNextFilename(String prefix, String suffix)
    {
        if (!new File(prefix + suffix).exists())
            return prefix + suffix;

        int i = 1;
        String s;
        do
        {
            s = prefix + "-" + i + suffix;
            if (!new File(s).exists())
                return s;
            i++;
        }
        while (i < 100); //don't hang indefinitely...

        return null;
    }

    /**
     * Finds the <tt>ReceiveStreamDesc</tt> with a particular
     * <tt>ReceiveStream</tt>.
     * @param receiveStream The <tt>ReceiveStream</tt> to match.
     * @return the <tt>ReceiveStreamDesc</tt> with a particular
     * <tt>ReceiveStream</tt>, or <tt>null</tt>.
     */
    private ReceiveStreamDesc findReceiveStream(ReceiveStream receiveStream)
    {
        if (receiveStream == null)
            return null;

        synchronized (receiveStreams)
        {
            for (ReceiveStreamDesc r : receiveStreams)
                if (receiveStream.equals(r.receiveStream))
                    return r;
        }

        return null;
    }

    /**
     * Finds the <tt>ReceiveStreamDesc</tt> with a particular
     * <tt>Processor</tt>
     * @param processor The <tt>Processor</tt> to match.
     * @return the <tt>ReceiveStreamDesc</tt> with a particular
     * <tt>Processor</tt>, or <tt>null</tt>.
     */
    private ReceiveStreamDesc findReceiveStream(Processor processor)
    {
        if (processor == null)
            return null;

        synchronized (receiveStreams)
        {
            for (ReceiveStreamDesc r : receiveStreams)
                if (processor.equals(r.processor))
                    return r;
        }

        return null;
    }

    /**
     * Finds the <tt>ReceiveStreamDesc</tt> with a particular
     * <tt>DataSink</tt>
     * @param dataSink The <tt>DataSink</tt> to match.
     * @return the <tt>ReceiveStreamDesc</tt> with a particular
     * <tt>DataSink</tt>, or <tt>null</tt>.
     */
    private ReceiveStreamDesc findReceiveStream(DataSink dataSink)
    {
        if (dataSink == null)
            return null;

        synchronized (receiveStreams)
        {
            for (ReceiveStreamDesc r : receiveStreams)
                if (dataSink.equals(r.dataSink))
                    return r;
        }

        return null;
    }

    /**
     * Finds the <tt>ReceiveStreamDesc</tt> with a particular
     * SSRC.
     * @param ssrc The SSRC to match.
     * @return the <tt>ReceiveStreamDesc</tt> with a particular
     * SSRC, or <tt>null</tt>.
     */
    private ReceiveStreamDesc findReceiveStream(long ssrc)
    {
        synchronized (receiveStreams)
        {
            for (ReceiveStreamDesc r : receiveStreams)
                if (ssrc == r.getSSRC())
                    return r;
        }

        return null;
    }

    /**
     * Removes a particular <tt>ReceiveStreamDesc</tt> from the set of
     * <tt>ReceiveStreamDesc</tt>s managed by this <tt>VideoRecorderImpl</tt>.
     *
     * @param desc the <tt>ReceiveStreamDesc</tt> to remove.
     */
    private void removeReceiveStream(ReceiveStreamDesc desc)
    {
        long ssrc = desc.getSSRC();
        if (logger.isInfoEnabled())
        {
            logger.info("Removing ReceiveStream ssrc=" + ssrc);
        }

        synchronized (receiveStreams)
        {
            receiveStreams.remove(desc);
        }

        // XXX it isn't clear what we are supposed to stop/close here
        if (desc.dataSink != null)
        {
            try
            {
                desc.dataSink.stop();
            }
            catch (IOException e)
            {
            logger.error("Failed to stop DataSink " + e);
            }

            desc.dataSink.close();
        }

        /*
        if (desc.format instanceof AudioFormat)
        {
            RecorderEvent event = new RecorderEvent();
            event.setType(RecorderEvent.Type.RECORDING_ENDED);
            event.setMediaType(MediaType.AUDIO);
            event.setFilename(desc.filename);
            event.setSsrc(desc.getSSRC());
            // the exact instant isn't significant
            event.setInstant(System.currentTimeMillis());

            if (eventHandler != null)
                eventHandler.handleEvent(event);
        } */
        // For video, the DataSink sends the RecorderEvent

        if (desc.processor != null)
        {
            desc.processor.stop();
            desc.processor.close();
        }

        DataSource dataSource = desc.receiveStream.getDataSource();
        if (dataSource != null)
        {
            try
            {
                dataSource.stop();
            }
            catch (IOException ioe)
            {
                logger.warn("Failed to stop DataSource");
            }
            dataSource.disconnect();
        }

        synchronized (ssrcs)
        {
            ssrcs.remove(ssrc);
        }

        synchronized (senderReports)
        {
            senderReports.remove(ssrc);
        }
    }

    /**
     * Gets the SSRC of a <tt>ReceiveStream</tt> as a (non-negative)
     * <tt>long</tt>.
     *
     * FMJ stores the 32-bit SSRC values in <tt>int</tt>s, and the
     * <tt>ReceiveStream.getSSRC()</tt> implementation(s) don't take care of
     * converting the negative <tt>int</tt> values sometimes resulting from
     * reading of a 32-bit field into the correct unsigned <tt>long</tt> value.
     * So do the conversion here.
     *
     * @param receiveStream the <tt>ReceiveStream</tt> for which to get the SSRC.
     * @return the SSRC of <tt>receiveStream</tt> an a (non-negative)
     * <tt>long</tt>.
     */
    private long getReceiveStreamSSRC(ReceiveStream receiveStream)
    {
        return 0xffffffffL & receiveStream.getSSRC();
    }

    /**
     * Notifies this <tt>VideoRecorderImpl</tt> that the audio
     * <tt>ReceiveStream</tt> considered active has changed, and that the new
     * active stream has SSRC <tt>ssrc</tt>.
     * @param ssrc the SSRC of the new active stream.
     */
    @Override
    public void activeSpeakerChanged(long ssrc)
    {
        if (eventHandler !=null)
        {
            RecorderEvent e = new RecorderEvent();
            e.setAudioSsrc(ssrc);
            e.setInstant(System.currentTimeMillis());
            e.setType(RecorderEvent.Type.SPEAKER_CHANGED);
            e.setMediaType(MediaType.VIDEO);
            eventHandler.handleEvent(e);
        }
    }

    /**
     * Handles a received RTCP Sender Report.
     *
     * Saves timing information (the RTP timestamp and the NTP timestamp) for
     * the first SR for each observed SSRC.
     *
     * @param pkt the packet to handle.
     */
    private void handleSenderReport(RawPacket pkt)
    {
        long ssrc = pkt.getRTCPSSRC() & 0xffffffffL;
        TimeDesc timeDesc = null;

        timeDesc = senderReports.get(ssrc);

        if (timeDesc != null)
        {
            // we already have a sender report for this SSRC
            return;
        }

        timeDesc = new TimeDesc();
        long sec = pkt.read32BitsAsLong(8);
        long fract = pkt.read32BitsAsLong(12);

        timeDesc.ntpTime = sec + (((double)fract) / (1L<<32));
        timeDesc.rtpTime = pkt.read32BitsAsLong(16);

        synchronized (senderReports)
        {
            if (senderReports.get(ssrc) != null)
                return;
            senderReports.put(ssrc, timeDesc);
        }

        // we just saved the first SR for this SSRC. We can now compute the
        // SSRCDesc's ntpTime, so do it and update it
        SSRCDesc ssrcDesc = ssrcs.get(ssrc);
        if (ssrcDesc != null)
        {
            ssrcDesc.firstRtpTime.ntpTime
                    = getNtpTime(ssrc, ssrcDesc.firstRtpTime.rtpTime);
        }


        if (eventHandler != null)
            eventHandler.newSenderReportAvailable();
    }

    /**
     * Handles an incoming RTP packet. Saves timing information.
     *
     * @param pkt the packet to handle.
     */
    private void handleRtpPacket(RawPacket pkt)
    {
        long ssrc = pkt.getSSRC() & 0xffffffffL;
        SSRCDesc ssrcDesc = ssrcs.get(ssrc);

        if (ssrcDesc != null)
        {
            // not the first RTP packet for the SSRC, check if we need to
            // restart a recording because of a big "hole"
            long diffMs = System.currentTimeMillis() - ssrcDesc.lastRtpTime;
            long diffPkts = pkt.getSequenceNumber() - ssrcDesc.lastRtpSeq;
            if (diffPkts < 0)
                diffPkts += (1<<16);

            if ( diffMs > TIMEOUT_MILLIS && diffPkts > TIMEOUT_PACKETS)
            {
                resetRecording(ssrc, 0xffffffffL & pkt.getTimestamp());
            }

            // update the info for the SSRC with the current packet.
            ssrcDesc.lastRtpSeq = pkt.getSequenceNumber();
            ssrcDesc.lastRtpTime = System.currentTimeMillis();
            return;
        }

        // new ssrc
        synchronized (ssrcs)
        {
            TimeDesc timeDesc = new TimeDesc();
            timeDesc.rtpTime = pkt.getTimestamp() & 0xffffffffL;
            timeDesc.localTime = System.currentTimeMillis();
            timeDesc.ntpTime = -1.0;

            ssrcDesc = new SSRCDesc();
            ssrcDesc.firstRtpTime = timeDesc;
            ssrcDesc.lastRtpTime = System.currentTimeMillis();
            ssrcDesc.lastRtpSeq = pkt.getSequenceNumber();

            ssrcs.put(ssrc, ssrcDesc);
        }

    }

    /**
     * Restarts the recording for a specific SSRC.
     * @param ssrc the SSRC for which to restart recording.
     * @param rtpTime the RTP timestamp of the next RTP packet (the first
     * RTP packet of the new recording).
     */
    private void resetRecording(long ssrc, long rtpTime)
    {
        ReceiveStreamDesc receiveStream = findReceiveStream(ssrc);

        //we only restart audio recordings
        if (receiveStream != null
                && receiveStream.format instanceof AudioFormat)
        {
            String newFilename
                    = getNextFilename(path + "/" + ssrc, AUDIO_FILENAME_SUFFIX);

            if (logger.isInfoEnabled())
            {
                logger.info("Restarting recording for SSRC=" + ssrc
                                    + ". New filename: "+ newFilename);
            }

            receiveStream.dataSink.close();
            receiveStream.dataSink = null;

            // reset the silence effect, so that it doesn't try to insert all
            // the insert silence for the time which is not recorded.
            receiveStream.silenceEffect.resetSilence();

            // flush the FMJ jitter buffer
            DataSource ds = receiveStream.receiveStream.getDataSource();
            if (ds instanceof net.sf.fmj.media.protocol.rtp.DataSource)
                ((net.sf.fmj.media.protocol.rtp.DataSource)ds).flush();

            try
            {
                receiveStream.dataSink
                    = Manager.createDataSink(receiveStream.dataSource,
                                             new MediaLocator(
                                                     "file:" + newFilename));
            }
            catch (NoDataSinkException ndse)
            {
                logger.warn("Could not reset recording for SSRC=" + ssrc + ": "
                                    + ndse);
                removeReceiveStream(receiveStream);
            }

            try
            {
                receiveStream.dataSink.open();
                receiveStream.dataSink.start();
            }
            catch (IOException ioe)
            {
                logger.warn("Could not reset recording for SSRC=" + ssrc + ": "
                                    + ioe);
                removeReceiveStream(receiveStream);
            }

            double ntpTime = getNtpTime(ssrc, rtpTime);
            SSRCDesc ssrcDesc = ssrcs.get(ssrc);
            if (ssrcDesc == null || ssrcDesc.firstRtpTime == null
                    || ssrcDesc.firstRtpTime.ntpTime == -1.0)
            {
                logger.error("Failed to send RecordingEvent after restarting"
                                     + " recording for SSRC=" + ssrc);
                return;
            }


            double diff = ntpTime - ssrcDesc.firstRtpTime.ntpTime;
            long diffMs = Math.round(diff * 1000);

            RecorderEvent event = new RecorderEvent();
            event.setType(RecorderEvent.Type.RECORDING_STARTED);
            event.setFilename(newFilename);
            event.setSsrc(ssrc);
            event.setMediaType(MediaType.AUDIO);
            event.setInstant(ssrcDesc.firstRtpTime.localTime + diffMs);
            event.setRtpTimestamp(rtpTime);
            event.setNtpTime(ntpTime);

            if (eventHandler != null)
                eventHandler.handleEvent(event);
        }
    }

    /**
     * Returns the SSRC source's wallclock time corresponding to the RTP
     * timestamp <tt>rtpTime</tt>.
     * If case of failure, returns <tt>-1.0</tt>.
     *
     * @param ssrc the SSRC associated with <tt>rtpTime</tt>. Used in order
     * to find an RTCP sender report.
     * @param rtpTime the RTP timestamp for which to return the source's
     * walltime.
     * @return the SSRC source's wallclock time corresponding to the RTP
     * timestamp <tt>rtpTime</tt>.
     */
    private double getNtpTime(long ssrc, long rtpTime)
    {
        ReceiveStreamDesc receiveStream = findReceiveStream(ssrc);
        if (receiveStream == null)
            return -1.0;

        int rtpClockRate = -1;
        if (opusFormat.matches(receiveStream.format))
            rtpClockRate = 48000;
        else if (receiveStream.format instanceof AudioFormat)
            rtpClockRate
                    = (int) ((AudioFormat) receiveStream.format)
                                .getSampleRate();
        else
            rtpClockRate = 90000; // video -- must be vp8

        if (rtpClockRate <= 0)
            return -1.0;

        TimeDesc sr = senderReports.get(ssrc);
        if (sr == null)
            return -1.0;

        // difference in rtp clock cycles
        long diffRtp = rtpTime - sr.rtpTime;

        // handle wrapping at 2^32
        if (diffRtp > (1L<<31))
            diffRtp -= (1L<<32);
        else if (diffRtp < -(1L<<31))
            diffRtp += (1L<<32);

        double diffS = ((double) diffRtp) / rtpClockRate;
        double ntpTime = sr.ntpTime + diffS;

        return ntpTime;
    }

    /**
     * The <tt>RTPConnector</tt> implementation used by this
     * <tt>VideoRecorderImpl</tt>.
     */
    private class RTPConnectorImpl
            implements RTPConnector
    {

        private PushSourceStreamImpl controlInputStream;

        private OutputDataStreamImpl controlOutputStream;

        private PushSourceStreamImpl dataInputStream;

        private OutputDataStreamImpl dataOutputStream;

        private SourceTransferHandler dataTransferHandler;

        private SourceTransferHandler controlTransferHandler;

        private RawPacket pendingDataPacket = new RawPacket();

        private RawPacket pendingControlPacket = new RawPacket();

        private PacketTransformer rtpPacketTransformer = null;

        private RTPConnectorImpl(byte redPT, byte ulpfecPT)
        {
            TransformEngine transformEngine
                = new TransformEngineChain(
                    new TransformEngine[]
                            {
                                    new FECTransformEngine(ulpfecPT, (byte)-1),
                                    new REDTransformEngine(redPT, (byte)-1)
                            });

            rtpPacketTransformer = transformEngine.getRTPTransformer();
        }

        private RTPConnectorImpl()
        {
        }

        @Override
        public void close()
        {
            try
            {
                if (dataOutputStream != null)
                    dataOutputStream.close();
                if (controlOutputStream != null)
                    controlOutputStream.close();
            }
            catch (IOException ioe)
            {
                throw new UndeclaredThrowableException(ioe);
            }
        }

        @Override
        public PushSourceStream getControlInputStream() throws IOException
        {
            if (controlInputStream == null)
            {
                controlInputStream = new PushSourceStreamImpl(true);
            }

            return controlInputStream;
        }

        @Override
        public OutputDataStream getControlOutputStream() throws IOException
        {
            if (controlOutputStream == null)
            {
                controlOutputStream = new OutputDataStreamImpl(true);
            }

            return controlOutputStream;
        }

        @Override
        public PushSourceStream getDataInputStream() throws IOException
        {
            if (dataInputStream == null)
            {
                dataInputStream = new PushSourceStreamImpl(false);
            }

            return dataInputStream;
        }

        @Override
        public OutputDataStream getDataOutputStream() throws IOException
        {
            if (dataOutputStream == null)
            {
                dataOutputStream = new OutputDataStreamImpl(false);
            }

            return dataOutputStream;
        }

        @Override
        public double getRTCPBandwidthFraction()
        {
            return -1;
        }

        @Override
        public double getRTCPSenderBandwidthFraction()
        {
            return -1;
        }

        @Override
        public int getReceiveBufferSize()
        {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public int getSendBufferSize()
        {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public void setReceiveBufferSize(int arg0) throws IOException
        {
            // TODO Auto-generated method stub

        }

        @Override
        public void setSendBufferSize(int arg0) throws IOException
        {
            // TODO Auto-generated method stub
        }

        private class OutputDataStreamImpl
                implements OutputDataStream
        {
            boolean isControlStream;
            private RawPacket[] rawPacketArray = new RawPacket[1];

            public OutputDataStreamImpl(boolean isControlStream)
            {
                this.isControlStream = isControlStream;
            }

            public int write(byte[] buffer, int offset, int length)
            {
                RawPacket pkt = rawPacketArray[0];
                if (pkt == null)
                    pkt = new RawPacket();
                rawPacketArray[0] = pkt;

                byte[] pktBuf = pkt.getBuffer();
                if (pktBuf == null || pktBuf.length < length)
                {
                    pktBuf = new byte[length];
                    pkt.setBuffer(pktBuf);
                }
                System.arraycopy(buffer, offset, pktBuf, 0, length);
                pkt.setOffset(0);
                pkt.setLength(length);

                if (isControlStream)
                {
                   if (pkt.getRTCPPayloadType() == 200 /* SR */)
                        handleSenderReport(pkt);
                }
                else
                {
                    handleRtpPacket(pkt);
                }

                PacketTransformer packetTransformer =
                        (isControlStream) ? null : rtpPacketTransformer;

                if (packetTransformer != null)
                    rawPacketArray
                            = packetTransformer.reverseTransform(rawPacketArray);

                SourceTransferHandler transferHandler;
                PushSourceStream pushSourceStream;

                try
                {
                    if (isControlStream)
                    {
                        transferHandler = controlTransferHandler;
                        pushSourceStream = getControlInputStream();
                    }
                    else
                    {
                        transferHandler = dataTransferHandler;
                        pushSourceStream = getDataInputStream();
                    }
                }
                catch (IOException ioe)
                {
                    throw new UndeclaredThrowableException(ioe);
                }

                for (int i = 0; i < rawPacketArray.length; i++)
                {
                    RawPacket packet = rawPacketArray[i];

                    //keep the first element for reuse
                    if (i != 0)
                        rawPacketArray[i] = null;

                    if (packet != null)
                    {
                        if (isControlStream)
                            pendingControlPacket = packet;
                        else
                            pendingDataPacket = packet;

                        if (transferHandler != null)
                        {
                            transferHandler.transferData(pushSourceStream);
                        }
                    }
                }

                return length;
            }

            public void close() throws IOException
            {
            }
        }

        /**
         * A dummy implementation of {@link PushSourceStream}.
         * @author Vladimir Marinov
         */
        private class PushSourceStreamImpl implements PushSourceStream
        {

            private boolean isControlStream = false;

            public PushSourceStreamImpl(boolean isControlStream)
            {
                this.isControlStream = isControlStream;
            }

            /**
             * Not implemented because there are currently no uses of the underlying
             * functionality.
             */
            @Override
            public boolean endOfStream()
            {
                return false;
            }

            /**
             * Not implemented because there are currently no uses of the underlying
             * functionality.
             */
            @Override
            public ContentDescriptor getContentDescriptor()
            {
                return null;
            }

            /**
             * Not implemented because there are currently no uses of the underlying
             * functionality.
             */
            @Override
            public long getContentLength()
            {
                return 0;
            }

            /**
             * Not implemented because there are currently no uses of the underlying
             * functionality.
             */
            @Override
            public Object getControl(String arg0)
            {
                return null;
            }

            /**
             * Not implemented because there are currently no uses of the underlying
             * functionality.
             */
            @Override
            public Object[] getControls()
            {
                return null;
            }

            /**
             * Not implemented because there are currently no uses of the underlying
             * functionality.
             */
            @Override
            public int getMinimumTransferSize()
            {
                if (isControlStream)
                {
                    if (pendingControlPacket.getBuffer() != null)
                    {
                        return pendingControlPacket.getLength();
                    }
                }
                else
                {
                    if (pendingDataPacket.getBuffer() != null)
                    {
                        return pendingDataPacket.getLength();
                    }
                }

                return 0;
            }

            @Override
            public int read(byte[] buffer, int offset, int length)
                    throws IOException
            {

                RawPacket pendingPacket;
                if (isControlStream)
                {
                    pendingPacket = pendingControlPacket;
                }
                else
                {
                    pendingPacket = pendingDataPacket;
                }
                int bytesToRead = 0;
                byte[] pendingPacketBuffer = pendingPacket.getBuffer();
                if (pendingPacketBuffer != null)
                {
                    int pendingPacketLength = pendingPacket.getLength();
                    bytesToRead = length > pendingPacketLength ?
                            pendingPacketLength: length;
                    System.arraycopy(
                            pendingPacketBuffer,
                            pendingPacket.getOffset(),
                            buffer,
                            offset,
                            bytesToRead);
                }
                return bytesToRead;
            }

            /**
             * {@inheritDoc}
             *
             * We keep the first non-null <tt>SourceTransferHandler</tt> that
             * was set, because we don't want it to be overwritten when we
             * initialize a second <tt>RTPManager</tt> with this
             * <tt>RTPConnector</tt>.
             *
             * See {@link VideoRecorderImpl#start(String, String)}
             */
            @Override
            public void setTransferHandler(
                    SourceTransferHandler transferHandler)
            {
                if (isControlStream)
                {
                    if (RTPConnectorImpl.this.controlTransferHandler == null)
                    {
                        RTPConnectorImpl.this.
                                controlTransferHandler = transferHandler;
                    }
                }
                else
                {
                    if (RTPConnectorImpl.this.dataTransferHandler == null)
                    {
                        RTPConnectorImpl.this.
                                dataTransferHandler = transferHandler;
                    }
                }
            }
        }
    }

    /**
     * Represents a <tt>ReceiveStream</tt> for the purposes of this
     * <tt>VideoRecorderImpl</tt>.
     */
    private class ReceiveStreamDesc
    {
        /**
         * The actual <tt>ReceiveStream</tt> which is represented by this
         * <tt>ReceiveStreamDesc</tt>.
         */
        private ReceiveStream receiveStream;

        /**
         * The <tt>Processor</tt> used to transcode this receive stream into a
         * format appropriate for saving to a file.
         */
        private Processor processor;

        /**
         * The <tt>DataSink</tt> which saves the <tt>this.dataSource</tt> to a
         * file.
         */
        private DataSink dataSink;

        /**
         * The <tt>DataSource</tt> for this receive stream which is to be saved
         * using a <tt>DataSink</tt> (i.e. the <tt>DataSource</tt> "after" all
         * needed transcoding is done).
         */
        private DataSource dataSource;

        /**
         * The name of the file into which this stream is being saved.
         */
        private String filename;

        /**
         * The (original) format of this receive stream.
         */
        private Format format;

        /**
         * The <tt>SilenceEffect</tt> used for this stream (for audio streams
         * only).
         */
        private SilenceEffect silenceEffect;

        private ReceiveStreamDesc(ReceiveStream receiveStream)
        {
            this.receiveStream = receiveStream;
        }

        /**
         * Returns the SSRC of this <tt>ReceiveStreamDesc</tt>.
         * @return the SSRC of this <tt>ReceiveStreamDesc</tt>.
         */
        private long getSSRC()
        {
            return getReceiveStreamSSRC(receiveStream);
        }
    }

    /**
     * An implementation of <tt>RecorderEventHandler</tt> which intercepts
     * <tt>RECORDING_STARTED</tt> events for audio and updates their 'instant'
     * fields with the instant of the first received audio RTP packet before
     * delegating to another underlying <tt>RecorderEventHandler</tt>.
     */
    private class RecorderEventHandlerImpl
        implements RecorderEventHandler
    {
        private RecorderEventHandler handler;

        private final Set<RecorderEvent> pendingEvents
                = new HashSet<RecorderEvent>();

        private RecorderEventHandlerImpl(RecorderEventHandler handler)
        {
            this.handler = handler;
        }

        @Override
        public boolean handleEvent(RecorderEvent event)
        {
            if (event != null
                && RecorderEvent.Type.RECORDING_STARTED.equals(event.getType()))
            {
                if (setNtpTime(event))
                    return handler.handleEvent(event);
                else
                {
                    synchronized (pendingEvents)
                    {
                        pendingEvents.add(event);
                    }
                    return true;
                }
            }

            return handler.handleEvent(event);
        }

        private boolean setNtpTime(RecorderEvent event)
        {
            if (event.getNtpTime() != -1.0)
                return true; //already set

            long ssrc = event.getSsrc();
            long rtpTime = event.getRtpTimestamp();

            if (rtpTime == -1)
            {
                logger.error("No rtp timestamp for event: " + event);
                return false;
            }


            ReceiveStreamDesc receiveStream = findReceiveStream(ssrc);
            if (receiveStream == null)
            {
                logger.error("Not writing a report for an inexistant "
                                     + "ReceiveStream.");
                return false;
            }

            double eventNtpTime = getNtpTime(ssrc, rtpTime);

            if (eventNtpTime == -1.0)
                return false;

            event.setNtpTime(eventNtpTime);
            return true;
        }

        private void newSenderReportAvailable()
        {
            synchronized (pendingEvents)
            {
                for (Iterator<RecorderEvent> i = pendingEvents.iterator();
                     i.hasNext();
                    )
                {
                    RecorderEvent event = i.next();
                    if (setNtpTime(event))
                    {
                        handler.handleEvent(event);
                        i.remove();
                    }
                }
            }
        }

        @Override
        public void close()
        {
            handler.close();
        }

    }

    private class TimeDesc
    {
        /**
         * NTP timestamp (RFC5905) converted to a double representing seconds.
         */
        private double ntpTime;

        /**
         * 32bit RTP timestamp.
         */
        private long rtpTime;

        /**
         * Time as returned by <tt>System.currentTimeMillis()</tt>.
         */
        private long localTime;
    }

    /**
     * Contains information about an SSRC being recorded.
     */
    private class SSRCDesc
    {
        /**
         * The SSRC.
         */
        private long ssrc = -1;

        /**
         * Timing information about the first RTP packet received for this
         * SSRC.
         */
        private TimeDesc firstRtpTime = null;

        /**
         * The RTP timestamp of the last RTP packet with this SSRC received.
         */
        private long lastRtpTime = -1;

        /**
         * The sequence number of the last RTP packet with this SSRC received.
         */
        private long lastRtpSeq = -1;
    }
}