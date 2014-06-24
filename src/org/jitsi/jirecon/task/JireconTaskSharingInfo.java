package org.jitsi.jirecon.task;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.format.MediaFormat;

// TODO: The existence of this class is ugly, I should refactor it.
/**
 * Gather some detailed information of a <tt>JireconTask</tt>. It can only be
 * accessed by <tt>JireocnTask</tt> and other inner things. The purpose of
 * setting such class is to simplify the communication inside the
 * <tt>JireconTask</tt>
 * 
 * @author lishunyang
 * 
 */
public class JireconTaskSharingInfo
{
    /**
     * The map between <tt>MediaType</tt> and ssrc of local peer's.
     */
    private Map<MediaType, Long> localSsrcs = new HashMap<MediaType, Long>();

    /**
     * Attribute "mslable" in source packet extension.
     */
    private String msLabel = UUID.randomUUID().toString();

    /**
     * Local peer's whole jid. It is mandatory to use whole jid because
     * <tt>JireconSession</tt> could use it to filter packet.
     */
    private String localJid;

    // TODO: Read other project's code such as videobridge-recording, to see how
    // did they solve filter stuff.
    /**
     * Remote peer's whole jid. It is mandatory to use whole jid because
     * <tt>JireconSession</tt> could use it to filter packet.
     */
    private String remoteJid;

    /**
     * Session id. It is necessary to be set in session packet.
     */
    private String sid;

    /**
     * The MUC jid, which is used to join a MUC. There is no mandatory to use
     * whole jid as long as it can be recognized by <tt>XMPPConnector</tt> and
     * join a MUC successfully.
     */
    private String mucJid;

    /**
     * The map between <tt>MediaFormat</tt> and dynamic payload type id.
     */
    public Map<MediaFormat, Byte> formatAndPayloadTypes =
        new HashMap<MediaFormat, Byte>();

    /**
     * The map between participant's jid and <tt>ParticipantInfo</tt>.
     * 
     * @see ParticipantInfo.
     */
    private Map<String, ParticipantInfo> participantsInfo =
        new HashMap<String, ParticipantInfo>();

    /**
     * Add a map between <tt>MediaType</tt> and local ssrc.
     * 
     * @param mediaType
     * @param ssrc
     */
    public synchronized void addLocalSsrc(MediaType mediaType, Long ssrc)
    {
        localSsrcs.put(mediaType, ssrc);
    }

    /**
     * Get a local ssrc of specified <tt>MediaType</tt>.
     * 
     * @param mediaType
     * @return local ssrc.
     */
    public synchronized Long getLocalSsrc(MediaType mediaType)
    {
        return localSsrcs.get(mediaType);
    }

    /**
     * Get attribute "mslabel".
     * 
     * @return mslabel
     */
    public synchronized String getMsLabel()
    {
        return msLabel;
    }

    /**
     * Get attribute "label" of specified <tt>MediaType</tt>.
     * 
     * @param mediaType
     * @return label
     */
    public synchronized String getLabel(MediaType mediaType)
    {
        return mediaType.toString();
    }

    /**
     * Get attribute "msid" of specified <tt>MediaType</tt>.
     * 
     * @param mediaType
     * @return msid.
     */
    public synchronized String getMsid(MediaType mediaType)
    {
        return msLabel + " " + getLabel(mediaType);
    }

    /**
     * Add a map between <tt>MediaFormat</tt> and dynamic payload type id.
     * 
     * @param format
     * @param payloadTypeId
     */
    public synchronized void addFormatAndPayloadType(MediaFormat format,
        byte payloadTypeId)
    {
        formatAndPayloadTypes.put(format, payloadTypeId);
    }

    /**
     * Set the maps between <tt>MediaType</tt> and dynamic payload type id. This
     * will replace the old map.
     * 
     * @param formatAndPayloadTypes is the new map.
     */
    public synchronized void setFormatAndPayloadTypes(
        Map<MediaFormat, Byte> formatAndPayloadTypes)
    {
        this.formatAndPayloadTypes = formatAndPayloadTypes;
    }

    /**
     * Get the maps between <tt>MediaFormat</tt> and dynamic payload type id of
     * a specified <tt>MediaType</tt>.
     * 
     * @param mediaType
     * @return the maps
     */
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

