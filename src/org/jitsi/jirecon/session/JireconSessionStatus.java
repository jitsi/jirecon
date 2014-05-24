/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.session;

public enum JireconSessionStatus
{
    INITIATING,
    INTIATING_SESSION_OK,
    INITIATING_SESSION_FAILED,
    INITIATING_CONNECTIVITY_OK,
    INITIATING_CONNECTIVITY_FAILED,
    ABORTED,
    CONSTRUCTED,
}
