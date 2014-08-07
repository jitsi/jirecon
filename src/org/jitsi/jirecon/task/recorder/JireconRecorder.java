/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.recorder;

import java.util.List;
import java.util.Map;

import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.task.JireconEndpoint;
import org.jitsi.jirecon.task.JireconTaskEventListener;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;

/**
 * <tt>JireconRecorder</tt> is a recorder manager which is used to record media
 * streams and save them into local files.
 * 
 * @author lishunyang
 * 
 */
public interface JireconRecorder
{
    /**
     * Initialize <tt>JireconRecorder</tt>.
     * <p>
     * <strong>Warning:</strong> LibJitsi must be started before calling this
     * method.
     * 
     * @param outputDir decide where to output the files. The directory must be
     *            existed and writable.
     * @param srtpControls is the map between <tt>MediaType</tt> and
     *            <tt>SrtpControl</tt> which is used for SRTP transfer.
     */
    public void init(String outputDir, Map<MediaType, SrtpControl> srtpControls);

    /**
     * Start recording media streams.
     * 
     * @param formatAndPTs
     * @param connectors is the map between <tt>MediaType</tt> and
     *            <tt>StreamConnector</tt>. <tt>JireconRecorder</tt> needs those
     *            connectors to transfer stream data.
     * @param targets is the map between <tt>MediaType</tt> and
     *            <tt>MediaStreamTarget</tt>. Every target indicates a media
     *            source.
     * @throws OperationFailedException if some operation failed and the
     *             recording is aborted.
     */
    public void startRecording(
        Map<MediaType, Map<MediaFormat, Byte>> formatAndPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets)
        throws OperationFailedException;

    /**
     * Stop the recording.
     */
    public void stopRecording();

    /**
     * Add <tt>JireconTaskEvent</tt> listener.
     * 
     * @param listener
     */
    public void addTaskEventListener(JireconTaskEventListener listener);

    /**
     * Remove <tt>JireconTaskEvent</tt> listener.
     * 
     * @param listener
     */
    public void removeTaskEventListener(JireconTaskEventListener listener);

    /**
     * Get local ssrcs of each <tt>MediaType</tt>.
     * 
     * @return Map between <tt>MediaType</tt> and ssrc.
     */
    public Map<MediaType, Long> getLocalSsrcs();

    public void setEndpoints(List<JireconEndpoint> endpoints);
}
