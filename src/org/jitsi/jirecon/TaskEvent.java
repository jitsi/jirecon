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

/**
 * Task event which can be used by <tt>JireconSession</tt> and
 * <tt>JireconRecorder</tt> to notify outside system, such as
 * <tt>JireconTask</tt>.
 * 
 * @author lishunyang
 * @see TaskEvent.Type
 */
public class TaskEvent
{
    /**
     * Type of this event.
     */
    private Type type;

    /**
     * Construction method.
     * 
     * @param type
     */
    public TaskEvent(Type type)
    {
        this.type = type;
    }

    /**
     * Get event type.
     * 
     * @return event type.
     */
    public Type getType()
    {
        return type;
    }

    /**
     * <tt>JireconTaskEvent</tt> type.
     * 
     * @author lishunyang
     * 
     */
    public static enum Type
    {
        /**
         * New participant came.
         */
        PARTICIPANT_CAME("PARTICIPANT_CAME"),

        /**
         * One participant left.
         */
        PARTICIPANT_LEFT("PARTICIPANT_LEFT"),

        /**
         * Recorder has broken for some reasons.
         */
        RECORDER_ABORTED("RECORDER_ABORTED");

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
     * <tt>TaskEvent</tt> listener.
     * 
     * @author lishunyang
     * @see TaskEvent
     * 
     */
    public interface TaskEventListener
    {
        /**
         * Handle the event.
         * 
         * @param event
         */
        public void handleTaskEvent(TaskEvent event);
    }
}
