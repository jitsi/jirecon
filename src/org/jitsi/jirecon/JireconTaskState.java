/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

public enum JireconTaskState
{
    INITIATED,
    SESSION_INITIATING,
    SESSION_CONSTRUCTED,
    RECORDER_INITIATING,
    RECORDER_RECEIVING,
    RECORDER_RECORDING,
    ABORTED
}
