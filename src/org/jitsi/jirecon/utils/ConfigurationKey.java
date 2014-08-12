/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.utils;

/**
 * A list of configuration key used by <tt>Jirecon</tt>.
 * <p>
 * <strong>Warning:</strong> They are keys, not values:D
 * 
 * @author lishunyang
 * 
 */
public class ConfigurationKey
{
    /**
     * The nick name of <tt>Jirecon</tt> when join the MUC.
     */
    public final static String NICK_KEY = "org.jitsi.jirecon.JIRECON_NICKNAME";

    /**
     * The host name of XMPP server, it's used for connecting with XMPP server.
     */
    public final static String XMPP_HOST_KEY = "org.jitsi.jirecon.XMPP_HOST";

    /**
     * The port of XMPP server, it's used for connecting with XMPP server.
     */
    public final static String XMPP_PORT_KEY = "org.jitsi.jirecon.XMPP_PORT";

    /**
     * The directory name indicates where to output recording files.
     */
    public final static String SAVING_DIR_KEY = "org.jitsi.jirecon.OUTPUT_DIR";

    /**
     * <tt>Jirecon</tt> needs multiple ports for transfering media stream, this
     * indicates the MIN port number it can use.
     */
    public final static String MIN_STREAM_PORT_KEY =
        "org.jitsi.jirecon.MIN_STREAM_PORT";

    /**
     * <tt>Jirecon</tt> needs multiple ports for transfering media stream, this
     * indicates the MAX port number it can use.
     */
    public final static String MAX_STREAM_PORT_KEY =
        "org.jitsi.jirecon.MAX_STREAM_PORT";

    /**
     * Hash function which will be used in DTLS.
     */
    public final static String HASH_FUNCTION_KEY =
        "org.jitsi.jirecon.DTLS_HASH_FUNTION";
}
