/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

// TODO: Rewrite those import statements to package import statement.
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;

import org.ice4j.ice.CandidatePair;
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

    // This is actually the conference jid
    private String remoteJid;

    private String sid;

    private String conferenceJid;

    private JireconSessionState status;
    
    private JireconSessionState state;
    
    private Map<MediaType, InfoBox> infoBoxes = new HashMap<MediaType, InfoBox>();

    /**
     * Constructor of JingleSessionInfo
     */
    public JireconSessionInfo()
    {
        status = JireconSessionState.INITIATING;
        for (MediaType media : MediaType.values())
        {
            infoBoxes.put(media, new InfoBox());
        }
    }

    /**
     * Add media formats and bind it with specified type of media.
     * 
     * @param media The media type, video or audio.
     * @param format The format which will be added.
     */
    public void addPayloadType(MediaType media, MediaFormat format, byte payloadTypeId)
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

    public void setRtpCandidatePair(MediaType media, CandidatePair candidatePair)
    {
        infoBoxes.get(media).rtpCandidatePair = candidatePair;
    }

    public void setRtcpCandidatePair(MediaType media,
        CandidatePair candidatePair)
    {
        infoBoxes.get(media).rtcpCandidatePair = candidatePair;
    }

    public CandidatePair getRtpCandidatePair(MediaType media)
    {
        return infoBoxes.get(media).rtpCandidatePair;
    }

    public CandidatePair getRtcpCandidatePair(MediaType media)
    {
        return infoBoxes.get(media).rtcpCandidatePair;
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
    
    public void setState(JireconSessionState state)
    {
        this.state = state;
    }
    
    public JireconSessionState getState()
    {
        return state;
    }

    private class InfoBox
    {
        /**
         * <format, payloadTypeId>
         */
        public Map<MediaFormat, Byte> payloadTypes =
            new HashMap<MediaFormat, Byte>();

        private Map<String, String> remoteSsrcs = new HashMap<String, String>();

        private String remoteFingerprint;

        private CandidatePair rtpCandidatePair;

        private CandidatePair rtcpCandidatePair;
    }
}
