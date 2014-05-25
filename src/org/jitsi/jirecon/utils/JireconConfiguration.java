package org.jitsi.jirecon.utils;

import java.io.IOException;

public interface JireconConfiguration
{
    public void loadConfiguration(String path) throws IOException;
    public String getProperty(String key);
}
