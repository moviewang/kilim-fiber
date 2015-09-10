package kilim.examples.basic;

import kilim.Pausable;
import kilim.Task;

/**
 * @author <a href="mailto:miles.wy.1@gmail.com">pf_miles</a>
 * 
 */
public class Sam implements ISam {

    public void test() throws Pausable {
        System.out.println("before yield");
        Task.yield();
        System.out.println("after yield");
    }
}
