/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.recorder;

import java.util.*;

import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.jitsi.service.neomedia.MediaType;

public class JireconRecorderInfo
{
    private JireconRecorderState state = JireconRecorderState.INIT;

    private Map<MediaType, Long> localSsrcs = new HashMap<MediaType, Long>();

    private String msLabel = UUID.randomUUID().toString();

    public JireconRecorderState getState()
    {
        return state;
    }

    public void addLocalSsrc(MediaType mediaType, Long ssrc)
    {
        localSsrcs.put(mediaType, ssrc);
    }

    public Long getLocalSsrc(MediaType mediaType)
    {
        return localSsrcs.get(mediaType);
    }

    public String getMsLabel()
    {
        return msLabel;
    }

    public String getLabel(MediaType mediaType)
    {
        return mediaType.toString();
    }

    public String getMsid(MediaType mediaType)
    {
        return msLabel + " " + getLabel(mediaType);
    }

    public void updateState(JireconRecorderEvent evt)
    {
        switch (evt)
        {
        case PREPARE_STREAM:
            state = JireconRecorderState.STREAM_READY;
            break;
        case PREPARE_RECORDER:
            state = JireconRecorderState.RECORDER_READY;
            break;
        case START_RECEIVING_STREAM:
            state = JireconRecorderState.RECEIVING_STREAM;
            break;
        case START_RECORDING_STREAM:
            state = JireconRecorderState.RECORDING_STREAM;
            break;
        case STOP_RECEIVING_STREAM:
            state = JireconRecorderState.STREAM_READY;
            break;
        case STOP_RECORDING_STREAM:
            state = JireconRecorderState.RECORDER_READY;
            break;
        }
    }

    public boolean readyTo(JireconRecorderEvent evt)
        throws OperationFailedException
    {
        switch (evt)
        {
        case PREPARE_STREAM:
            if (JireconRecorderState.INIT != state)
                return false;
            break;
        case START_RECEIVING_STREAM:
            if (JireconRecorderState.STREAM_READY != state)
                return false;
            break;
        case PREPARE_RECORDER:
            if (JireconRecorderState.RECEIVING_STREAM != state)
                return false;
            break;
        case START_RECORDING_STREAM:
            if (JireconRecorderState.RECORDER_READY != state)
                return false;
            break;
        case STOP_RECEIVING_STREAM:
            if (JireconRecorderState.RECEIVING_STREAM != state)
                return false;
            break;
        case STOP_RECORDING_STREAM:
            if (JireconRecorderState.RECORDING_STREAM != state)
                return false;
            break;
        }
        return true;
    }

    public enum JireconRecorderEvent
    {
        PREPARE_STREAM,
        PREPARE_RECORDER,
        START_RECEIVING_STREAM,
        STOP_RECEIVING_STREAM,
        START_RECORDING_STREAM,
        STOP_RECORDING_STREAM
    }

    public enum JireconRecorderState
    {
        INIT, STREAM_READY, RECEIVING_STREAM, RECORDER_READY, RECORDING_STREAM,
    }
}
