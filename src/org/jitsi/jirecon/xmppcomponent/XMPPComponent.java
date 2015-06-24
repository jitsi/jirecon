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

import java.util.*;
import org.jitsi.jirecon.*;
import org.jitsi.jirecon.TaskManagerEvent.*;
import org.jitsi.util.*;
import org.xmpp.component.*;
import org.xmpp.packet.*;

/**
 * Implements <tt>org.xmpp.component.Component</tt> to provide <tt>Jirecon</tt>
 * as an external XMPP component.
 * 
 * In the process of interaction, client is the active side while Jirecon
 * component is passive side.
 * <ol>
 * <li>
 * 1. Client sends commands to Jirecon component, letting component do
 * something.</li>
 * <li>
 * 2. Jirecon component listens to the commands, records meeting and sends
 * reply.</li>
 * </ol>
 * <p>
 * 
 * Here are two simple interaction examples. The numbers in brackets indicate
 * packet type(see packet introduction below).
 * 
 * <pre>
 * 1. Normal scene
 * 
 *   client                                  XMPP component
 *     |                                            |
 *     |  'Hey, start recording'(1)                 |
 * 1   |------------------------------------------->|
 *     |                    'Roger, initiating'(6)  |
 * 2   |<-------------------------------------------|
 *     |           'Hey, recording has started'(3)  |
 * 3   |<-------------------------------------------|
 *     |  'Roger'(8)                                |
 * 4   |------------------------------------------->|
 *     |                                            |
 *     |               Recording...                 |
 *     |                                            |
 *     |  'Hey, stop recording'(2)                  |
 * 5   |------------------------------------------->|
 *     |                      'Roger, stopping'(7)  |
 * 6   |<-------------------------------------------|
 *     |           'Hey, recording has stopped'(4)  |
 * 7   |<-------------------------------------------|
 *     |  'Roger'(8)                                |
 * 8   |------------------------------------------->|
 *     |                                            |
 * 
 * 
 * 2. Abnormal scene
 * 
 *   client                                  XMPP component
 *     |                                            |
 *     |  'Hey, start recording'(1)                 |
 * 1   |------------------------------------------->|
 *     |                    'Roger, initiating'(6)  |
 * 2   |<-------------------------------------------|
 *     |           'Hey, recording has started'(3)  |
 * 3   |<-------------------------------------------|
 *     |  'Roger'(8)                                |
 * 4   |------------------------------------------->|
 *     |                                            |
 *     |               Recording...                 |
 *     |                                            |
 *     |           'Hey, recording has aborted'(5)  |
 * 5   |<-------------------------------------------|
 *     |  'Roger'(8)                                |
 * 6   |------------------------------------------->|
 *     |                                            |
 * </pre>
 * <p>
 * 
 * Some rules:
 * <ol>
 * <li>
 * 1. All packet should set "rid" in order to identify recording session, except
 * for the first command packet sent from client, because ONLY Jirecon component
 * can generate "rid".</li>
 * <li>
 * 2. All packet sent from Jirecon component should set "status", to tell client
 * how's the recording session going.</li>
 * </ol>
 * 
 * Now we come up with 8 type of IQ packet(IQ-set or IQ-result) of interaction:
 * 
 * <pre>
 * +----+----+-------+--------+----------+----------+------------------+
 * |No. |id  |action |status  |From      |To        |Meaning           |
 * +----+----+-------+--------+----------+----------+------------------+
 * |1   |    |start  |        |client    |component |Start a recording |
 * +----+----+-------+--------+----------+----------+------------------+
 * |2   |yes |stop   |        |client    |component |Stop a recording  |
 * +----+----+-------+--------+----------+----------+------------------+
 * |3   |yes |info   |started |component |client    |Notify client the |
 * |    |    |       |        |          |          |recording has     |
 * |    |    |       |        |          |          |started           |
 * +----+----+-------+--------+----------+----------+------------------+
 * |4   |yes |info   |stopped |component |client    |Notify client the |
 * |    |    |       |        |          |          |recording has     |
 * |    |    |       |        |          |          |stopped           |
 * +----+----+-------+--------+----------+----------+------------------+
 * |5   |yes |info   |aborted |component |client    |Notify client the |
 * |    |    |       |        |          |          |recording has     |
 * |    |    |       |        |          |          |aborted           |
 * +----+----+-------+--------+----------+----------+------------------+
 * |6   |yes |info   |initiat-|component |client    |Notify client the |
 * |    |    |       |ing     |          |          |recording is      |
 * |    |    |       |        |          |          |initiating        |
 * +----+----+-------+--------+----------+----------+------------------+
 * |7   |yes |info   |stopping|component |client    |Notify client the |
 * |    |    |       |        |          |          |recording is      |
 * |    |    |       |        |          |          |stopping          |
 * +----+----+-------+--------+----------+----------+------------------+
 * |8   |yes |info   |        |client    |component |Ack packet        |
 * +----+----+-------+--------+----------+----------+------------------+
 * </pre>
 * 
 * @author lishunyang
 * @see TaskManager
 */
