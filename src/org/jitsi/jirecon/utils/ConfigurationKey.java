/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.utils;

/**
 * A list of configuration key used by <tt>Jirecon</tt>.
 * <p>
 * <strong>Warning:</strong> They are keys, not values :D
 * 
 * @author lishunyang
 * 
 */
public class ConfigurationKey
{
    /**
     * Prefix of configiguration keys.
     */
    private static final String PREFIX = "org.jitsi.jirecon";

    /**
     * The nick name of <tt>Jirecon</tt> when join the MUC.
     */
    public final static String NICK_KEY = PREFIX + ".JIRECON_NICKNAME";

    /**
     * The host name of XMPP server, it's used for connecting with XMPP server.
     */
    public final static String XMPP_HOST_KEY = PREFIX + ".XMPP_HOST";

    /**
     * The port of XMPP server, it's used for connecting with XMPP server.
     */
    public final static String XMPP_PORT_KEY = PREFIX + ".XMPP_PORT";

    /**
     * The directory name indicates where to output recording files.
     */
    public final static String SAVING_DIR_KEY = PREFIX + ".OUTPUT_DIR";

    /**
     * <tt>Jirecon</tt> needs multiple ports for transfering media stream, this
     * indicates the MIN port number it can use.
     */
    public final static String MIN_STREAM_PORT_KEY = PREFIX
        + ".MIN_STREAM_PORT";

    /**
     * <tt>Jirecon</tt> needs multiple ports for transfering media stream, this
     * indicates the MAX port number it can use.
     */
    public final static String MAX_STREAM_PORT_KEY = PREFIX
        + ".MAX_STREAM_PORT";
}
