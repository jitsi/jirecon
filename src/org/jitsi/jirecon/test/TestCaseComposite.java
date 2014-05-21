package org.jitsi.jirecon.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.swingui.TestRunner;


public class TestCaseComposite
{
    public static Test suite()
    {
        TestSuite suite = new TestSuite("TestSuite");
        
        suite.addTestSuite(TestJingleSessionManagerImpl.class);
        
        return suite;
    }
}
