/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.xmppcomponent;

import java.util.concurrent.TimeUnit;

import org.jivesoftware.whack.ExternalComponentManager;
import org.xmpp.component.ComponentException;

/**
 * A launch application which is used to run <tt>Jirecon</tt>.
 * <p>
 * Usually there will be a associated Shell script to start this application.
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
     * Prefix of "name" parameter.
     */
    public static String NAME_ARG_NAME = "--name=";

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
    public static String name = "jirecon";

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
            else if (arg.startsWith(NAME_ARG_NAME))
            {
                name = arg.substring(NAME_ARG_NAME.length());
            }
        }

        String[] tokens = domain.split("\\.", 2);
        
        ExternalComponentManager mgr =
            new ExternalComponentManager(tokens[1], port);

        mgr.setSecretKey(tokens[0], secret);
        mgr.setServerName(tokens[1]);

        try
        {
            mgr.addComponent(tokens[0], new XMPPComponent(name + "@"
                + domain, conf));
        }
        catch (ComponentException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }

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
