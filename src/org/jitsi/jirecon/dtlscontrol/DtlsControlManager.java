/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package org.jitsi.jirecon.dtlscontrol;

import java.util.*;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.DtlsFingerprintPacketExtension;

import org.jitsi.impl.neomedia.transform.dtls.DtlsControlImpl;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.*;

/**
 * Hold the <tt>DtlsControl</tt> of different <tt>MediaType</tt>.
 * 
 * @author lishunyang
 */
public class DtlsControlManager
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
     * Add a specified <tt>MediaType</tt> finger print of a remote peer. If
     * there is a finger print with same <tt>MediaType</tt>, the old finger
     * print will be replaced.
     * 
     * @param mediaType The <tt>MediaType</tt> of the finger print.
     * @param fingerprint The finger print.
     */
    public void addRemoteFingerprint(MediaType mediaType, String fingerprint)
    {
        final DtlsControl dtlsControl = getDtlsControl(mediaType);
        final Map<String, String> fingerprints = new HashMap<String, String>();
        fingerprints.put(hashFunction, fingerprint);
        dtlsControl.setRemoteFingerprints(fingerprints);
    }

    /**
     * Get the specified <tt>MediaType</tt> local finger print.
     * 
     * @param mediaType The <tt>MediaType</tt> of the finger print.
     * @return The local finger print.
     */
    public String getLocalFingerprint(MediaType mediaType)
    {
        return getDtlsControl(mediaType).getLocalFingerprint();
    }

    /**
     * Get the hash function name of specified <tt>MediaType</tt> local finger
     * print.
     * 
     * @param mediaType The <tt>MediaType</tt> of the finger print.
     * @return The hash function name.
     */
    public String getLocalFingerprintHashFunction(MediaType mediaType)
    {
        return getDtlsControl(mediaType).getLocalFingerprintHashFunction();
    }

    /**
     * Get all <tt>DtlsControl</tt> of this manager.
     * 
     * @return The map between <tt>MediaType</tt> and <tt>DtlsControl</tt>.
     */
    public Map<MediaType, DtlsControl> getAllDtlsControl()
    {
        Map<MediaType, DtlsControl> controls =
            new HashMap<MediaType, DtlsControl>();
        
        for (MediaType mediaType : MediaType.values())
        {
            controls.put(mediaType, getDtlsControl(mediaType));
        }
        
        return controls;
    }

    /**
     * Specify hash function of <tt>DtlsControl</tt>
     * 
     * @param hash The hash function
     */
    public void setHashFunction(String hash)
    {
        this.hashFunction = hash;
    }

    /**
     * Get Fingerprint packet extension from <tt>DtlsControlManager</tt>.
     * 
     * @param mediaType The <tt>MediaType</tt> of the fingerprint.
     * @return Fingerprint packet extension.
     */
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
     * Start DTLS control.
     * <p>
     * This method hasn't been used, because <tt>MediaStream</tt> will start
     * DTLS control.
     * 
     * @param mediaType
     */
    public void startDtlsControl(MediaType mediaType)
    {
        dtlsControls.get(mediaType).start(mediaType);
    }

    /**
     * Stop SRTP control.
     * <p>
     * Note: <tt>MediaStream</tt> will clean DTLS control by the way, so if you
     * add srtp controller to <tt>MediaStream</tt>, you don't need to call this
     * method.
     * 
     * @param mediaType
     */
    public void stopDtlsControl(MediaType mediaType)
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
    public DtlsControl getDtlsControl(MediaType mediaType)
    {
        DtlsControl control = dtlsControls.get(mediaType);

        if (null == control)
        {
            if (MediaType.DATA == mediaType)
            {
                /*
                 * We have to create DtlsControlImpl directly because
                 * MediaService can only create DtlsControl without Srtp
                 * support(construcive argument is false).
                 */
                control = new DtlsControlImpl(true);
            }
            else
            {
                MediaService mediaService = LibJitsi.getMediaService();
                control =
                    (DtlsControl) mediaService
                        .createSrtpControl(SrtpControlType.DTLS_SRTP);
            }

            dtlsControls.put(mediaType, control);
            control.setSetup(DtlsControl.Setup.ACTIVE);
        }

        return control;
    }
}
