/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package org.jitsi.jirecon.dtlscontrol;

import java.util.*;
import java.util.Map.Entry;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.DtlsFingerprintPacketExtension;

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
     * The map between <tt>MediaType</tt> and <tt>DtlsControl</tt>.
     */
    private final Map<MediaType, DtlsControl> dtlsControls =
        new HashMap<MediaType, DtlsControl>();

    /**
     * Indicate which kind of hash function is used by <tt>DtlsContrl</tt>.
     */
    private String hashFunction;

    /**
     * Construction method.
     */
    public DtlsControlManagerImpl()
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addRemoteFingerprint(MediaType mediaType, String fingerprint)
    {
        final DtlsControl dtlsControl = getDtlsControl(mediaType);
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
        return getDtlsControl(mediaType).getLocalFingerprint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalFingerprintHashFunction(MediaType mediaType)
    {
        return getDtlsControl(mediaType).getLocalFingerprintHashFunction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SrtpControl getSrtpControl(MediaType mediaType)
    {
        return getDtlsControl(mediaType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<MediaType, SrtpControl> getAllSrtpControl()
    {
        Map<MediaType, SrtpControl> controls =
            new HashMap<MediaType, SrtpControl>();
        
        for (MediaType mediaType : MediaType.values())
        {
            controls.put(mediaType, getDtlsControl(mediaType));
        }
        
        return controls;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHashFunction(String hash)
    {
        this.hashFunction = hash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbstractPacketExtension getFingerprintPacketExt(MediaType mediaType)
    {
        DtlsFingerprintPacketExtension fingerprintPE =
            new DtlsFingerprintPacketExtension();

        fingerprintPE.setHash(getLocalFingerprintHashFunction(mediaType));
        fingerprintPE.setFingerprint(getLocalFingerprint(mediaType));
        fingerprintPE.setAttribute("setup", DtlsControl.Setup.ACTIVE);

        return fingerprintPE;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method hasn't been used, because <tt>MediaStream</tt> will start
     * DTLS control.
     */
    @Override
    public void startSrtpControl(MediaType mediaType)
    {
        dtlsControls.get(mediaType).start(mediaType);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note: <tt>MediaStream</tt> will clean DTLS control by the way, so if you
     * add srtp controller to <tt>MediaStream</tt>, you don't need to call this
     * method.
     */
    @Override
    public void stopSrtpControl(MediaType mediaType)
    {
        final DtlsControl control = getDtlsControl(mediaType);

        if (null != control)
        {
            dtlsControls.get(mediaType).cleanup();
        }
    }

    /**
     * Get <tt>DtlsControl</tt> of a specified <tt>MediaType</tt>, if it doens't
     * exist, just create a new one.
     * 
     * @param mediaType
     * @return
     */
    private DtlsControl getDtlsControl(MediaType mediaType)
    {
        DtlsControl control = dtlsControls.get(mediaType);

        if (null == control)
        {
            MediaService mediaService = LibJitsi.getMediaService();

            control =
                (DtlsControl) mediaService
                    .createSrtpControl(SrtpControlType.DTLS_SRTP);
            dtlsControls.put(mediaType, control);
            control.setSetup(DtlsControl.Setup.ACTIVE);
        }

        return control;
    }
}
