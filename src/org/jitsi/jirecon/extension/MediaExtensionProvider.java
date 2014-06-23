/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.extension;

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
                    String type = parser.getAttributeValue(0);
                    String ssrc = parser.getAttributeValue(1);
                    String direction = parser.getAttributeValue(2);
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
