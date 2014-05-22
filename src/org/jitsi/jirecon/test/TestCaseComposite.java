package org.jitsi.jirecon.test;

import junit.framework.Test;
import junit.framework.TestSuite;


public class TestCaseComposite
{
    public static Test suite()
    {
        TestSuite suite = new TestSuite("TestSuite");
        
        suite.addTestSuite(TestJingleSessionManagerImpl.class);
        
        return suite;
    }
}
