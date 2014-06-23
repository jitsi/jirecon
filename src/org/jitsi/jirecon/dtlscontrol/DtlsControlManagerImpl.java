/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package org.jitsi.jirecon.dtlscontrol;

import java.util.*;
import java.util.Map.Entry;

import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.*;

/**
 * An implementation of <tt>SrtpControlManager</tt>, it holds the
 * <tt>DtlsControl</tt> of different <tt>MediaType</tt>.
 * 
 * @author lishunyang
 * @see SrtpControlManager
 */
public class DtlsControlManagerImpl
    implements SrtpControlManager
{
    /**
     * The mapping between <tt>MediaType</tt> and <tt>DtlsControl</tt>.
     */
    private Map<MediaType, DtlsControl> dtlsControls =
        new HashMap<MediaType, DtlsControl>();

    /**
     * Indicate which kind of hash function is used by <tt>DtlsContrl</tt>.
     */
    private String hashFunction;

    /**
     * The hash function item key in configuration file.
     */
    private final static String HASH_FUNCTION_KEY = "DTLS_HASH_FUNTION";

    /**
     * Initializes a new <tt>DtlsControlManagerImpl</tt> instance, create
     * <tt>DtlsControl</tt> for both audio and video.
     * <p>
     * <strong>Warning:</strong> 
     * <tt>DtlsControlManagerImpl</tt> relies on
     * <tt>LibJitsi</tt> service, so <tt>LibJitsi</tt> must be started before
     * calling this method.
     */
    public DtlsControlManagerImpl()
    {
        ConfigurationService configuration = LibJitsi.getConfigurationService();
        hashFunction = configuration.getString(HASH_FUNCTION_KEY);
        MediaService mediaService = LibJitsi.getMediaService();

        for (MediaType mediaType : MediaType.values())
        {
            if (mediaType != MediaType.AUDIO && mediaType != MediaType.VIDEO)
                continue;
            DtlsControl control =
                (DtlsControl) mediaService
                    .createSrtpControl(SrtpControlType.DTLS_SRTP);
            dtlsControls.put(mediaType, control);
            control.setSetup(DtlsControl.Setup.ACTIVE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addRemoteFingerprint(MediaType mediaType, String fingerprint)
    {
        final DtlsControl dtlsControl = dtlsControls.get(mediaType);
        final Map<String, String> fingerprints = new HashMap<String, String>();
        fingerprints.put(hashFunction, fingerprint);
        dtlsControl.setRemoteFingerprints(fingerprints);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalFingerprint(MediaType mediaType)
    {
        return dtlsControls.get(mediaType).getLocalFingerprint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalFingerprintHashFunction(MediaType mediaType)
    {
        return dtlsControls.get(mediaType).getLocalFingerprintHashFunction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SrtpControl getSrtpControl(MediaType mediaType)
    {
        return dtlsControls.get(mediaType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<MediaType, SrtpControl> getAllSrtpControl()
    {
        Map<MediaType, SrtpControl> controls =
            new HashMap<MediaType, SrtpControl>();
        for (Entry<MediaType, DtlsControl> e : dtlsControls.entrySet())
        {
            controls.put(e.getKey(), e.getValue());
        }
        return controls;
    }
}
