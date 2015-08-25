/* Copyright (c) 2006, Sriram Srinivasan
 *
 * You may distribute this software under the terms of the license 
 * specified in the file "License"
 */

package kilim;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import kilim.analysis.MethodLocatingVisitor;
import kilim.analysis.MethodSignature;
import kilim.analysis.Utils;
import asm5.org.objectweb.asm.ClassReader;
import asm5.org.objectweb.asm.Opcodes;

/**
 * A base class for tasks. A task is a lightweight thread (it contains its own
 * stack in the form of a fiber). A concrete subclass of Task must provide a
 * pausable execute method.
 * 
 */
public abstract class Task implements Runnable {

    private static final Pattern normNotCountingPrefixes = Pattern.compile("^com\\.sun\\.|^sun\\.|^java\\.|^javax\\.");
    private static final ConcurrentHashMap<StackTraceElement, Boolean> isPausableCache = new ConcurrentHashMap<StackTraceElement, Boolean>(16, 0.75f,
            Utils.PROC_NUM * 10);

    static PauseReason yieldReason = new YieldReason();
    /**
     * Task id, automatically generated
     */
    public final int id;
    static final AtomicInteger idSource = new AtomicInteger();

    /**
     * The stack manager in charge of rewinding and unwinding the stack when
     * Task.pause() is called.
     */
    protected Fiber fiber;

    /**
     * The reason for pausing (duh) and performs the role of a await condition
     * in CCS. This object is responsible for resuming the task.
     * 
     * @see kilim.PauseReason
     */
    protected PauseReason pauseReason;

    /**
     * running = true when it is put on the schdulers run Q (by Task.resume()).
     * The Task.runExecute() method is called at some point; 'running' remains
     * true until the end of runExecute (where it is reset), at which point a
     * fresh decision is made whether the task needs to continue running.
     */
    protected boolean running = false;
    protected boolean done = false;

    public volatile Object exitResult = "OK";

    public Task() {
        id = idSource.incrementAndGet();
        fiber = new Fiber(this);
    }

    public int id() {
        return id;
    }

    /**
     * The generated code calls Fiber.upEx, which in turn calls this to find out
     * out where the current method is w.r.t the closest _runExecute method.
     * 
     * @return the number of stack frames above _runExecute(), not including
     *         this method
     */
    public int getStackDepth() {
        int depth = 0;
        for (StackTraceElement ste : new Exception().getStackTrace()) {
            if (ste.getMethodName().equals("_runExecute")) {
                // discounting WorkerThread.run, Task._runExecute, and
                // Scheduler.getStackDepth
                return depth - 1;
            }
            if (notCounting(ste))
                continue;
            depth++;
        }
        throw new AssertionError("Expected task to be run by Task._runExecute");
    }

    // to tell if specified stack element should not be count
    // those neither declared in a kilim class nor 'Pausable' method
    // should not be count
    private boolean notCounting(StackTraceElement ste) {
        return isNormalNotCountingCls(ste) || !isDeclaredInKilimCls(ste) && !isPausableMethod(ste);
    }

    // test if the method of specified stackframe is pausable
    private boolean isPausableMethod(StackTraceElement ste) {
        Boolean ret = isPausableCache.get(ste);
        if (ret == null) {
            // check if method pausable Reflectively
            Method[] ms = reflectMethods(ste);
            if (ms == null || ms.length == 0) {
                ret = false;
            } else if (ms.length == 1) {
                ret = hasPausableException(ms[0].getExceptionTypes());
            } else {
                // more than one method found, must determine it by bytecode
                // analyzing
                if (ste.getLineNumber() > 0) {
                    ret = isInPausableMethodByAsm(ste, ms);
                } else {
                    // XXX don't know the exact method invoking, have to choose
                    // one currently, not rigorous and have potential bugs
                    // though.(this method should warning about potential bug
                    // cases)
                    ret = oneHasPausableException(ms);
                }
            }
            isPausableCache.put(ste, ret);
        }
        return ret;
    }

