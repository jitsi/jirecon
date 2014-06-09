/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

import java.io.IOException;
import java.util.Map;

import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.dtlscontrol.*;
import org.jitsi.jirecon.utils.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.format.*;

public interface JireconRecorder
{
    public void init(JireconConfiguration configuration, MediaService service,
        JireconSrtpControlManager srtpControlManager);

    public void uninit();

    public void prepareMediaStreams();

    public void completeMediaStreams(
        Map<MediaFormat, Byte> formatAndDynamicPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets);

    public void startReceiving() throws OperationFailedException;

    public void stopReceiving();

    public void prepareRecorders();

    public void startRecording() throws IOException, MediaException;

    public void stopRecording();

    public JireconRecorderInfo getRecorderInfo();

    // public JireconRecorderState getState();

    // public void addEventListener(JireconEventListener listener);

    // public void removeEventListener(JireconEventListener listener);
}
