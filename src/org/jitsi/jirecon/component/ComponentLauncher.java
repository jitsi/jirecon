/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.component;

import java.util.concurrent.TimeUnit;

import org.jivesoftware.whack.ExternalComponentManager;
import org.xmpp.component.ComponentException;

public class ComponentLauncher
{
    /*
     * TODO: This configuration attributes should be loaded from configuration file.
     */
    
    /**
     * Port of XMPP Component.
     */
    public static final int PORT = 5275;

    /**
     * Secret of XMPP Component.
     */
    public static final String SECRET = "xxxxx";

    /**
     * Subdomain of XMPP component.
     */
    public static final String SUBDOMAIN = "jirecon";

    /**
     * Domain of XMPP component.
     */
    public static final String DOMAIN = "example.com";

    public static void main(String[] args)
    {
        ExternalComponentManager mgr =
            new ExternalComponentManager(DOMAIN, PORT);

        mgr.setSecretKey(SUBDOMAIN, SECRET);
        mgr.setServerName(DOMAIN);
        
        try
        {
            mgr.addComponent(
                SUBDOMAIN, 
                new JireconComponentImpl(
                    "jirecon@" + SUBDOMAIN + "." + DOMAIN, 
                    "./jirecon.properties"));
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
