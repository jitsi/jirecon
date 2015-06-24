/*
/*
 * Jirecon, the JItsi REcording COntainer.
 *
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.jirecon;

import java.util.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import org.jitsi.impl.neomedia.transform.dtls.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.service.neomedia.*;

/**
 * DtlsControlManager is used for holding the <tt>DtlsControl</tt> of different
 * <tt>MediaType</tt>s. It can also be used for creating <tt>DtlsControl</tt>s
 * or fingerprint packets.
 * 
 * @author lishunyang
 */
public class DtlsControlManager
{
    /**
     * The <tt>DtlsControl</tt>s hold by this manager.
     */
    private final Map<MediaType, DtlsControl> dtlsControls =
        new HashMap<MediaType, DtlsControl>();

    /**
     * Set a remote fingerprint to <tt>DtlsControl</tt>. Notice that old fingerprint
     * can be replaced by new fingerprint.
     * 
     * @param mediaType The <tt>MediaType</tt> of this fingerprint.
     * @param fingerprintPE The fingerprint packet extension comes from remote
     *            peer.
     */
    public void setRemoteFingerprint(MediaType mediaType,
                                     DtlsFingerprintPacketExtension fingerprintPE)
    {
        final DtlsControl dtlsControl = getDtlsControl(mediaType);
        final Map<String, String> fingerprints = new HashMap<String, String>();

        fingerprints.put(fingerprintPE.getHash(),
            fingerprintPE.getFingerprint());
        dtlsControl.setRemoteFingerprints(fingerprints);
    }

    /**
     * Get a local fingerprint text.
     * 
     * @param mediaType The <tt>MediaType</tt> of the finger print.
     * @return Fingerprint text.
     */
    public String getLocalFingerprint(MediaType mediaType)
    {
        return getDtlsControl(mediaType).getLocalFingerprint();
    }

    /**
     * Get hash function text of local fingerprint.
     * 
     * @param mediaType The <tt>MediaType</tt> of the finger print.
     * @return Hash function text.
     */
    public String getLocalFingerprintHashFunction(MediaType mediaType)
    {
        return getDtlsControl(mediaType).getLocalFingerprintHashFunction();
    }

    /**
     * Get all <tt>DtlsControl</tt>s that this manager supported.
     * <p>
     * <strong>Warning:</strong> This method will return all supported
     * <tt>DtlsControl</tt>s, even though you may not need them all.
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
     * Create fingerprint packet extension of specified <tt>MediaType</tt>.
     * 
     * @param mediaType The <tt>MediaType</tt> of the fingerprint.
     * @return Fingerprint packet extension.
     */
    public DtlsFingerprintPacketExtension createFingerprintPacketExt(MediaType mediaType)
    {
        DtlsFingerprintPacketExtension fingerprintPE =
            new DtlsFingerprintPacketExtension();

        fingerprintPE.setHash(getLocalFingerprintHashFunction(mediaType));
        fingerprintPE.setFingerprint(getLocalFingerprint(mediaType));
        /*
         * In Jirecon, all "setup" attributes are safely to be set "ACTIVE". 
         */
        fingerprintPE.setAttribute("setup", DtlsControl.Setup.ACTIVE);

        return fingerprintPE;
    }

    /**
     * Start specified <tt>DtlsControl</tt>.
     * <p>
     * If you plan to put the <tt>DtlsControl</tt> into <tt>MediaStream</tt>, then you
     * don't need to call this method, because <tt>MediaStream</tt> will start
     * DTLS control automatically.
     * 
     * @param mediaType Indicate which <tt>DtlsControl</tt> you want to start.
     */
    public void startDtlsControl(MediaType mediaType)
    {
        dtlsControls.get(mediaType).start(mediaType);
    }

    /**
     * Stop specified <tt>DtlsControl</tt>.
     * <p>
     * If you have put the <tt>DtlsControl</tt> into <tt>MediaStream</tt>, then
     * you don't need to call this method, because <tt>MediaStream</tt> will
     * clean DTLS control automatically.
     * 
     * @param mediaType Indicate which <tt>DtlsControl</tt> you want to stop.
     */
    public void stopDtlsControl(MediaType mediaType)
    {
        final DtlsControl control = getDtlsControl(mediaType);

        if (null != control)
        {
            dtlsControls.get(mediaType).cleanup(null);
        }
    }

    /**
     * Get a <tt>DtlsControl</tt>.
     * <p>
     * <tt>DtlsControlManager</tt> will create new <tt>DtlsControl</tt> if it's
     * necessary.
     * 
     * @param mediaType Indicate which type of <tt>DtlsControl</tt> you want to
     *            get.
     * @return
     */
    public DtlsControl getDtlsControl(MediaType mediaType)
    {
        DtlsControl control = dtlsControls.get(mediaType);

        if (null == control)
        {
            if (MediaType.DATA == mediaType)
            {
                // Do add SRTP extensions, because the server-side code
                // (org.jitsi.impl.neomedia.transform.dtls.TlsServerImpl)
                // expects them in all cases.
                control = new DtlsControlImpl(false);
            }
            else
            {
                LibJitsi.start();

                 // As for the other media types (such as "audio" or "video"), we
                 // can use MediaService to create DtlsControl.
                MediaService mediaService = LibJitsi.getMediaService();
                control =
                    (DtlsControl) mediaService
                        .createSrtpControl(SrtpControlType.DTLS_SRTP);
            }

            dtlsControls.put(mediaType, control);
            /*
             * In Jirecon, "setup" can be safely set to "ACTIVE".
             */
            control.setSetup(DtlsControl.Setup.ACTIVE);
        }

        return control;
    }
}