    // test if specified stack trace element located in a pausable method, by
    // line number, using asm bytecode analysis
    private boolean isInPausableMethodByAsm(StackTraceElement ste, Method[] ms) {
        ClassLoader ld = Thread.currentThread().getContextClassLoader();
        boolean threadLoader = true;
        if (ld == null) {
            ld = Task.class.getClassLoader();
            threadLoader = false;
        }
        String clsFileName = ste.getClassName().replaceAll("\\.", "/") + ".class";
        InputStream is = ld.getResourceAsStream(clsFileName);
        if (is == null && threadLoader) {
            ld = Task.class.getClassLoader();
            is = ld.getResourceAsStream(clsFileName);
        }
        if (is == null) {
            // could not load class file bytes, fallback to reflective solution
            return oneHasPausableException(ms);
        } else {
            MethodLocatingVisitor locating = new MethodLocatingVisitor(Opcodes.ASM5, ste.getLineNumber());
            try {
                ClassReader cr = new ClassReader(is);
                cr.accept(locating, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            } catch (Exception e) {
                return oneHasPausableException(ms);
            }
            MethodSignature sig = locating.getLocatedMethod();
            if (sig == null) {
                return oneHasPausableException(ms);
            } else {
                return sig.hasThrows(Pausable.class);
            }
        }
    }

    private boolean oneHasPausableException(Method[] ms) {
        boolean ret = false;
        boolean oneHasNoPausableException = false;
        for (Method m : ms) {
            if (hasPausableException(m.getExceptionTypes())) {
                ret = true;
            } else {
                oneHasNoPausableException = true;
            }
        }
        // potential bug case, warn the user about this
        if (ret && oneHasNoPausableException) {
            System.err
                    .println("Methods of the same name: '"
                            + ms[0].getName()
                            + "'"
                            + " have different pausable signature, this could be potentially buggy. It is suggested that, in a same class, methods with 'Pausable' declaration and those without it should have different names. See: https://github.com/pfmiles/kilim-fiber/issues/6");
        }
        return ret;
    }

    // check if the specified method has 'Pausable' exception
    private boolean hasPausableException(Class<?>[] exTypes) {
        for (Class<?> ex : exTypes) {
            if (Pausable.class.equals(ex))
                return true;
        }
        return false;
    }

    // find methods by stack trace element
    private Method[] reflectMethods(StackTraceElement ste) {
        String clsName = ste.getClassName();
        Class<?> cls = null;
        ClassLoader ld = Thread.currentThread().getContextClassLoader();
        if (ld == null)
            ld = Task.class.getClassLoader();
        try {
            cls = ld.loadClass(clsName);
        } catch (ClassNotFoundException e) {
            ld = Task.class.getClassLoader();
        }
        try {
            cls = ld.loadClass(clsName);
        } catch (ClassNotFoundException e) {
            System.err.println("Could not load class when analyzing invocation stack: " + e);
            return null;
        }
        Method[] ms = cls.getDeclaredMethods();
        List<Method> ret = new ArrayList<Method>();
        for (Method m : ms)
            if (ste.getMethodName().equals(m.getName()))
                ret.add(m);

        return ret.toArray(new Method[ret.size()]);
    }

    // test if kilim class
    private boolean isDeclaredInKilimCls(StackTraceElement ste) {
        return ste.getClassName().startsWith("kilim.");
    }

    private boolean isNormalNotCountingCls(StackTraceElement ste) {
        String clsName = ste.getClassName();
        // including native methods
        if (clsName == null || ste.isNativeMethod())
            return true;
        // including cglib generated classes
        if ("<generated>".equals(ste.getFileName()))
            return true;
        // including jdk classes
        return normNotCountingPrefixes.matcher(clsName).find();
    }

    /**
     * This is a placeholder that doesn't do anything useful. Weave replaces the
     * call in the bytecode from invokestateic Task.getCurrentTask to load fiber
     * getfield task
     */
    public static Task getCurrentTask() throws Pausable {
        return null;
    }

    /**
     * Analogous to System.exit, except an Object can be used as the exit value
     */

    public static void exit(Object aExitValue) throws Pausable {
    }

    public static void exit(Object aExitValue, Fiber f) {
        assert f.pc == 0 : "f.pc != 0";
        f.task.setPauseReason(new TaskDoneReason(aExitValue));
        f.togglePause();
    }

    /**
     * Exit the task with a throwable indicating an error condition. The value
     * is conveyed through the exit mailslot (see informOnExit). All exceptions
     * trapped by the task scheduler also set the error result.
     */
    public static void errorExit(Throwable ex) throws Pausable {
    }

    public static void errorExit(Throwable ex, Fiber f) {
        assert f.pc == 0 : "fc.pc != 0";
        f.task.setPauseReason(new TaskDoneReason(ex));
        f.togglePause();
    }

    public static void errNotWoven() {
        System.err.println("############################################################");
        System.err.println("Task has either not been woven or the classpath is incorrect");
        System.err.println("############################################################");
        Thread.dumpStack();
        System.exit(0);
    }

    public static void errNotWoven(Task t) {
        System.err.println("############################################################");
        System.err.println("Task " + t.getClass() + " has either not been woven or the classpath is incorrect");
        System.err.println("############################################################");
        Thread.dumpStack();
        System.exit(0);
    }

    static class ArgState extends kilim.State {
        Object mthd;
        Object obj;
        Object[] fargs;
    }

    /**
     * Invoke a pausable method via reflection. Equivalent to Method.invoke().
     * 
     * @param mthd
     *            : The method to be invoked. (Implementation note: the
     *            corresponding woven method is invoked instead).
     * @param target
     *            : The object on which the method is invoked. Can be null if
     *            the method is static.
     * @param args
     *            : Arguments to the method
     * @return
     * @throws Pausable
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     */
    public static Object invoke(Method mthd, Object target, Object... args) throws Pausable, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {
        Fiber f = getCurrentTask().fiber;
        Object[] fargs;
        if (f.pc == 0) {
            mthd = getWovenMethod(mthd);
            // Normal invocation.
            if (args == null) {
                fargs = new Object[1];
            } else {
                fargs = new Object[args.length + 1]; // for fiber
                System.arraycopy(args, 0, fargs, 0, args.length);
            }
            fargs[fargs.length - 1] = f;
        } else {
            // Resuming from a previous yield
            ArgState as = (ArgState) f.getState();
            mthd = (Method) as.mthd;
            target = as.obj;
            fargs = as.fargs;
        }
        f.down();
        Object ret = mthd.invoke(target, fargs);
        switch (f.up()) {
        case Fiber.NOT_PAUSING__NO_STATE:
        case Fiber.NOT_PAUSING__HAS_STATE:
            return ret;
        case Fiber.PAUSING__NO_STATE:
            ArgState as = new ArgState();
            as.obj = target;
            as.fargs = fargs;
            as.pc = 1;
            as.mthd = mthd;
            f.setState(as);
            return null;
        case Fiber.PAUSING__HAS_STATE:
            return null;
        }
        throw new IllegalAccessException("Internal Error");
    }

    // Given a method corresp. to "f(int)", return the equivalent woven method
    // for "f(int, kilim.Fiber)"
    private static Method getWovenMethod(Method m) {
        Class<?>[] ptypes = m.getParameterTypes();
        if (!(ptypes.length > 0 && ptypes[ptypes.length - 1].getName().equals("kilim.Fiber"))) {
            // The last param is not "Fiber", so m is not woven.
            // Get the woven method corresponding to m(..., Fiber)
            boolean found = false;
            LOOP: for (Method wm : m.getDeclaringClass().getDeclaredMethods()) {
                if (wm != m && wm.getName().equals(m.getName())) {
                    // names match. Check if the wm has the exact parameter
                    // types as m, plus a fiber.
                    Class<?>[] wptypes = wm.getParameterTypes();
                    if (wptypes.length != ptypes.length + 1 || !(wptypes[wptypes.length - 1].getName().equals("kilim.Fiber")))
                        continue LOOP;
                    for (int i = 0; i < ptypes.length; i++) {
                        if (ptypes[i] != wptypes[i])
                            continue LOOP;
                    }
                    m = wm;
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Found no pausable method corresponding to supplied method: " + m);
            }
        }
        return m;
    }

    /**
     * Yield cooperatively to the next task waiting to use the thread.
     */
    public static void yield() throws Pausable {
        errNotWoven();
    }

    public static void yield(Fiber f) {
        if (f.pc == 0) {
            f.task.setPauseReason(yieldReason);
        } else {
            f.task.setPauseReason(null);
        }
        f.togglePause();
        f.task.checkKill();
    }

    /**
     * Ask the current task to pause with a reason object, that is responsible
     * for resuming the task when the reason (for pausing) is not valid any
     * more.
     * 
     * @param pauseReason
     *            the reason
     */
    public static void pause(PauseReason pauseReason) throws Pausable {
        errNotWoven();
    }

    public static void pause(PauseReason pauseReason, Fiber f) {
        if (f.pc == 0) {
            f.task.setPauseReason(pauseReason);
        } else {
            f.task.setPauseReason(null);
        }
        f.togglePause();
        f.task.checkKill();
    }

    /*
     * This is the fiber counterpart to the execute() method that allows us to
     * detec when a subclass has not been woven.
     * 
     * If the subclass has not been woven, it won't have an execute method of
     * the following form, and this method will be called instead.
     */
    public abstract void execute() throws Pausable, Exception;

    public void execute(Fiber f) throws Exception {
        errNotWoven(this);
    }

    public String toString() {
        return "" + id + "(running=" + running + ",pr=" + pauseReason + ")";
    }

    public String dump() {
        synchronized (this) {
            return "" + id + "(running=" + running + ", pr=" + pauseReason + ")";
        }
    }

    final protected void setPauseReason(PauseReason pr) {
        pauseReason = pr;
    }

    public final PauseReason getPauseReason() {
        return pauseReason;
    }

    public synchronized boolean isDone() {
        return done;
    }

    /**
     * Run the fiber until pause/yield is called inside
     */
    public synchronized void _runExecute() throws NotPausable {
        Fiber f = fiber;
        boolean isDone = false;
        try {
            // start execute. fiber is wound to the beginning.
            execute(f.begin());

            // execute() done. Check fiber if it is pausing and reset it.
            isDone = f.end() || (pauseReason instanceof TaskDoneReason);
        } catch (Throwable th) {
            // Definitely done
            setPauseReason(new TaskDoneReason(th));
            isDone = true;
        }

        if (isDone) {
            // inform on exit
            if (pauseReason instanceof TaskDoneReason) {
                exitResult = ((TaskDoneReason) pauseReason).exitObj;
                if (exitResult instanceof Throwable) {
                    throw new RuntimeException("task is done with exception", (Throwable) exitResult);
                }
            }
            done = true;
        } else {
            running = false;
        }
    }

    public void run() {
        _runExecute();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public void checkKill() {
    }
}
