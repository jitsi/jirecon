package org.jitsi.jirecon.task;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.format.MediaFormat;

// TODO: The existence of this class is ugly, I should refactor it.
public class JireconTaskSharingInfo
{
    private Map<MediaType, Long> localSsrcs = new HashMap<MediaType, Long>();

    private String msLabel = UUID.randomUUID().toString();

    private String localJid;

    // Where we send packet to(actually is mucJid)
    private String remoteJid;

    private String sid;

    private String mucJid;

    // <format, payloadTypeId>
    public Map<MediaFormat, Byte> formatAndPayloadTypes =
        new HashMap<MediaFormat, Byte>();

    private Map<String, ParticipantInfo> participantsInfo =
        new HashMap<String, ParticipantInfo>();

    public synchronized void addLocalSsrc(MediaType mediaType, Long ssrc)
    {
        localSsrcs.put(mediaType, ssrc);
    }

    public synchronized Long getLocalSsrc(MediaType mediaType)
    {
        return localSsrcs.get(mediaType);
    }

    public synchronized String getMsLabel()
    {
        return msLabel;
    }

    public synchronized String getLabel(MediaType mediaType)
    {
        return mediaType.toString();
    }

    public synchronized String getMsid(MediaType mediaType)
    {
        return msLabel + " " + getLabel(mediaType);
    }

    public synchronized void addFormatAndPayloadType(MediaFormat format,
        byte payloadTypeId)
    {
        formatAndPayloadTypes.put(format, payloadTypeId);
    }

    public synchronized void setFormatAndPayloadTypes(
        Map<MediaFormat, Byte> formatAndPayloadTypes)
    {
        this.formatAndPayloadTypes = formatAndPayloadTypes;
    }

    public synchronized Map<MediaFormat, Byte> getFormatAndPayloadTypes(
        MediaType mediaType)
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

    public synchronized Map<MediaFormat, Byte> getFormatAndPayloadTypes()
    {
        if (formatAndPayloadTypes.size() > 0)
            return formatAndPayloadTypes;
        else
            return null;
    }

    public synchronized String getMucJid()
    {
        return mucJid;
    }

    public synchronized void setMucJid(String mucJid)
    {
        this.mucJid = mucJid;
    }

    public synchronized String getLocalJid()
    {
        return localJid;
    }

    public synchronized void setLocalJid(String localNode)
    {
        this.localJid = localNode;
    }

    public synchronized String getRemoteJid()
    {
        return remoteJid;
    }

    public synchronized void setRemoteJid(String remoteNode)
    {
        this.remoteJid = remoteNode;
    }

    public synchronized String getSid()
    {
        return sid;
    }

    public synchronized void setSid(String sid)
    {
        this.sid = sid;
    }

    public synchronized void setParticipantSsrcs(String participantJid,
        Map<MediaType, String> ssrcs)
    {
        if (!participantsInfo.containsKey(participantJid))
        {
            participantsInfo.put(participantJid, new ParticipantInfo());
        }

        ParticipantInfo info = participantsInfo.get(participantJid);
        info.setSsrcs(ssrcs);
    }

    public synchronized Map<String, Map<MediaType, String>> getParticipantsSsrcs()
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

    public synchronized Map<MediaType, String> getParticipantSsrcs(
        String participantJid)
    {
        if (participantsInfo.containsKey(participantJid))
            return participantsInfo.get(participantJid).getSsrcs();
        else
            return null;
    }

    public synchronized void setParticipantFingerprint(String participantJid,
        String fingerprint)
    {
        if (!participantsInfo.containsKey(participantJid))
        {
            participantsInfo.put(participantJid, new ParticipantInfo());
        }

        ParticipantInfo info = participantsInfo.get(participantJid);
        info.setFingerprint(fingerprint);
    }

    public synchronized String getParticipantFingerprint(String participantJid)
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
}
