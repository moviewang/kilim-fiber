package kilim.examples.basic;

import kilim.Pausable;
import kilim.Task;

/**
 * a basic example for test
 * 
 * @author <a href="mailto:miles.wy.1@gmail.com">pf_miles</a>
 * 
 */
public class App extends Task {

    public static void main(String... args) throws Exception {
        App a = new App();
        System.out.println("first run:");
        a.run();
        System.out.println("second run:");
        a.run();
    }

    public void execute() throws Pausable, Exception {
        Sam s = new Sam();
        System.out.println("Hello");
        s.test();
        System.out.println("World");
    }

}
