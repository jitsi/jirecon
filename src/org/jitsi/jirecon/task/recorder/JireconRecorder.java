/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.recorder;

import java.util.Map;

import net.java.sip.communicator.service.protocol.OperationFailedException;

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
     * Start recording media streams.
     * 
     * @param formatAndDynamicPTs is the map between <tt>MediaFormat</tt> and
     *            dynamic payload type id. <tt>JireconRecorder</tt> needs it to
     *            recognize those dynamic payload types.
     * @param connectors is the map between <tt>MediaType</tt> and
     *            <tt>StreamConnector</tt>. <tt>JireconRecorder</tt> needs those
     *            connectors to transfer stream data.
     * @param targets is the map between <tt>MediaType</tt> and
     *            <tt>MediaStreamTarget</tt>. Every target indicates a media
     *            source.
     * @throws OperationFailedException if some operation failed and the
     *             recording is aborted.
     */
    public void startRecording(Map<MediaFormat, Byte> formatAndDynamicPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets)
        throws OperationFailedException;

    /**
     * Stop the recording.
     */
    public void stopRecording();
}
