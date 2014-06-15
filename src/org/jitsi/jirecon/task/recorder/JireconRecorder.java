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

public interface JireconRecorder
{
    public void startRecording(Map<MediaFormat, Byte> formatAndDynamicPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets)
        throws OperationFailedException,
        IOException,
        MediaException;

    public void stopRecording();

    public JireconRecorderInfo getRecorderInfo();

    // public JireconRecorderState getState();

    // public void addEventListener(JireconEventListener listener);

    // public void removeEventListener(JireconEventListener listener);
}
