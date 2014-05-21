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
                int count = parser.getAttributeCount();

                if (MediaExtension.ELEMENT_NAME.equals(name))
                {
                    done = true;
                }
                break;
            }

            case XmlPullParser.START_TAG:
            {
                String name = parser.getName();
                int count = parser.getAttributeCount();

                if (count > 0)
                {
                    result.list.add(new Source(parser.getAttributeValue(0),
                        parser.getAttributeValue(1), parser
                            .getAttributeValue(2)));
                }
                break;
            }

            case XmlPullParser.TEXT:
            {
                String name = parser.getName();
                int count = parser.getAttributeCount();
                break;
            }
            }

        }
        return result;

    }
}
