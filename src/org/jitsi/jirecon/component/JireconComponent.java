/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.component;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.dom4j.Attribute;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.jitsi.jirecon.Jirecon;
import org.jitsi.jirecon.JireconEvent;
import org.jitsi.jirecon.JireconEventListener;
import org.jitsi.jirecon.JireconImpl;
import org.xmpp.component.AbstractComponent;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketExtension;

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

    private final DocumentFactory docFactory = DocumentFactory.getInstance();

    private final String description = "Jirecon component.";

    private Jirecon jirecon = new JireconImpl();

    private List<RecordingSession> recordingSessions =
        new LinkedList<RecordingSession>();

    // FIXME: It seems that this attribute can be removed.
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
        System.out.println("Start Jireocn component");

        if (isStarted)
        {
            return;
        }

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

        System.out.println("namespace: "
            + element.getNamespace().getStringValue());
        System.out.println("name: " + element.getName());

        for (int i = 0; i < element.attributeCount(); i++)
        {
            final Attribute attr = element.attribute(i);

            System.out.println(attr.getName() + ": " + attr.getValue());
        }

        final String action = element.attribute("action").getValue();

        // Start recording
        if (0 == "start".compareTo(action))
        {
            startRecording(iq);
        }
        // Stop recording.
        else if (0 == "stop".compareTo(action))
        {
            stopRecording(iq);
        }

        return IQ.createResultIQ(iq);
    }

    @Override
    public void handleEvent(JireconEvent evt)
    {
        System.out.println("Task: " + evt.getMucJid() + " " + evt.getType());

        final String mucJid = evt.getMucJid();
        RecordingSession session = null;

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
            notification = createIqSet(session, "aborted", session.getRid());
        }
        else if (JireconEvent.Type.TASK_FINISED == evt.getType())
        {
            jirecon.stopJireconTask(evt.getMucJid(), true);
            recordingSessions.remove(session);
            notification = createIqSet(session, "stopped", session.getRid());
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

    private void startRecording(IQ iq)
    {
        final Element element = iq.getChildElement();

        String mucJid = element.attribute("mucjid").getValue();
        // Here we cut the 'resource' part of full-jid, because it seems
        // that we can't join a MUC with full-jid.
        mucJid = mucJid.split("/")[0];

        RecordingSession session = new RecordingSession(mucJid);
        recordingSessions.add(session);

        send(createIqResult(iq, "initiating", session.getRid()));

        jirecon.startJireconTask(mucJid);
    }

    private void stopRecording(IQ iq)
    {
        final Element element = iq.getChildElement();
        final String rid = element.attribute("rid").getValue();

        RecordingSession session = null;
        for (RecordingSession s : recordingSessions)
        {
            if (0 == rid.compareTo(s.getRid()))
            {
                session = s;
                break;
            }
        }

        // Session should never be null.
        if (null != session)
        {
            send(createIqResult(iq, "stopping", rid));
            jirecon.stopJireconTask(session.getMucJid(), true);
        }
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
        Element record = docFactory.createElement(ELEMENT_NAME, NAMESPACE);
        // TODO: Some of the attribute should be removed.
        docFactory.createAttribute(record, "action", "");
        docFactory.createAttribute(record, "status", status);
        docFactory.createAttribute(record, "mucjid", "");
        docFactory.createAttribute(record, "dst", "");
        docFactory.createAttribute(record, "rid", rid);

        IQ result = IQ.createResultIQ(iq);
        result.addExtension(new PacketExtension(record));

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
        // TODO: Some of the attribute should be removed.
        docFactory.createAttribute(record, "action", "info");
        docFactory.createAttribute(record, "status", status);
        docFactory.createAttribute(record, "mucjid", "");
        docFactory.createAttribute(record, "dst", dst);
        docFactory.createAttribute(record, "rid", rid);

        set.addExtension(new PacketExtension(record));

        return set;
    }

    private IQ createIqSet(RecordingSession session, String status, String rid)
    {
        return createIqSet(session, status, rid, "");
    }

    private class RecordingSession
    {
        private String rid;

        private String mucJid;

        private String outputPath;

        public RecordingSession(String mucJid)
        {
            this.rid = generateRid();
            this.mucJid = mucJid;
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
