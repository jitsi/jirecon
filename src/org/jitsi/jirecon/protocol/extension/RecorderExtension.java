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
package org.jitsi.jirecon.protocol.extension;

import org.jivesoftware.smack.packet.*;

/**
 * A simple extension added to presence packets, indicating that the entity
 * is a Jirecon recorder.
 *
 * @author Boris Grozev
 */
public class RecorderExtension
        implements PacketExtension
{
    /**
     * The name of the "recorder" element.
     */
    public static final String ELEMENT_NAME = "recorder";

    /**
     * The namespace for the "recorder" element.
     */
    public static final String NAMESPACE = "http://jitsi.org/protocol/jirecon";

    /**
     * The name of the "state" attribute.
     */
    public static final String STATE_ATTR_NAME = "state";

    /**
     * The value of the "state" attribute.
     */
    private String state = null;

    /**
     * Initializes a new <tt>RecorderExtension</tt> with the given value of the
     * state attribute.
     * @param state the value for the state attribute.
     */
    public RecorderExtension(String state)
    {
        this.state = state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getElementName()
    {
        return ELEMENT_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getNamespace()
    {
        return NAMESPACE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toXML()
    {
        StringBuilder builder = new StringBuilder();

        builder.append("<").append(ELEMENT_NAME).append(" xmlns='")
                .append(NAMESPACE).append("'");
        if (state != null)
            builder.append(" ").append(STATE_ATTR_NAME).append("='")
                .append(state).append("'");
        builder.append("/>");

        return builder.toString();
    }
}
