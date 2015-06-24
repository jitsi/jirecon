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
import org.jivesoftware.smack.provider.*;
import org.xmlpull.v1.XmlPullParser;

/**
 * The <tt>MediaExtensionProvider</tt> parses "media" elements into
 * <tt>MediaExtension</tt> instances.
 * 
 * @author lishunyang
 * @see MediaExtension
 */
public class MediaExtensionProvider
    implements PacketExtensionProvider
{
    /**
     * {@inheritDoc}
     */
    @Override
    public PacketExtension parseExtension(XmlPullParser parser)
        throws Exception
    {
        MediaExtension result = new MediaExtension();
        boolean done = false;

        while (!done)
        {
            switch (parser.next())
            {
            case XmlPullParser.END_TAG:
            {
                String name = parser.getName();

                if (MediaExtension.ELEMENT_NAME.equals(name))
                {
                    done = true;
                }
                break;
            }

            case XmlPullParser.START_TAG:
            {
                int count = parser.getAttributeCount();

                if (count > 0)
                {
                    String type = parser.getAttributeValue("", "type");
                    String ssrc = parser.getAttributeValue("", "ssrc");
                    String direction = parser.getAttributeValue("", "direction");
                    if (type.equalsIgnoreCase("audio")
                        || type.equalsIgnoreCase("video"))
                    {
                        result.setSsrc(type, ssrc);
                        result.setDirection(type, direction);
                    }
                }
                break;
            }

            case XmlPullParser.TEXT:
                break;
            }
        }
        return result;
    }
}
