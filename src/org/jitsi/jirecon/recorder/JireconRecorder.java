/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

import java.io.IOException;
import java.util.Map;

import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.jirecon.JireconEventListener;
import org.jitsi.jirecon.dtlscontrol.JireconSrtpControlManager;
import org.jitsi.jirecon.session.JireconSessionInfo;
import org.jitsi.jirecon.transport.JireconTransportManager;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.service.neomedia.DtlsControl;
import org.jitsi.service.neomedia.MediaException;
import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaStreamTarget;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.StreamConnector;
import org.jitsi.service.neomedia.format.MediaFormat;

public interface JireconRecorder
{
    public void init(JireconConfiguration configuration, MediaService service,
        JireconSrtpControlManager srtpControlManager);

    public void uninit();

    public void stopReceiving();

    public void stopRecording();

    // public JireconRecorderState getState();

//    public void addEventListener(JireconEventListener listener);

//    public void removeEventListener(JireconEventListener listener);

    public void prepareRecorders();

    public void prepareMediaStreams();

    public void completeMediaStreams(
        Map<MediaFormat, Byte> formatAndDynamicPTs,
        Map<MediaType, StreamConnector> connectors,
        Map<MediaType, MediaStreamTarget> targets);

    public JireconRecorderInfo getRecorderInfo();

    public void startReceiving() throws OperationFailedException;

    public void startRecording() throws IOException, MediaException;
}
