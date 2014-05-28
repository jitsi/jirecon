/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.test;

import java.io.IOException;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQProvider;

import org.jitsi.jirecon.JireconTask;
import org.jitsi.jirecon.JireconTaskImpl;
import org.jitsi.jirecon.JireconTaskState;
import org.jitsi.jirecon.extension.MediaExtensionProvider;
import org.jitsi.jirecon.utils.JireconConfiguration;
import org.jitsi.jirecon.utils.JireconConfigurationImpl;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.MediaService;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.provider.ProviderManager;

import junit.framework.TestCase;

public class TestJireconTaskImpl
    extends TestCase
{
    private static JireconTask task;

    private final static String XMPP_HOST_KEY = "XMPP_HOST";

    private final static String XMPP_PORT_KEY = "XMPP_PORT";
    
    private static XMPPConnection connection;

    @Override
    protected void setUp()
    {
        LibJitsi.start();
        JireconConfiguration configuration =
            new JireconConfigurationImpl();
        try
        {
            configuration.loadConfiguration("jirecon.property");
        }
        catch (IOException e)
        {
            e.printStackTrace();
            configuration = null;
        }
        assertTrue(null != configuration);

        String xmppHost = null;
        int xmppPort = -1;
        xmppHost = configuration.getProperty(XMPP_HOST_KEY);
        xmppPort = Integer.valueOf(configuration.getProperty(XMPP_PORT_KEY));

        assertTrue(xmppHost.length() > 0);
        assertTrue(xmppPort > 0);

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

        task = new JireconTaskImpl();
        task.init(configuration, "it6h4jfosr6kcsor@conference.example.com",
            connection, mediaService);
    }

    public void testSessionAndRecorder()
    {
        task.start();
        try
        {
            Thread.sleep(100000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        //assertTrue(JireconTaskState.SESSION_CONSTRUCTED == task.getTaskInfo().getState());
        assertTrue(JireconTaskState.RECORDER_RECEIVING == task.getTaskInfo().getState());
        task.stop();
    }

    @Override
    protected void tearDown()
    {
        task.uninit();
        LibJitsi.stop();
        connection.disconnect();
    }
}
