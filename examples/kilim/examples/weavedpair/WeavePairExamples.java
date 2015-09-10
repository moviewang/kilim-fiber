package kilim.examples.weavedpair;

import kilim.Pausable;
import kilim.Task;

/**
 * @author <a href="mailto:miles.wy.1@gmail.com">pf_miles</a>
 * 
 */
public class WeavePairExamples {
    public void weavePublic() throws Pausable {
        System.out.println("before yield");
        Task.yield();
        System.out.println("after yield");
    }

    protected void weaveProtected(String test) throws Pausable, Exception {
        System.out.println("before yield");
        Task.yield();
        System.out.println("after yield");
    }

    private void weavePrivate(String test, int test1) throws Pausable, RuntimeException, Exception {
        System.out.println("before yield");
        Task.yield();
        System.out.println("after yield");
    }

    void weaveDefault() throws Pausable {
        System.out.println("before yield");
        Task.yield();
        System.out.println("after yield");
    }

    // false pairs
    // different params
    void falseDiffParams() throws Pausable {
    }

    void falseDiffParams(String test1) throws Pausable {
    }

    void falseDiffParams1(int test1) throws Pausable {
    }

    void falseDiffParams1(String test1) throws Pausable {
    }

    // different exceptions
    void falseDiffExs() throws Pausable, Exception {
    }

    void falseDiffExs1() throws Pausable {
    }

    void falseDiffExs2() throws Pausable, Exception {
    }

    void falseDiffExs3() throws Pausable, RuntimeException {
    }

    // different modifiers
    void falseDiffMod() throws Pausable {
    }

    protected void falseDiffMod1() throws Pausable {
    }
}
