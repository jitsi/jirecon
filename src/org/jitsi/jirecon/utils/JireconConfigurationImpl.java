/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.utils;

import java.io.*;
import java.util.Properties;

import org.jitsi.util.Logger;

public class JireconConfigurationImpl
    implements JireconConfiguration
{
    private Properties props;

    private Logger logger;

    public JireconConfigurationImpl()
    {
        logger = Logger.getLogger(this.getClass());
    }

    public void loadConfiguration(String path) throws IOException
    {
        props = new Properties();
        InputStream in = null;
        try
        {
            in = new BufferedInputStream(new FileInputStream(path));
            props.load(in);
        }
        catch (FileNotFoundException e)
        {
            logger.fatal("JireconConfiguration: Configuration file not found.");
            e.printStackTrace();
            throw new IOException();
        }
        catch (IOException e)
        {
            logger.fatal("JireconConfiguration: Failed to load properties");
            e.printStackTrace();
            throw new IOException();
        }
        finally
        {
            if (null != in)
            {
                in.close();
            }
        }
    }

    public String getProperty(String key)
    {
        return props.getProperty(key);
    }
}
