/*
 * Jirecon, the Jitsi recorder container.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jirecon.extension;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.xmlpull.v1.XmlPullParser;

public class MediaExtensionProvider
    implements PacketExtensionProvider
{

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
//                int count = parser.getAttributeCount();

                if (MediaExtension.ELEMENT_NAME.equals(name))
                {
                    done = true;
                }
                break;
            }

            case XmlPullParser.START_TAG:
            {
//                String name = parser.getName();
                int count = parser.getAttributeCount();

                if (count > 0)
                {
                    String type = parser.getAttributeValue(0);
                    String ssrc = parser.getAttributeValue(1);
                    String direction = parser.getAttributeValue(2);
                    if (type.equalsIgnoreCase("audio") || type.equalsIgnoreCase("video"))
                    {
                        result.setSsrc(type, ssrc);
                        result.setDirection(type, direction);
                    }
                }
                break;
            }

            case XmlPullParser.TEXT:
            {
//                String name = parser.getName();
//                int count = parser.getAttributeCount();
                break;
            }
            }

        }
        return result;

    }
}
