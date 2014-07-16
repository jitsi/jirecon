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

public class JireconComponent
    extends AbstractComponent
    implements JireconEventListener
{
    public static final String CONFIURATION_PATH = "./jirecon.properties";

    // TODO: Some of those attributes should be moved to configuration file.
    public static final String SUBDOMAIN = "jirecon";

    public static final String DOMAIN = "example.com";

    public static final String SECRET = "xxxxx";

    public static final int PORT = 5275;

    public static final String ELEMENT_NAME = "recording";

    public static final String NAMESPACE = "http://jitsi.org/protocol/jirecon";
    
    public static final String LOCAL_JID = "jirecon@" + SUBDOMAIN + "." + DOMAIN;

    private final DocumentFactory docFactory = DocumentFactory.getInstance();

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

    @Override
    public String getDescription()
    {
        return description;
    }

    @Override
    public String getDomain()
    {
        return DOMAIN;
    }

    @Override
    public String getName()
    {
        return SUBDOMAIN;
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
            jirecon.init(CONFIURATION_PATH);
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

        // According to the documentation of AbstracComponent, We have to return
        // a result iq.
        return IQ.createResultIQ(iq);
    }

    @Override
    protected IQ handleIQSet(IQ iq) throws Exception
    {
        System.out.println("RECV IQSET: " + iq.toXML());

        Element element = iq.getChildElement();

        final String action = element.attribute("action").getValue();
        IQ result = null;

        // Start recording
        if (0 == "start".compareTo(action))
        {
            result = startRecording(iq);
        }
        // Stop recording.
        else if (0 == "stop".compareTo(action))
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
                    createIqSet(session, "aborted", session.getRid());
            }
            else if (JireconEvent.Type.TASK_FINISED == evt.getType())
            {
                jirecon.stopJireconTask(evt.getMucJid(), true);
                recordingSessions.remove(session);
                notification =
                    createIqSet(session, "stopped", session.getRid());
            }
            else if (JireconEvent.Type.TASK_STARTED == evt.getType())
            {
                notification =
                    createIqSet(session, "started", session.getRid(),
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
        // Here we cut the 'resource' part of full-jid, because it seems
        // that we can't join a MUC with full-jid.
        mucJid = mucJid.split("/")[0];

        RecordingSession session = new RecordingSession(mucJid, iq.getFrom().toString());
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
        IQ result = IQ.createResultIQ(iq);
        Element record = docFactory.createElement(ELEMENT_NAME, NAMESPACE);

        record.add(docFactory.createAttribute(record, "status", status));
        record.add(docFactory.createAttribute(record, "rid", rid));
        
        result.setChildElement(record);

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
        IQ set = new IQ(IQ.Type.set);
        Element record = docFactory.createElement(ELEMENT_NAME, NAMESPACE);
        
        record.add(docFactory.createAttribute(record, "action", "info"));
        record.add(docFactory.createAttribute(record, "status", status));
        record.add(docFactory.createAttribute(record, "dst", dst));
        if (null != rid)
            record.add(docFactory.createAttribute(record, "rid", rid));

        set.setFrom(LOCAL_JID);
        set.setTo(session.getClientJid());
        set.setChildElement(record);

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

        // TODO: This should be associated with JireconImpl
        private String generateOutputPath()
        {
            return "./output/" + mucJid + "/";
        }
    }
}
