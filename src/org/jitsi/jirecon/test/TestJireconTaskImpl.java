/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.test;

import java.util.concurrent.TimeUnit;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;

import org.jitsi.jirecon.extension.*;
import org.jitsi.jirecon.task.Task;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.configuration.ConfigurationService;
import org.jitsi.service.libjitsi.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.provider.*;

import junit.framework.TestCase;

public class TestJireconTaskImpl
    extends TestCase
{
    private static Task task;

    private final static String XMPP_HOST_KEY = "XMPP_HOST";

    private final static String XMPP_PORT_KEY = "XMPP_PORT";

    private static final String SAVING_DIR_KEY = "OUTPUT_DIR";

    private static final String CONFIGURATION_FILE_PATH = "jirecon.properties";

    private static XMPPConnection connection;

    @Override
    protected void setUp()
    {
        LibJitsi.start();

        System.setProperty(ConfigurationService.PNAME_CONFIGURATION_FILE_NAME,
            CONFIGURATION_FILE_PATH);
        ConfigurationService configuration = LibJitsi.getConfigurationService();

        String xmppHost = null;
        int xmppPort = -1;
        String savingDir = null;
        xmppHost = configuration.getString(XMPP_HOST_KEY);
        xmppPort = configuration.getInt(XMPP_PORT_KEY, -1);
        savingDir = configuration.getString(SAVING_DIR_KEY);

        assertTrue(xmppHost.length() > 0);
        assertTrue(xmppPort > 0);
        assertTrue(savingDir.length() > 0);

        try
        {
            ConnectionConfiguration conf =
                new ConnectionConfiguration(xmppHost, xmppPort);
            connection = new XMPPConnection(conf);
            connection.connect();
            connection.loginAnonymously();
        }
        catch (XMPPException e)
        {
            if (null != connection)
                connection.disconnect();
            connection = null;
        }
        assertTrue(null != connection);

        MediaService mediaService = LibJitsi.getMediaService();
        assertTrue(null != mediaService);

        ProviderManager providerManager = ProviderManager.getInstance();

        providerManager.addIQProvider(JingleIQ.ELEMENT_NAME,
            JingleIQ.NAMESPACE, new JingleIQProvider());
        providerManager.addExtensionProvider("media", "http://estos.de/ns/mjs",
            new MediaExtensionProvider());

        task = new Task();
        String mucJid = "p15uivfwk4xcg14i@conference.example.com";
        task.init(mucJid, connection, savingDir + "/" + mucJid);
    }

    public void testSessionAndRecorder()
    {
        task.start();
        try
        {
            TimeUnit.SECONDS.sleep(20);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        task.stop();
    }

    @Override
    protected void tearDown()
    {
        task.uninit(true);
        LibJitsi.stop();
        connection.disconnect();
    }
}
