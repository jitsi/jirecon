/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.component;

import org.dom4j.*;
import org.xmpp.packet.IQ;

public class RecordingIQUtils
{
    public static final String NAMESPACE = "http://jitsi.org/protocol/jirecon";

    public static final String ELEMENT_NAME = "recording";

    public static final String ACTION_NAME = "action";

    public static final String STATUS_NAME = "status";

    public static final String OUTPUT_NAME = "dst";

    public static final String RID_NAME = "rid";

    private static final DocumentFactory docFactory = DocumentFactory
        .getInstance();

    public static IQ createIqResult(IQ relatedIq)
    {
        final IQ result = IQ.createResultIQ(relatedIq);
        final Element record =
            docFactory.createElement(ELEMENT_NAME, NAMESPACE);

        result.setChildElement(record);

        return result;
    }

    public static IQ createIqSet(String from, String to)
    {
        final IQ set = new IQ(IQ.Type.set);
        final Element record =
            docFactory.createElement(ELEMENT_NAME, NAMESPACE);

        set.setFrom(from);
        set.setTo(to);
        set.setChildElement(record);

        return set;
    }

    public static void addAttribute(IQ iq, String attrName, String attrValue)
    {
        final Element record = iq.getChildElement();

        record.add(docFactory.createAttribute(record, attrName, attrValue));
    }

    public static String getAttribute(IQ iq, String attrName)
    {
        final Element element = iq.getChildElement();

        return element.attribute(attrName).getValue();
    }

    public static boolean actionsEqual(Action action, String anotherAction)
    {
        return 0 == action.toString().compareTo(anotherAction);
    }

    public enum Action
    {
        /**
         * 
         */
        START("start"),

        /**
         * 
         */
        STOP("stop"),

        /**
         * 
         */
        INFO("info");

        private String name;

        private Action(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    public enum Status
    {
        /**
         * 
         */
        INITIATING("initiating"),

        /**
         * 
         */
        STARTED("started"),

        /**
         * 
         */
        STOPPING("stopping"),

        /**
         * 
         */
        STOPPED("stopped"),

        /**
         * 
         */
        ABORTED("aborted");

        private String name;

        private Status(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }
}
