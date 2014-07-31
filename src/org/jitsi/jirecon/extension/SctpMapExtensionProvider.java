/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.extension;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.xmlpull.v1.XmlPullParser;

public class SctpMapExtensionProvider
    implements PacketExtensionProvider
{

    @Override
    public PacketExtension parseExtension(XmlPullParser parser)
        throws Exception
    {
        SctpMapExtension result = new SctpMapExtension();

        if (parser.getName().equals(SctpMapExtension.ELEMENT_NAME)
            && parser.getNamespace().equals(SctpMapExtension.NAMESPACE))
        {
            result.setPort(Integer.parseInt(parser.getAttributeValue(null,
                SctpMapExtension.PORT_ATTR_NAME)));
            result.setProtocol(parser.getAttributeValue(null,
                SctpMapExtension.PROTOCOL_ATTR_NAME));
            result.setStreams(Integer.parseInt(parser.getAttributeValue(null,
                SctpMapExtension.STREAMS_ATTR_NAME)));
        }

//        boolean done = false;
//        while (!done)
//        {
//            switch (parser.next())
//            {
//            case XmlPullParser.END_TAG:
//                System.out.println("END");
//                done = true;
//                break;
//            case XmlPullParser.START_TAG:
//                System.out.println("START");
//                break;
//            case XmlPullParser.TEXT:
//                System.out.println("TEXT");
//                break;
//            }
//        }

        System.out.println(result.toXML());

        return result;
    }
}