public class XMPPComponent
    extends AbstractComponent
    implements JireconEventListener
{
    /**
     * Logger.
     */
    public static Logger logger = Logger.getLogger(XMPPComponent.class);

    /**
     * Configuration file path.
     */
    public String configurationPath;

    /**
     * Local jid.
     */
    private String localJid;

    /**
     * Name of component.
     */
    private final String name = "Jirecon component";

    /**
     * Description of component.
     */
    private final String description = "Jirecon component.";

    /**
     * Main part of <tt>JireconComponent</tt>.
     */
    private final TaskManager jirecon = new TaskManager();

    /**
     * Recording sessions. It is used for caching some information.
     */
    private final List<RecordingSession> recordingSessions =
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

    /**
     * Construction method.
     * 
     * @param localJid Jid of this component.
     * @param configurationPath Path of configuration file.
     */
    public XMPPComponent(String localJid, String configurationPath)
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

        logger.info("Start Jirecon component");

        jirecon.addEventListener(this);
        try
        {
            jirecon.init(configurationPath);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            isStarted = false;
        }

        isStarted = true;

        logger.info("Jirecon component has been started successfully.");
    }

    @Override
    public void preComponentShutdown()
    {
        logger.info("Shutdown Jirecon component");

        /*
         * If there is any recording session hasn'e been finished, "uninit"
         * method will stop them.
         */
        jirecon.uninit();
        jirecon.removeEventListener(this);

        isStarted = false;
    }

    @Override
    protected IQ handleIQGet(IQ iq)
    {
        /*
         * According to the documentation of AbstractComponent, We have to return
         * a result iq, otherwise component will send error iq back to remote
         * peer.
         */
        logger.info("RECV IQ GET: " + iq.toXML());
        return IQ.createResultIQ(iq);
    }

    @Override
    protected IQ handleIQSet(IQ iq) throws Exception
    {
        logger.info("RECV IQ SET: " + iq.toXML());

        final String action =
            RecordingIqUtils.getAttribute(iq, RecordingIqUtils.ACTION_NAME);
        IQ result = null;

        // Start recording
        if (0 == action.compareTo(RecordingIqUtils.Action.START.toString()))
        {
            result = startRecording(iq);
        }
        // Stop recording.
        else if (0 == action.compareTo(RecordingIqUtils.Action.STOP.toString()))
        {
            result = stopRecording(iq);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     * 
     * Handle event from <tt>Jirecon</tt>, such as some recording task has been
     * started/stopped/aborted.
     */
    @Override
    public void handleEvent(TaskManagerEvent evt)
    {
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

            if (TaskManagerEvent.Type.TASK_ABORTED == evt.getType())
            {
                jirecon.stopJireconTask(evt.getMucJid(), false);
                recordingSessions.remove(session);

                notification =
                    createIqSet(session,
                        RecordingIqUtils.Status.ABORTED.toString(),
                        session.getRid());
            }
            else if (TaskManagerEvent.Type.TASK_FINISED == evt.getType())
            {
                jirecon.stopJireconTask(evt.getMucJid(), true);
                recordingSessions.remove(session);

                notification =
                    createIqSet(session,
                        RecordingIqUtils.Status.STOPPED.toString(),
                        session.getRid());
            }
            else if (TaskManagerEvent.Type.TASK_STARTED == evt.getType())
            {
                notification =
                    createIqSet(session,
                        RecordingIqUtils.Status.STARTED.toString(),
                        session.getRid(), session.getOutputPath());
            }

            if (null != notification)
            {
                send(notification);
            }
        }
    }

    /**
     * Start a recording session according to a "start" command IQ.
     * 
     * @param iq "start" command IQ.
     * @return The result IQ which will be sent back to client.
     */
    private IQ startRecording(IQ iq)
    {
        String mucJid =
            RecordingIqUtils.getAttribute(iq, RecordingIqUtils.MUCJID_NAME);

        RecordingSession newSession = null;

        synchronized (recordingSessions)
        {
            for (RecordingSession session : recordingSessions)
            {
                if (session.getMucJid().equals(mucJid))
                {
                    logger.error("Failed to start a recording session,"
                                    + " already recording.");
                    return createIqResult(
                            iq,
                            RecordingIqUtils.Status.ABORTED.toString(),
                            null);
                }
            }

            newSession = new RecordingSession(mucJid, iq.getFrom().toString());
            recordingSessions.add(newSession);
        }

        jirecon.startJireconTask(mucJid);

        return createIqResult(iq,
            RecordingIqUtils.Status.INITIATING.toString(), newSession.getRid());
    }

    /**
     * Stop a specified recording session according to a "stop" command IQ.
     * 
     * @param iq "stop" command IQ.
     * @return The result IQ which will be sent back to client.
     */
    private IQ stopRecording(IQ iq)
    {
        final String rid =
            RecordingIqUtils.getAttribute(iq, RecordingIqUtils.RID_NAME);

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

        return createIqResult(iq, RecordingIqUtils.Status.STOPPING.toString(),
            rid);
    }

    /**
     * As for <tt>JireconComponent</tt>, it will send two kinds of "result" IQ
     * (action="info"). The only difference between them is the attribute
     * "status":
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
     * @param iq Associated IQ.
     * @param status Value of attribute "status".
     * @param rid Value of attribute "rid".
     * @return Result IQ.
     */
    private IQ createIqResult(IQ iq, String status, String rid)
    {
        IQ result = RecordingIqUtils.createIqResult(iq);

        RecordingIqUtils.addAttribute(result, RecordingIqUtils.STATUS_NAME,
            status);

        if (rid != null)
            RecordingIqUtils.addAttribute(result, RecordingIqUtils.RID_NAME, rid);

        return result;
    }

    /**
     * As for <tt>JireconComponent</tt>, it will send three kinds of "set" IQ
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
     * @param session Associated recording session.
     * @param status Value of attribute "status".
     * @param rid Value of attribute "rid".
     * @param dst Value of attribute "dst".
     * @return Set IQ.
     */
    private IQ createIqSet(RecordingSession session, String status, String rid,
        String dst)
    {
        IQ set = RecordingIqUtils.createIqSet(localJid, session.getClientJid());

        RecordingIqUtils.addAttribute(set, RecordingIqUtils.ACTION_NAME,
            RecordingIqUtils.Action.INFO.toString());

        RecordingIqUtils.addAttribute(set, RecordingIqUtils.OUTPUT_NAME, dst);

        RecordingIqUtils
            .addAttribute(set, RecordingIqUtils.STATUS_NAME, status);

        if (null != rid)
        {
            RecordingIqUtils.addAttribute(set, RecordingIqUtils.RID_NAME, rid);
        }

        return set;
    }

    /**
     * Create setIQ without attribute "dst".
     * 
     * @param session Associated recording session.
     * @param status Value of attribute "status".
     * @param rid Value of attribute "rid".
     * @return Set IQ.
     */
    private IQ createIqSet(RecordingSession session, String status, String rid)
    {
        return createIqSet(session, status, rid, null);
    }

    /**
     * Represent a recording session. It's used for encapsulating some
     * information.
     * 
     * @author lishunyang
     * 
     */
    private class RecordingSession
    {
        /**
         * Recording session id.
         */
        private String rid;

        /**
         * Jid of recorded meeting.
         */
        private String mucJid;

        /**
         * Jid of client which starts this recording session.
         */
        private String clientJid;

        /**
         * Path of recording output files.
         */
        private String outputPath;

        /**
         * Construction method.
         * 
         * @param mucJid Jid of the recorded meeting.
         * @param clientJid Jid of the client which starts this recording
         *            session.
         */
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

        /**
         * Generate a random rid string(32 chars length) for this recording
         * session.
         * 
         * @return
         */
        private String generateRid()
        {
            return UUID.randomUUID().toString().replace("-", "");
        }

        /*
         * TODO: This should be associated with JireconImpl, but we haven't
         * decided the form yet, so just leave it now.
         */
        /**
         * Generate output path.
         * 
         * @return
         */
        private String generateOutputPath()
        {
            return "./output/" + mucJid + "/";
        }
    }
}
