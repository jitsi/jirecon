/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

// TODO: Rewrite those import statements to package import statement.
import java.util.*;

import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.format.MediaFormat;

/**
 * This class is used to pack information of Jingle session.
 * 
 * @author lishunyang
 * 
 */
public class JireconSessionInfo
{
    private String localJid;

    private String remoteJid;

    private String sid;

    private String conferenceJid;

    private JireconSessionState status;

    private JireconSessionState state = JireconSessionState.INIT;

    private Map<MediaType, InfoBox> infoBoxes =
        new HashMap<MediaType, InfoBox>();

    /**
     * Constructor of JingleSessionInfo
     */
    public JireconSessionInfo()
    {
        status = JireconSessionState.INIT;
        for (MediaType mediaType : MediaType.values())
        {
            // Make sure that we only handle audio or video type.
            if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
            {
                continue;
            }

            infoBoxes.put(mediaType, new InfoBox());
        }
    }

    /**
     * Add media formats and bind it with specified type of media.
     * 
     * @param media The media type, video or audio.
     * @param format The format which will be added.
     */
    public void addPayloadType(MediaType media, MediaFormat format,
        byte payloadTypeId)
    {
        infoBoxes.get(media).payloadTypes.put(format, payloadTypeId);
    }

    /**
     * Add remote SSRC and bind it with specified type of media.
     * 
     * @param media The media type, video or audio.
     * @param ssrc The remote SSRC which wll be added.
     */
    public void addRemoteSsrc(MediaType media, String remoteJid, String ssrc)
    {
        if (infoBoxes.get(media).remoteSsrcs.containsKey(remoteJid))
        {
            return;
        }
        infoBoxes.get(media).remoteSsrcs.put(remoteJid, ssrc);
    }

    /**
     * Get the format of a specified media type.
     * 
     * @param media The media type, video or audio.
     * @return
     */
    public Map<MediaFormat, Byte> getPayloadTypes(MediaType media)
    {
        return infoBoxes.get(media).payloadTypes;
    }

    /**
     * Get the remote SSRC of a specified media type.
     * 
     * @param media The media type, video or audio.
     * @return
     */
    public Map<String, String> getRemoteSsrcs(MediaType media)
    {
        return infoBoxes.get(media).remoteSsrcs;
    }

    public void setSessionStatus(JireconSessionState status)
    {
        this.status = status;
    }

    public JireconSessionState getSessionStatus()
    {
        return status;
    }

    public String getConferenceJid()
    {
        return conferenceJid;
    }

    public void setConferenceJid(String conferenceJid)
    {
        this.conferenceJid = conferenceJid;
    }

    public String getLocalJid()
    {
        return localJid;
    }

    public void setLocalJid(String localNode)
    {
        this.localJid = localNode;
    }

    public String getRemoteJid()
    {
        return remoteJid;
    }

    public void setRemoteJid(String remoteNode)
    {
        this.remoteJid = remoteNode;
    }

    public String getSid()
    {
        return sid;
    }

    public void setSid(String sid)
    {
        this.sid = sid;
    }

    public void setRemoteFingerprint(MediaType media, String fingerprint)
    {
        infoBoxes.get(media).remoteFingerprint = fingerprint;
    }

    public String getRemoteFingerprint(MediaType media)
    {
        return infoBoxes.get(media).remoteFingerprint;
    }

    public JireconSessionState getState()
    {
        return state;
    }

    private class InfoBox
    {
        // <format, payloadTypeId>
        public Map<MediaFormat, Byte> payloadTypes =
            new HashMap<MediaFormat, Byte>();

        private Map<String, String> remoteSsrcs = new HashMap<String, String>();

        private String remoteFingerprint;
    }

    public boolean readyTo(JireconSessionEvent evt)
    {
        switch (evt)
        {
        case JOIN_MUC:
            if (JireconSessionState.INIT != state)
                return false;
            break;
        case LEAVE_MUC:
            if (JireconSessionState.IN_CONFERENCE != state)
                return false;
            break;
        case SEND_SESSION_ACCEPT:
            if (JireconSessionState.GOT_SESSION_INIT != state)
                return false;
            break;
        case SEND_SESSION_TERMINATE:
            if (JireconSessionState.CONNECTED != state)
                return false;
            break;
        case WAIT_SESSION_ACK:
            if (JireconSessionState.SENT_SESSION_ACCEPT != state)
                return false;
            break;
        case WAIT_SESSION_INIT:
            if (JireconSessionState.IN_CONFERENCE != state)
                return false;
            break;
        }
        return true;
    }

    public void updateState(JireconSessionEvent evt)
    {
        switch (evt)
        {
        case JOIN_MUC:
            state = JireconSessionState.IN_CONFERENCE;
            break;
        case LEAVE_MUC:
            state = JireconSessionState.INIT;
            break;
        case SEND_SESSION_ACCEPT:
            state = JireconSessionState.SENT_SESSION_ACCEPT;
            break;
        case SEND_SESSION_TERMINATE:
            state = JireconSessionState.IN_CONFERENCE;
            break;
        case WAIT_SESSION_ACK:
            state = JireconSessionState.CONNECTED;
            break;
        case WAIT_SESSION_INIT:
            state = JireconSessionState.GOT_SESSION_INIT;
            break;
        }
    }

    public enum JireconSessionEvent
    {
        JOIN_MUC,
        LEAVE_MUC,
        SEND_SESSION_ACCEPT,
        SEND_SESSION_TERMINATE,
        WAIT_SESSION_INIT,
        WAIT_SESSION_ACK,
    }

    public enum JireconSessionState
    {
        INIT, IN_CONFERENCE, GOT_SESSION_INIT, SENT_SESSION_ACCEPT, CONNECTED,
    }
}
