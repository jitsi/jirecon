package org.jitsi.jirecon.utils;

import org.jitsi.jirecon.session.JireconSession;
import org.jitsi.jirecon.session.JireconSessionImpl;
import org.jivesoftware.smack.XMPPConnection;

/**
 * This class is used to create JireconSession and JireconRecorder instance.
 * 
 * @author lishunyang
 * 
 */
public class JireconFactoryImpl
    implements JireconFactory
{

    /**
     * Create JireconSession instance.
     */
    @Override
    public JireconSession createSession(XMPPConnection connection)
    {
        return new JireconSessionImpl(connection);
    }

    // @Override
    // public JireconRecorder createRecorder()
    // {
    // return null;
    // }

}
