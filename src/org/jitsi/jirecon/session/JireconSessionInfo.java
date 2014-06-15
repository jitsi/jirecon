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

    // <format, payloadTypeId>
    public Map<MediaFormat, Byte> formatAndPayloadTypes =
        new HashMap<MediaFormat, Byte>();

    private Map<String, ParticipantInfo> participantsInfo =
        new HashMap<String, ParticipantInfo>();

    public void addFormatAndPayloadType(MediaFormat format, byte payloadTypeId)
    {
        formatAndPayloadTypes.put(format, payloadTypeId);
    }

    public void setFormatAndPayloadTypes(
        Map<MediaFormat, Byte> formatAndPayloadTypes)
    {
        this.formatAndPayloadTypes = formatAndPayloadTypes;
    }

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
}
