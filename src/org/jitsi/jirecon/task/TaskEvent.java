/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.task;

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
}
