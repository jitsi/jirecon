/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon;

import java.util.EventObject;

public abstract class JireconEvent extends EventObject
{

    public JireconEvent(Object source)
    {
        super(source);
    }

}
