/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package org.jitsi.jirecon.dtlscontrol;

import org.jitsi.service.neomedia.MediaService;
import org.jitsi.service.neomedia.MediaType;
import org.jitsi.service.neomedia.SrtpControl;

public interface JireconSrtpControlManager
{
    public void init(MediaService mediaService);

    public void uinit();

    public void addRemoteFingerprint(MediaType mediaType, String hashFuntion,
        String fingerprint);

    public String getLocalFingerprint(MediaType mediaType);

    public String getLocalFingerprintHashFunction(MediaType mediaType);

    public SrtpControl getSrtpControl(MediaType mediaType);
}
