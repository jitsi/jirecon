/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.utils;

public class JireconConfigurationKey
{
    /**
     * The hash nick name item key in configuration file.
     */
    public final static String NICK_KEY = "org.jitsi.jirecon.JIRECON_NICKNAME";

    /**
     * The XMPP server host item key in configuration file.
     */
    public final static String XMPP_HOST_KEY = "org.jitsi.jirecon.XMPP_HOST";

    /**
     * The XMPP server port item key in configuration file.
     */
    public final static String XMPP_PORT_KEY = "org.jitsi.jirecon.XMPP_PORT";

    /**
     * The saving directory item key in configuration file.
     */
    public final static String SAVING_DIR_KEY = "org.jitsi.jirecon.OUTPUT_DIR";

    /**
     * The minimum stream port item key in configuration file.
     */
    public final static String MIN_STREAM_PORT_KEY =
        "org.jitsi.jirecon.MIN_STREAM_PORT";

    /**
     * The maximum stream port item key in configuration file.
     */
    public final static String MAX_STREAM_PORT_KEY =
        "org.jitsi.jirecon.MAX_STREAM_PORT";

    /**
     * The hash function item key in configuration file.
     */
    public final static String HASH_FUNCTION_KEY =
        "org.jitsi.jirecon.DTLS_HASH_FUNTION";
}
