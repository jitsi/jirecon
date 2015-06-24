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
