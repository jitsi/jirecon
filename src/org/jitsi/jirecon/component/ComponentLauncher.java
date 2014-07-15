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
    public static void main(String[] args)
    {
        ExternalComponentManager mgr =
            new ExternalComponentManager(JireconComponent.DOMAIN,
                JireconComponent.PORT);

        mgr.setSecretKey(JireconComponent.SUBDOMAIN, JireconComponent.SECRET);
        mgr.setServerName(JireconComponent.DOMAIN);
        try
        {
            mgr.addComponent(JireconComponent.SUBDOMAIN, new JireconComponent());
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
