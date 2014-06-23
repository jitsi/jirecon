/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task.recorder;

import java.io.IOException;
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
     * @param formatAndDynamicPTs is the mapping between <tt>MediaFormat</tt>
     *            and dynamic payload type id. <tt>JireconRecorder</tt> needs it
     *            to recognize those dynamic payload types.
     * @param connectors is the mapping between <tt>MediaType</tt> and
     *            <tt>StreamConnector</tt>. <tt>JireconRecorder</tt> needs those
     *            connectors to transfer stream data.
     * @param targets is the mapping between <tt>MediaType</tt> and
     *            <tt>MediaStreamTarget</tt>. Every target indicates a media
     *            source.
     * @throws OperationFailedException if some operation failed.
     * @throws IOException
     * @throws MediaException
     */
    // TODO Merge IOException and MediaException to OperationFailedException, so
    // that it will look better.
    public void startRecording(Map<MediaFormat, Byte> formatAndDynamicPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets)
        throws OperationFailedException,
        IOException,
        MediaException;

    /**
     * Stop the recording.
     */
    public void stopRecording();
}
