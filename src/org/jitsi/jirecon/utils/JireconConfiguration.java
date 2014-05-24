package org.jitsi.jirecon.utils;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.jitsi.util.Logger;

public class JireconConfiguration
{
    private Properties props;
    private Logger logger;
    
    public JireconConfiguration()
    {
        logger = Logger.getLogger(this.getClass());
    }
    
    public void loadConfiguration(String path) throws IOException {
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
    
    public static void main(String[] args)
    {
        
    }
}
