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
package org.jitsi.jirecon.xmppcomponent;

import java.util.concurrent.*;
import org.jivesoftware.whack.*;
import org.xmpp.component.*;

/**
 * A launch application which is used to run <tt>Jirecon</tt>.
 * <p>
 * Usually there will be a associated shell script to start this application.
 * 
 * @author lishunyang
 * 
 */
public class ComponentLauncher
{
    /**
     * Prefix of "configuration" parameter.
     */
    public static String CONF_ARG_NAME = "--conf=";

    /**
     * Prefix of "port" parameter.
     */
    public static String PORT_ARG_NAME = "--port=";

    /**
     * Prefix of "secret" parameter.
     */
    public static String SECRET_ARG_NAME = "--secret=";

    /**
     * Prefix of "domain" parameter.
     */
    public static String DOMAIN_ARG_NAME = "--domain=";
    
    /**
     * Prefix of "subdomain" parameter.
     */
    public static String SUBDOMAIN_ARG_NAME = "--subdomain=";

    /**
     * Prefix of the "host" parameter.
     */
    public static String HOST_ARG_NAME = "--host=";

    /**
     * Default value of configuration file path.
     */
    private static String conf = "./jirecon.properties";

    /**
     * Default value of XMPP Component's port.
     */
    public static int port = 5275;

    /**
     * Default value of XMPP Component's secret.
     */
    public static String secret = "xxxxx";

    /**
     * Default value of XMPP component's domain.
     */
    public static String domain = "jirecon.example.com";
    
    /**
     * Default value of XMPP component's name.
     */
    public static String subdomain = "jirecon";

    /**
     * The default value of the hostname.
     */
    public static String host = "localhost";

    /**
     * Application entry.
     * 
     * @param args
     */
    public static void main(String[] args)
    {
        for (String arg : args)
        {
            if (arg.startsWith(CONF_ARG_NAME))
            {
                conf = arg.substring(CONF_ARG_NAME.length());
            }
            else if (arg.startsWith(PORT_ARG_NAME))
            {
                port = Integer.valueOf(arg.substring(PORT_ARG_NAME.length()));
            }
            else if (arg.startsWith(SECRET_ARG_NAME))
            {
                secret = arg.substring(SECRET_ARG_NAME.length());
            }
            else if (arg.startsWith(DOMAIN_ARG_NAME))
            {
                domain = arg.substring(DOMAIN_ARG_NAME.length());
            }
            else if (arg.startsWith(SUBDOMAIN_ARG_NAME))
            {
                subdomain = arg.substring(SUBDOMAIN_ARG_NAME.length());
            }
            else if (arg.startsWith(HOST_ARG_NAME))
            {
                host = arg.substring(HOST_ARG_NAME.length());
            }
        }

        // TODO: allow a separate hostname to be used
        ExternalComponentManager mgr =
            new ExternalComponentManager(host, port);

        mgr.setSecretKey(subdomain, secret);

        mgr.setServerName(domain);

        try
        {
            mgr.addComponent(subdomain, new XMPPComponent(subdomain + "."
                + domain, conf));
        }
        catch (ComponentException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }

        /*
         * Once we started the component, sleep and wake up every 10 second to
         * reduce CPU load.
         */
        while (true)
        {
            try
            {
                TimeUnit.SECONDS.sleep(10);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }
}
