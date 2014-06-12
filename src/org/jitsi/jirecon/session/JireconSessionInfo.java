/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

// TODO: Rewrite those import statements to package import statement.
import java.util.*;
import java.util.Map.Entry;

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

    // Where we send packet to(actually is mucJid)
    private String remoteJid;

    private String sid;

    private String mucJid;

    private JireconSessionState status;

    private JireconSessionState state = JireconSessionState.INIT;

    // <format, payloadTypeId>
    public Map<MediaFormat, Byte> formatAndPayloadTypes =
        new HashMap<MediaFormat, Byte>();

    // private Map<MediaType, InfoBox> infoBoxes =
    // new HashMap<MediaType, InfoBox>();

    private Map<String, ParticipantInfo> participantsInfo =
        new HashMap<String, ParticipantInfo>();

    /**
     * Constructor of JingleSessionInfo
     */
    public JireconSessionInfo()
    {
        status = JireconSessionState.INIT;
        // for (MediaType mediaType : MediaType.values())
        // {
        // // Make sure that we only handle audio or video type.
        // if (MediaType.AUDIO != mediaType && MediaType.VIDEO != mediaType)
        // {
        // continue;
        // }
        //
        // infoBoxes.put(mediaType, new InfoBox());
        // }
    }

    // public void addPayloadType(MediaType media, MediaFormat format,
    // byte payloadTypeId)
    // {
    // infoBoxes.get(media).payloadTypes.put(format, payloadTypeId);
    // }

    public void addFormatAndPayloadType(MediaFormat format, byte payloadTypeId)
    {
        formatAndPayloadTypes.put(format, payloadTypeId);
    }

    public void setFormatAndPayloadTypes(
        Map<MediaFormat, Byte> formatAndPayloadTypes)
    {
        this.formatAndPayloadTypes = formatAndPayloadTypes;
    }

    // public void addRemoteSsrc(MediaType media, String remoteJid, String ssrc)
    // {
    // if (infoBoxes.get(media).remoteSsrcs.containsKey(remoteJid))
    // {
    // return;
    // }
    // infoBoxes.get(media).remoteSsrcs.put(remoteJid, ssrc);
    // }

    // public Map<MediaFormat, Byte> getPayloadTypes(MediaType media)
    // {
    // return infoBoxes.get(media).payloadTypes;
    // }

    public Map<MediaFormat, Byte> getFormatAndPayloadTypes(MediaType mediaType)
    {
        Map<MediaFormat, Byte> result = new HashMap<MediaFormat, Byte>();
        for (Entry<MediaFormat, Byte> e : formatAndPayloadTypes.entrySet())
        {
            if (e.getKey().getMediaType().equals(mediaType))
                result.put(e.getKey(), e.getValue());
        }

        if (result.size() > 0)
            return result;
        else
            return null;
    }

    public Map<MediaFormat, Byte> getFormatAndPayloadTypes()
    {
        if (formatAndPayloadTypes.size() > 0)
            return formatAndPayloadTypes;
        else
            return null;
    }

    // public Map<String, String> getRemoteSsrcs(MediaType media)
    // {
    // return infoBoxes.get(media).remoteSsrcs;
    // }

    public void setSessionStatus(JireconSessionState status)
    {
        this.status = status;
    }

    public JireconSessionState getSessionStatus()
    {
        return status;
    }

    public String getMucJid()
    {
        return mucJid;
    }

    public void setMucJid(String mucJid)
    {
        this.mucJid = mucJid;
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

    // public void setRemoteFingerprint(MediaType media, String fingerprint)
    // {
    // infoBoxes.get(media).remoteFingerprint = fingerprint;
    // }
    //
    // public String getRemoteFingerprint(MediaType media)
    // {
    // return infoBoxes.get(media).remoteFingerprint;
    // }

    public JireconSessionState getState()
    {
        return state;
    }

    public void setParticipantSsrcs(String participantJid,
        Map<MediaType, String> ssrcs)
    {
        if (!participantsInfo.containsKey(participantJid))
        {
            participantsInfo.put(participantJid, new ParticipantInfo());
        }

        ParticipantInfo info = participantsInfo.get(participantJid);
        info.setSsrcs(ssrcs);
    }

    public Map<String, Map<MediaType, String>> getParticipantsSsrcs()
    {
        Map<String, Map<MediaType, String>> result =
            new HashMap<String, Map<MediaType, String>>();
        for (Entry<String, ParticipantInfo> e : participantsInfo.entrySet())
        {
            result.put(e.getKey(), e.getValue().getSsrcs());
        }

        if (result.size() > 0)
            return result;
        else
            return null;
    }

    public Map<MediaType, String> getParticipantSsrcs(String participantJid)
    {
        if (participantsInfo.containsKey(participantJid))
            return participantsInfo.get(participantJid).getSsrcs();
        else
            return null;
    }

    public void setParticipantFingerprint(String participantJid,
        String fingerprint)
    {
        if (!participantsInfo.containsKey(participantJid))
        {
            participantsInfo.put(participantJid, new ParticipantInfo());
        }

        ParticipantInfo info = participantsInfo.get(participantJid);
        info.setFingerprint(fingerprint);
    }

    public String getParticipantFingerprint(String participantJid)
    {
        if (participantsInfo.containsKey(participantJid))
            return participantsInfo.get(participantJid).getFingerprint();
        else
            return null;
    }

    public class ParticipantInfo
    {
        private String fingerprint;

        private Map<MediaType, String> ssrcs;

        public void setFingerprint(String fingerprint)
        {
            this.fingerprint = fingerprint;
        }

        public void setSsrcs(Map<MediaType, String> ssrcs)
        {
            this.ssrcs = ssrcs;
        }

        public String getFingerprint()
        {
            return fingerprint;
        }

        public Map<MediaType, String> getSsrcs()
        {
            return ssrcs;
        }
    }

    // private class InfoBox
    // {
    // // <format, payloadTypeId>
    // public Map<MediaFormat, Byte> payloadTypes =
    // new HashMap<MediaFormat, Byte>();
    //
    // private Map<String, String> remoteSsrcs = new HashMap<String, String>();
    //
    // private String remoteFingerprint;
    // }

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
