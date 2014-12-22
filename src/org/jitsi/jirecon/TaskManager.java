/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.text.*;
import java.util.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import org.jitsi.jirecon.TaskManagerEvent.*;
import org.jitsi.jirecon.protocol.extension.*;
import org.jitsi.jirecon.utils.*;
import org.jitsi.service.configuration.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.provider.*;

/**
 * The manager of <tt>Task</tt>, each <tt>Task</tt> represents a
 * recording task for specified Jitsi-meeting. 
 * 
 * @author lishunyang
 */
public class TaskManager
    implements JireconEventListener
{
    /**
     * The <tt>Logger</tt>, used to log messages to standard output.
     */
    private static final Logger logger = Logger.getLogger(TaskManager.class.getName());
    
    /**
     * List of <tt>EventListener</tt>, if something important happen,
     * they will be notified.
     */
    private List<JireconEventListener> listeners =
        new ArrayList<JireconEventListener>();

    /**
     * An instance of <tt>XMPPConnection</tt>, it is shared with every
     * <tt>JireconTask</tt>
     */
    private XMPPConnection connection;

    /**
     * Active <tt>JireconTask</tt>, map between Jitsi-meeting jid and task.
     */
    private final Map<String, Task> tasks =
        new HashMap<String, Task>();

    /**
     * The base directory to save recording files. <tt>JireconImpl</tt> will add
     * date suffix to it as a final output directory.
     */
    private String baseOutputDir;
    
    /**
     * Indicates whether <tt>JireconImpl</tt> has been initialized, it is used
     * to avoid double initialization.
     */
    private boolean isInitialized = false;

    /**
     * Initialize <tt>Jirecon</tt>.
     * <p>
     * Once this method has been executed successfully, <tt>Jirecon</tt> should
     * be ready to start working.
     * 
     * Start Libjitsi, load configuration file and create connection with XMPP
     * server.
     * 
     * @param configurationPath is the configuration file path.
     * @throws Exception if failed to initialize Jirecon.
     * 
     */
    public void init(String configurationPath) 
        throws Exception
    {
        if (isInitialized) 
        {
            logger.info("Double initialization of " + this.getClass()
                + ", ignored.");
            return;
        }
        
        logger.info(this.getClass() + "init");

        initiatePacketProviders();

        LibJitsi.start();

        System.setProperty(ConfigurationService.PNAME_CONFIGURATION_FILE_NAME,
            configurationPath);
        System.setProperty(ConfigurationService.PNAME_CONFIGURATION_FILE_IS_READ_ONLY, "true");
        final ConfigurationService configuration = LibJitsi.getConfigurationService();
        
        baseOutputDir =
            configuration.getString(ConfigurationKey.SAVING_DIR_KEY);
        if (baseOutputDir.isEmpty())
        {
            logger.info("Failed to initialize Jirecon, output directory was not set.");
            throw new Exception(
                "Failed to initialize Jirecon, ouput directory was not set.");
        }
        // Remove the suffix '/' in SAVE_DIR
        if ('/' == baseOutputDir.charAt(baseOutputDir.length() - 1))
        {
            baseOutputDir =
                baseOutputDir.substring(0, baseOutputDir.length() - 1);
        }

        final String xmppHost =
            configuration.getString(ConfigurationKey.XMPP_HOST_KEY);
        final int xmppPort =
            configuration.getInt(ConfigurationKey.XMPP_PORT_KEY, -1);
        
        try
        {
            connect(xmppHost, xmppPort);
            loginAnonymously();
        }
        catch (XMPPException e)
        {
            logger.info("Failed to initialize Jirecon, " + e.getXMPPError());
            uninit();
            throw new Exception("Failed to initialize Jirecon, "
                + e.getMessage());
        }
        
        isInitialized = true;
    }

    /**
     * Uninitialize <tt>Jirecon</tt>, prepare for GC.
     * <p>
     * <strong>Warning:</tt> If there is any residue <tt>JireconTask</tt>,
     * </tt>Jirecon</tt> will stop them and notify <tt>JireconEventListener</tt>
     * s.
     * 
     * Stop Libjitsi and close connection with XMPP server.
     * 
     */
    public void uninit()
    {
        logger.info(this.getClass() + "uninit");
        synchronized (tasks)
        {
            for (Task task : tasks.values())
            {
                task.uninit(true);
            }
        }
        closeConnection();
        LibJitsi.stop();
    }

    /**
     * Create a new recording task for a specified Jitsi-meeting.
     * <p>
     * <strong>Warning:</strong> This method is asynchronous, it will return
     * immediately while it doesn't mean the task has been started successfully.
     * If the task failed, it will notify event listeners.
     * 
     * @param mucJid indicates which Jitsi-meeting you want to record.
     * @return true if the task has been started successfully, otherwise false.
     *         Notice that the task may fail during the execution.
     */
    public boolean startJireconTask(String mucJid)
    {
        logger.info(this.getClass() + "startJireconTask: " + mucJid);

        Task task = null;
        synchronized (tasks)
        {
            if (tasks.containsKey(mucJid))
            {
                logger.info("Failed to start Jirecon by mucJid: " + mucJid
                    + ". Duplicate mucJid.");
                return false;
            }
            task = new Task();
            tasks.put(mucJid, task);
        }

        String outputDir =
            baseOutputDir + "/" + mucJid
                + new SimpleDateFormat("-yyMMdd-HHmmss").format(new Date());

        task.addEventListener(this);
        task.init(mucJid, connection, outputDir);

        task.start();
        return true;
    }

    /**
     * Stop a recording task for a specified Jitsi-meeting.
     * 
     * @param mucJid indicates which Jitsi-meeting you want to record.
     * @param keepData Whether keeping the data. Keep the output files if it is
     *            true, otherwise remove them.
     * @return true if the task has been stopped successfully, otherwise false,
     *         such as task is not found.
     */
    public boolean stopJireconTask(String mucJid, boolean keepData)
    {
        logger.info(this.getClass() + "stopJireconTask: " + mucJid);
        
        Task task = null;
        
        synchronized (tasks)
        {
            task = tasks.remove(mucJid);
        }
        
        if (null == task)
        {
            logger.info("Failed to stop Jirecon by mucJid: " + mucJid
                + ". Jid was not found.");
            return false;
        }
        else
        {
            task.stop();
            task.uninit(keepData);
        }
        return true;
    }

    /**
     * Build XMPP connection.
     * 
     * @param xmppHost is the host name of XMPP server.
     * @param xmppPort is the port of XMPP server.
     * @throws XMPPException if failed to build connection.
     */
    private void connect(String xmppHost, int xmppPort) throws XMPPException
    {
        logger.info(this.getClass() + "connect");
        ConnectionConfiguration conf =
            new ConnectionConfiguration(xmppHost, xmppPort);
        connection = new XMPPConnection(conf);
        connection.connect();
    }

    /**
     * Close XMPP connection.
     */
    private void closeConnection()
    {
        logger.info(this.getClass() + "closeConnection");
        if (connection.isConnected())
            connection.disconnect();
    }

    /**
     * Login XMPP server anonymously.
     * 
     * @throws XMPPException
     */
    private void loginAnonymously() throws XMPPException
    {
        logger.info(this.getClass() + "login");
        connection.loginAnonymously();
    }

    /**
     * Add packet provider to connection.
     */
    private void initiatePacketProviders()
    {
        logger.info(this.getClass() + "initiatePacketProviders");
        ProviderManager providerManager = ProviderManager.getInstance();

        providerManager.addIQProvider(JingleIQ.ELEMENT_NAME,
            JingleIQ.NAMESPACE, new JingleIQProvider());
        providerManager.addExtensionProvider(MediaExtension.ELEMENT_NAME,
            MediaExtension.NAMESPACE, new MediaExtensionProvider());
        providerManager.addExtensionProvider(SctpMapExtension.ELEMENT_NAME,
            SctpMapExtension.NAMESPACE, new SctpMapExtensionProvider());
    }

    /**
     * Register an event listener, if some important things happen, they'll be
     * notified.
     * 
     * @param listener is the event listener you want to register.
     */
    public void addEventListener(JireconEventListener listener)
    {
        logger.info(this.getClass() + " addEventListener");
        listeners.add(listener);
    }

    /**
     * Remove an event listener.
     * 
     * @param listener is the event listener you want to remove.
     */
    public void removeEventListener(JireconEventListener listener)
    {
        logger.info(this.getClass() + " removeEventListener");
        listeners.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleEvent(TaskManagerEvent evt)
    {
        String mucJid = evt.getMucJid();

        switch (evt.getType())
        {
        case TASK_ABORTED:
            stopJireconTask(mucJid, false);
            logger.info("Recording task of MUC " + mucJid + " failed.");
            fireEvent(evt);
            break;
        case TASK_FINISED:
            stopJireconTask(mucJid, true);
            logger.info("Recording task of MUC: " + mucJid
                + " finished successfully.");
            fireEvent(evt);
            break;
        case TASK_STARTED:
            logger.info("Recording task of MUC " + mucJid + " started.");
            fireEvent(evt);
            break;
        default:
            break;
        }
    }

    /**
     * Notify the listeners.
     * 
     * @param evt is the event that you want to send.
     */
    private void fireEvent(TaskManagerEvent evt)
    {
        for (JireconEventListener l : listeners)
        {
            l.handleEvent(evt);
        }
    }
}
