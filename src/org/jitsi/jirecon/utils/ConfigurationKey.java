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
     * The name of configuration property that specifies the user name used by
     * jirecon to login to XMPP server.
     */
    public final static String XMPP_USER_KEY = PREFIX + ".XMPP_USER";

    /**
     * The name of configuration property that specifies login password of the
     * jirecon user. If not provided then anonymous login method is used.
     */
    public final static String XMPP_PASS_KEY = PREFIX + ".XMPP_PASS";

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
