/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

// TODO: Rewrite those import statements to package import statement.
import java.util.HashMap;
import java.util.Map;

import org.ice4j.ice.CandidatePair;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.format.MediaFormat;

/**
 * This class is used to pack information of Jingle session.
 * 
 * @author lishunyang
 * 
 */
public class SessionInfo
{
    private String localNode;

    private String remoteNode;

    private String sid;

    private String conferenceJid;

    /**
     * The video or audio format which will be used in transferring RTP stream.
     */
    // TODO 使用formatlist，把所有的format都记录下来
    private Map<MediaType, MediaFormat> formats;

    /**
     * The dynamic payloadtype id of video or audio format which will be used in
     * transferring RTP stream.
     */
    private Map<MediaType, Byte> dynamicPayloadTypeIds;

    /**
     * The video or audio remote SSRC property, it will be used in receiving RTP
     * stream.
     */
    private Map<MediaType, String> remoteSsrcs;

    private Map<MediaType, CandidatePair> rtpCandidatePairs;

    private Map<MediaType, CandidatePair> rtcpCandidatePairs;

    private JireconSessionStatus status;

    /**
     * Constructor of JingleSessionInfo
     */
    public SessionInfo()
    {
        formats = new HashMap<MediaType, MediaFormat>();
        dynamicPayloadTypeIds = new HashMap<MediaType, Byte>();
        remoteSsrcs = new HashMap<MediaType, String>();
        rtpCandidatePairs = new HashMap<MediaType, CandidatePair>();
        rtcpCandidatePairs = new HashMap<MediaType, CandidatePair>();
        status = JireconSessionStatus.INITIATING;
    }

    /**
     * Add media formats and bind it with specified type of media.
     * 
     * @param media The media type, video or audio.
     * @param format The format which will be added.
     */
    public void addFormat(MediaType media, MediaFormat format)
    {
        formats.put(media, format);
    }

    /**
     * Add dynamic payloadtype id and bind it with specified type of media.
     * 
     * @param media The media type, video or audio.
     * @param dynamicPayloadTypeId The dynamic payloadtype id which will be
     *            added.
     */
    public void addDynamicPayloadTypeId(MediaType media,
        Byte dynamicPayloadTypeId)
    {
        dynamicPayloadTypeIds.put(media, dynamicPayloadTypeId);
    }

    /**
     * Add remote SSRC and bind it with specified type of media.
     * 
     * @param media The media type, video or audio.
     * @param ssrc The remote SSRC which wll be added.
     */
    public void addRemoteSsrc(MediaType media, String ssrc)
    {
        remoteSsrcs.put(media, ssrc);
    }

    /**
     * Get the format of a specified media type.
     * 
     * @param media The media type, video or audio.
     * @return
     */
    public MediaFormat getFormat(MediaType media)
    {
        return formats.get(media);
    }

    /**
     * Get the dynamic payloadtype id of a specified media type.
     * 
     * @param media The media type, video or audio.
     * @return
     */
    public Byte getDynamicPayloadTypeId(MediaType media)
    {
        return dynamicPayloadTypeIds.get(media);
    }

    /**
     * Get the remote SSRC of a specified media type.
     * 
     * @param media The media type, video or audio.
     * @return
     */
    public String getRemoteSsrc(MediaType media)
    {
        return remoteSsrcs.get(media);
    }

    public void addRtpCandidatePair(MediaType media, CandidatePair candidatePair)
    {
        rtpCandidatePairs.put(media, candidatePair);
    }

    public void addRtcpCandidatePair(MediaType media,
        CandidatePair candidatePair)
    {
        rtcpCandidatePairs.put(media, candidatePair);
    }

    public CandidatePair getRtpCandidatePair(MediaType media)
    {
        return rtpCandidatePairs.get(media);
    }

    public CandidatePair getRtcpCandidatePair(MediaType media)
    {
        return rtcpCandidatePairs.get(media);
    }

    public void setSessionStatus(JireconSessionStatus status)
    {
        this.status = status;
    }

    public JireconSessionStatus getSessionStatus()
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
    
    public String getLocalNode()
    {
        return localNode;
    }
    
    public void setLocalNode(String localNode)
    {
        this.localNode = localNode;
    }
    
    public String getRemoteNode()
    {
        return remoteNode;
    }
    
    public void setRemoteNode(String remoteNode)
    {
        this.remoteNode = remoteNode;
    }
    
    public String getSid()
    {
        return sid;
    }
    
    public void setSid(String sid)
    {
        this.sid = sid;
    }
}
