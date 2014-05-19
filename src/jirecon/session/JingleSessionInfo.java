package jirecon.session;

import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.format.MediaFormat;

public interface JingleSessionInfo
{
    public void addFormat(MediaType media, MediaFormat format);

    public void addDynamicPayloadTypeId(MediaType media,
        Byte dynamicPayloadTypeId);

    public void addRemoteSsrc(MediaType media, String ssrc);

    public MediaFormat getFormat(MediaType media);

    public Byte getDynamicPayloadTypeId(MediaType media);

    public String getRemoteSsrc(MediaType media);
}
