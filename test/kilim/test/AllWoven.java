package kilim.test;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * 
 * @author <a href="mailto:miles.wy.1@gmail.com">pf_miles</a>
 * 
 */
public class AllWoven extends TestSuite {
    public static Test suite() {
        TestSuite ret = new AllWoven();
        ret.addTestSuite(TestBasicWeave.class);
        return ret;
    }
}
