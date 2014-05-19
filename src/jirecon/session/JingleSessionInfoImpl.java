package jirecon.session;

import java.util.HashMap;
import java.util.Map;

import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.format.MediaFormat;

public class JingleSessionInfoImpl implements JingleSessionInfo
{
    private Map<MediaType, MediaFormat> formats;
    private Map<MediaType, Byte> dynamicPayloadTypeIds;
    private Map<MediaType, String> remoteSsrcs;
    
    public JingleSessionInfoImpl()
    {
        formats = new HashMap<MediaType, MediaFormat>();
        dynamicPayloadTypeIds = new HashMap<MediaType, Byte>();
        remoteSsrcs = new HashMap<MediaType, String>();
    }
    
    @Override
    public void addFormat(MediaType media, MediaFormat format)
    {
        formats.put(media, format);
    }
    
    @Override
    public void addDynamicPayloadTypeId(MediaType media, Byte dynamicPayloadTypeId)
    {
        dynamicPayloadTypeIds.put(media, dynamicPayloadTypeId);
    }
    
    @Override
    public void addRemoteSsrc(MediaType media, String ssrc)
    {
        remoteSsrcs.put(media, ssrc);
    }

    @Override
    public MediaFormat getFormat(MediaType media)
    {
        return formats.get(media);
    }

    @Override
    public Byte getDynamicPayloadTypeId(MediaType media)
    {
        return dynamicPayloadTypeIds.get(media);
    }

    @Override
    public String getRemoteSsrc(MediaType media)
    {
        return remoteSsrcs.get(media);
    }
}
