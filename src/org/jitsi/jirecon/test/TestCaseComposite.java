/*
 * Jirecon, the Jitsi recorder container.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jitsi.jirecon.test;

import junit.framework.*;

public class TestCaseComposite
{
    public static Test suite()
    {
        TestSuite suite = new TestSuite("TestSuite");

        // suite.addTestSuite(TestJireconImpl.class);
        suite.addTestSuite(TestJireconTaskImpl.class);

        return suite;
    }
}
