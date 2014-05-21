package org.jitsi.jirecon.session;

// TODO: Rewrite those import statements to package import statement.
import java.util.HashMap;
import java.util.Map;

import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.format.MediaFormat;

/**
 * This class is used to pack information of Jingle session.
 * 
 * @author lishunyang
 * 
 */
public class JingleSessionInfo
{
    /**
     * The video or audio format which will be used in transferring RTP stream.
     */
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

    /**
     * Constructor of JingleSessionInfo
     */
    public JingleSessionInfo()
    {
        formats = new HashMap<MediaType, MediaFormat>();
        dynamicPayloadTypeIds = new HashMap<MediaType, Byte>();
        remoteSsrcs = new HashMap<MediaType, String>();
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
}
