/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.util.*;

/**
 * Running event of <tt>TaskManager</tt>, which means some important things
 * happened.
 * 
 * @author lishunyang
 * 
 */
public class TaskManagerEvent
{
    /**
     * Event type.
     */
    private Type type;

    /**
     * MUC jid, represents a JireconTask.
     */
    private String mucJid;

    /**
     * Construction method.
     * 
     * @param source indicates where this event comes from.
     * @param type indicates the event type.
     */
    public TaskManagerEvent(String mucJid, Type type)
    {
        this.mucJid = mucJid;
        this.type = type;
    }

    /**
     * Get event type.
     * 
     * @return event type
     */
    public Type getType()
    {
        return type;
    }

    /**
     * Get MUC jid.
     * 
     * @return jid of MUC.
     */
    public String getMucJid()
    {
        return mucJid;
    }

    /**
     * <tt>JireconEvent</tt> type.
     * 
     * @author lishunyang
     * 
     */
    public enum Type
    {
        /**
         * Task started.
         */
        TASK_STARTED("TASK_STARTED"),
        
        /**
         * Task failed.
         */
        TASK_ABORTED("TASK_ABORTED"),

        /**
         * Task finished.
         */
        TASK_FINISED("TASK_FINISHED");

        private String name;

        private Type(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }
    
    /**
     * Listener interface of <tt>JireconEvent</tt>.
     * 
     * @author lishunyang
     * @see TaskManagerEvent
     */
    public interface JireconEventListener
        extends EventListener
    {
        /**
         * Handle the specified <tt>JireconEvent</tt>.
         * 
         * @param evt is the specified event.
         */
        void handleEvent(TaskManagerEvent evt);
    }
}
