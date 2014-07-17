/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.component;

import java.util.*;

import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.dom4j.*;
import org.jitsi.jirecon.*;
import org.xmpp.component.AbstractComponent;
import org.xmpp.packet.*;

/**
 * Implements <tt>org.xmpp.component.Component</tt> to provide <tt>Jirecon</tt> as an
 * external XMPP component.
 * 
 * @author lishunyang
 * @see Jirecon
 */
public class JireconComponentImpl
    extends AbstractComponent
    implements JireconEventListener
{
    /**
     * Configuration file path.
     */
    public String configurationPath;
    
    private String localJid;

    private final String name = "Jirecon component";
    
    private final String description = "Jirecon component.";

    private Jirecon jirecon = new JireconImpl();

    private List<RecordingSession> recordingSessions =
        new LinkedList<RecordingSession>();

    /**
     * Indicate whether the <tt>JireconComponent</tt> has been started. It is
     * used for:
     * <ol>
     * <li>
     * Avoiding double initialization.</li>
     * <li>
     * Identify whether <tt>JireconComponent</tt> can work normally.</li>
     * </ol>
     */
    private boolean isStarted = false;

    public JireconComponentImpl(String localJid, String configurationPath)
    {
        this.localJid = localJid;
        this.configurationPath = configurationPath;
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public void preComponentStart()
    {
        if (isStarted)
        {
            return;
        }

        System.out.println("Start Jireocn component");

        jirecon.addEventListener(this);
        try
        {
            jirecon.init(configurationPath);
        }
        catch (OperationFailedException e)
        {
            e.printStackTrace();
            isStarted = false;
        }

        isStarted = true;

        System.out.println("Jireocn component has been started successfully.");
    }

    @Override
    public void preComponentShutdown()
    {
        System.out.println("Shutdown Jireocn component");

        jirecon.uninit();
        jirecon.removeEventListener(this);

        isStarted = false;
    }

    @Override
    protected void send(Packet packet)
    {
        System.out.println("SEND: " + packet.toXML());

        super.send(packet);
    }

    @Override
    protected IQ handleIQGet(IQ iq)
    {
        System.out.println("RECV IQGET: " + iq.toXML());

        /*
         * According to the documentation of AbstracComponent, We have to return
         * a result iq, otherwise component will send error iq back to remote
         * peer.
         */
        return IQ.createResultIQ(iq);
    }

    @Override
    protected IQ handleIQSet(IQ iq) 
        throws Exception
    {
        System.out.println("RECV IQSET: " + iq.toXML());

        final String action = RecordingIQUtils.getAttribute(iq, RecordingIQUtils.ACTION_NAME);
        IQ result = null;

        // Start recording
        if (RecordingIQUtils
            .actionsEqual(RecordingIQUtils.Action.START, action))
        {
            result = startRecording(iq);
        }
        // Stop recording.
        else if (RecordingIQUtils.
            actionsEqual(RecordingIQUtils.Action.STOP, action))
        {
            result = stopRecording(iq);
        }

        return result;
    }

    @Override
    public void handleEvent(JireconEvent evt)
    {
        System.out.println("Task: " + evt.getMucJid() + " " + evt.getType());

        final String mucJid = evt.getMucJid();
        RecordingSession session = null;

        synchronized (recordingSessions)
        {
            for (RecordingSession s : recordingSessions)
            {
                if (0 == mucJid.compareTo(s.getMucJid()))
                {
                    session = s;
                    break;
                }
            }

            // Session should never be null.
            if (null == session)
                return;

            IQ notification = null;
            if (JireconEvent.Type.TASK_ABORTED == evt.getType())
            {
                jirecon.stopJireconTask(evt.getMucJid(), false);
                recordingSessions.remove(session);
                
                notification =
                    createIqSet(
                        session, 
                        RecordingIQUtils.Status.ABORTED.toString(), 
                        session.getRid());
            }
            else if (JireconEvent.Type.TASK_FINISED == evt.getType())
            {
                jirecon.stopJireconTask(evt.getMucJid(), true);
                recordingSessions.remove(session);
                
                notification =
                    createIqSet(
                        session,
                        RecordingIQUtils.Status.STOPPED.toString(),
                        session.getRid());
            }
            else if (JireconEvent.Type.TASK_STARTED == evt.getType())
            {
                notification =
                    createIqSet(
                        session,
                        RecordingIQUtils.Status.STARTED.toString(),
                        session.getRid(), 
                        session.getOutputPath());
            }

            if (null != notification)
            {
                send(notification);
            }
        }
    }

    private IQ startRecording(IQ iq)
    {
        final Element element = iq.getChildElement();

        String mucJid = element.attribute("mucjid").getValue();
        /*
         * Here we cut the 'resource' part of full-jid, because it seems that we
         * can't join a MUC with full-jid.
         */
        mucJid = mucJid.split("/")[0];

        RecordingSession session =
            new RecordingSession(mucJid, iq.getFrom().toString());
        
        synchronized (recordingSessions)
        {
            recordingSessions.add(session);
        }

        jirecon.startJireconTask(mucJid);

        return createIqResult(iq, "initiating", session.getRid());
    }

    private IQ stopRecording(IQ iq)
    {
        final Element element = iq.getChildElement();
        final String rid = element.attribute("rid").getValue();

        RecordingSession session = null;
        synchronized (recordingSessions)
        {
            for (RecordingSession s : recordingSessions)
            {
                if (0 == rid.compareTo(s.getRid()))
                {
                    session = s;
                    break;
                }
            }
        }

        // Session should never be null.
        if (null != session)
        {
            jirecon.stopJireconTask(session.getMucJid(), true);
        }

        return createIqResult(iq, "stopping", rid);
    }

    /**
     * As for <tt>JireconComponent</tt>, there are two kinds of "result" IQ
     * (action="notification"). The only difference between them is the
     * attribute "status":
     * <ol>
     * <li>
     * "initiating". Notify client the "start" command has been received.</li>
     * <li>
     * "stopping". Notify client the "stop" command has been received.</li>
     * </ol>
     * <p>
     * <strong>Warning:</strong> These "result" IQs should be sent back to
     * client immediately after receiving "set" IQ.
     * 
     * @param iq
     * @param status
     * @param rid
     * @return
     */
    private IQ createIqResult(IQ iq, String status, String rid)
    {
        IQ result = RecordingIQUtils.createIqResult(iq);

        RecordingIQUtils.addAttribute(
            result, 
            RecordingIQUtils.STATUS_NAME, 
            status);
        
        RecordingIQUtils.addAttribute(
            result, 
            RecordingIQUtils.RID_NAME, 
            rid);

        return result;
    }

    /**
     * As for <tt>JireconComponent</tt>, there are three kinds of "set" IQ
     * (action="info"). The only difference between them is the attribute
     * "status":
     * <ol>
     * <li>
     * "started". Notify client that some recording task has been started.</li>
     * <li>
     * "stopped". Notify to client that some recording task has been stopped.</li>
     * <li>
     * "aborted". Notify to client that some recording task has been aborted.</li>
     * </ol>
     * 
     * @param session
     * @param status
     * @param rid
     * @param dst
     * @return
     */
    private IQ createIqSet(RecordingSession session, String status, String rid,
        String dst)
    {
        IQ set =
            RecordingIQUtils.createIqSet(localJid, session.getClientJid());

        RecordingIQUtils.addAttribute(
            set, 
            RecordingIQUtils.ACTION_NAME,
            RecordingIQUtils.Action.INFO.toString());
        
        RecordingIQUtils.addAttribute(
            set, 
            RecordingIQUtils.OUTPUT_NAME, 
            dst);
        
        RecordingIQUtils.addAttribute(
            set, 
            RecordingIQUtils.STATUS_NAME, 
            status);
        
        if (null != rid)
        {
            RecordingIQUtils.addAttribute(
                set, 
                RecordingIQUtils.RID_NAME, 
                rid);
        }

        return set;
    }

    private IQ createIqSet(RecordingSession session, String status, String rid)
    {
        return createIqSet(session, status, rid, null);
    }

    private class RecordingSession
    {
        private String rid;

        private String mucJid;

        private String clientJid;

        private String outputPath;

        public RecordingSession(String mucJid, String clientJid)
        {
            this.rid = generateRid();
            this.mucJid = mucJid;
            this.clientJid = clientJid;
            this.outputPath = generateOutputPath();
        }

        public String getRid()
        {
            return rid;
        }

        public String getMucJid()
        {
            return mucJid;
        }

        public String getClientJid()
        {
            return clientJid;
        }

        public String getOutputPath()
        {
            return outputPath;
        }

        private String generateRid()
        {
            return UUID.randomUUID().toString().replace("-", "");
        }

        /*
         * TODO: This should be associated with JireconImpl, but we haven't
         * decided the form yet, so just leave it now.
         */
        private String generateOutputPath()
        {
            return "./output/" + mucJid + "/";
        }
    }
}
