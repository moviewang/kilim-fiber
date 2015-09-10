package kilim.test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import kilim.Task;
import kilim.examples.basic.App;
import kilim.examples.weavedpair.WeavePairExamples;

/**
 * normal weaving case
 * 
 * @author <a href="mailto:miles.wy.1@gmail.com">pf_miles</a>
 * 
 */
public class TestBasicWeave extends TestCase {

    // normal case with SAM interface * SAM class
    public void testNormal() throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream mem = new PrintStream(bo, false, "UTF-8");
        PrintStream temp = System.out;
        try {
            // change the sysout
            System.setOut(mem);

            App.main();

            /**
             * the output should be:
             * 
             * <pre>
             * first run:
             * Hello
             * before yield
             * second run:
             * after yield
             * World
             * </pre>
             */
            mem.flush();
        } finally {
            // restore the sysout
            System.setOut(temp);
        }
        String rst = new String(bo.toByteArray(), "UTF-8");
        // System.out.println(rst);
        assertTrue(("first run:\n" + "Hello\n" + "before yield\n" + "second run:\n" + "after yield\n" + "World\n").equals(rst));

    }

    // test the 'isPausableWeavedPair' method
    public void testIsPausableWeavedPair() {
        // trues
        Method[] ms = reflectMethods(WeavePairExamples.class, "weavePublic");
        assertTrue(isPausableWeavedPair(ms));

        ms = reflectMethods(WeavePairExamples.class, "weaveProtected");
        assertTrue(isPausableWeavedPair(ms));

        ms = reflectMethods(WeavePairExamples.class, "weavePrivate");
        assertTrue(isPausableWeavedPair(ms));

        ms = reflectMethods(WeavePairExamples.class, "weaveDefault");
        assertTrue(isPausableWeavedPair(ms));

        // falses
        // diff param count
        ms = reflectMethods(WeavePairExamples.class, "falseDiffParams");
        Method m = selectOneByArgsCount(ms, 0);
        Method m1 = selectOneByArgsCount(ms, 2);
        assertFalse(isPausableWeavedPair(m, m1));

        // diff param type
        ms = reflectMethods(WeavePairExamples.class, "falseDiffParams1");
        m = selectOneByArgsCount(selectByHasParamType(ms, int.class), 1);
        m1 = selectOneByArgsCount(selectByHasParamType(ms, String.class), 2);
        assertFalse(isPausableWeavedPair(m, m1));

        // diff ex count
        m = selectOneByArgsCount(reflectMethods(WeavePairExamples.class, "falseDiffExs"), 0);
        m1 = selectOneByArgsCount(reflectMethods(WeavePairExamples.class, "falseDiffExs1"), 1);
        assertFalse(isPausableWeavedPair(m, m1));

        // diff ex type
        m = selectOneByArgsCount(reflectMethods(WeavePairExamples.class, "falseDiffExs2"), 0);
        m1 = selectOneByArgsCount(reflectMethods(WeavePairExamples.class, "falseDiffExs3"), 1);
        assertFalse(isPausableWeavedPair(m, m1));

        // diff modifier
        m = selectOneByArgsCount(reflectMethods(WeavePairExamples.class, "falseDiffMod"), 0);
        m1 = selectOneByArgsCount(reflectMethods(WeavePairExamples.class, "falseDiffMod1"), 1);
        assertFalse(isPausableWeavedPair(m, m1));
    }

    private Method[] selectByHasParamType(Method[] ms, Class<?> hasType) {
        List<Method> ret = new ArrayList<Method>();
        for (Method m : ms) {
            for (Class<?> t : m.getParameterTypes())
                if (hasType.equals(t))
                    ret.add(m);
        }
        if (ret.isEmpty())
            throw new RuntimeException("No method with " + hasType.getSimpleName() + " parameter found.");
        return ret.toArray(new Method[ret.size()]);
    }

    private Method selectOneByArgsCount(Method[] ms, int count) {
        for (Method m : ms) {
            if (m.getParameterTypes().length == count)
                return m;
        }
        throw new RuntimeException("No method with " + count + " parameter(s) found.");
    }

    private boolean isPausableWeavedPair(Method... ms) {
        if (ms.length != 2)
            return false;
        Method m = reflectMethods(Task.class, "isPausableWeavedPair")[0];
        m.setAccessible(true);
        try {
            return (Boolean) m.invoke(null, (Object[]) ms);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // find methods by class and method name
    private static Method[] reflectMethods(Class<?> cls, String methodName) {
        Method[] ms = cls.getDeclaredMethods();
        List<Method> ret = new ArrayList<Method>();
        for (Method m : ms)
            if (methodName.equals(m.getName()))
                ret.add(m);

        return ret.toArray(new Method[ret.size()]);
    }

}