    /**
     * Get the maps between <tt>MediaFormat</tt> and dynamic payload type id
     * 
     * @return the maps
     */
    public synchronized Map<MediaFormat, Byte> getFormatAndPayloadTypes()
    {
        if (formatAndPayloadTypes.size() > 0)
            return formatAndPayloadTypes;
        else
            return null;
    }

    /**
     * Get MUC jid.
     * 
     * @return muc jid
     */
    public synchronized String getMucJid()
    {
        return mucJid;
    }

    /**
     * Set MUC jid.
     * 
     * @param mucJid
     */
    public synchronized void setMucJid(String mucJid)
    {
        this.mucJid = mucJid;
    }

    /**
     * Get local jid.
     * 
     * @return local jid
     */
    public synchronized String getLocalJid()
    {
        return localJid;
    }

    /**
     * Set local jid.
     * 
     * @param localNode
     */
    public synchronized void setLocalJid(String localNode)
    {
        this.localJid = localNode;
    }

    /**
     * Get remote jid.
     * 
     * @return remote jid
     */
    public synchronized String getRemoteJid()
    {
        return remoteJid;
    }

    /**
     * Set remote jid.
     * 
     * @param remoteNode
     */
    public synchronized void setRemoteJid(String remoteNode)
    {
        this.remoteJid = remoteNode;
    }

    /**
     * Get session id.
     * 
     * @return session id
     */
    public synchronized String getSid()
    {
        return sid;
    }

    /**
     * Set session id.
     * 
     * @param sid
     */
    public synchronized void setSid(String sid)
    {
        this.sid = sid;
    }

    /**
     * Set a specified participant's map between <tt>MediaType</tt> and ssrc.
     * 
     * @param participantJid indicates which participant you want to set.
     * @param ssrcs is the map between <tt>MediaType</tt> and ssrc.
     */
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

    /**
     * Get all participants and their map between <tt>MediaType</tt> and ssrc.
     * 
     * @return participants and their map
     */
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

    /**
     * Get a specified participant's map between <tt>MediaType</tt> and ssrc.
     * 
     * @param participantJid indicates which participant do you want to get.
     * @return map between <tt>MediaType</tt> and ssrc
     */
    public synchronized Map<MediaType, String> getParticipantSsrcs(
        String participantJid)
    {
        if (participantsInfo.containsKey(participantJid))
            return participantsInfo.get(participantJid).getSsrcs();
        else
            return null;
    }

    /**
     * Set fingerprint to a specified participant.
     * 
     * @param participantJid indicates which participant do you want to set.
     * @param fingerprint
     */
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

    /**
     * Get a specified participant's fingerprint.
     * 
     * @param participantJid indicates which participant do you want to get.
     * @return fingerprint
     */
    public synchronized String getParticipantFingerprint(String participantJid)
    {
        if (participantsInfo.containsKey(participantJid))
            return participantsInfo.get(participantJid).getFingerprint();
        else
            return null;
    }

    /**
     * An encapsulation of participant information.
     * 
     * @author lishunyang
     * 
     */
    public class ParticipantInfo
    {
        /**
         * The fingerprint of the participant.
         */
        private String fingerprint;

        /**
         * The map between <tt>MediaType</tt> and ssrc.
         */
        private Map<MediaType, String> ssrcs;

        /**
         * Set fingerprint.
         * 
         * @param fingerprint
         */
        public void setFingerprint(String fingerprint)
        {
            this.fingerprint = fingerprint;
        }

        /**
         * Set the map between <tt>MediaType</tt> and ssrc.
         * 
         * @param ssrcs
         */
        public void setSsrcs(Map<MediaType, String> ssrcs)
        {
            this.ssrcs = ssrcs;
        }

        /**
         * Get fingerptint.
         * 
         * @return fingerprint
         */
        public String getFingerprint()
        {
            return fingerprint;
        }

        /**
         * Get the map between <tt>MediaType</tt> and ssrc.
         * 
         * @return
         */
        public Map<MediaType, String> getSsrcs()
        {
            return ssrcs;
        }
    }
}
