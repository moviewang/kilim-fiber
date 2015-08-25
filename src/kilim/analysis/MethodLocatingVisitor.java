package kilim.analysis;

import asm5.org.objectweb.asm.ClassVisitor;
import asm5.org.objectweb.asm.Label;
import asm5.org.objectweb.asm.MethodVisitor;
import asm5.org.objectweb.asm.Opcodes;

/**
 * To find which method is in according to the specified line number.
 * 
 * @author <a href="mailto:miles.wy.1@gmail.com">pf_miles</a>
 * 
 */
public class MethodLocatingVisitor extends ClassVisitor {

    private int lineNum;
    private MethodSignature locatedMethod;

    /**
     * @param api
     *            Must be one of {@link Opcodes#ASM4} or {@link Opcodes#ASM5}.
     * @param lineNum
     *            the locating line number
     */
    public MethodLocatingVisitor(int api, int lineNum) {
        super(api);
        this.lineNum = lineNum;
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM5) {

            @Override
            public void visitLineNumber(int line, Label start) {
                if (line == lineNum) {// located
                    MethodLocatingVisitor.this.locatedMethod = new MethodSignature(access, name, desc, signature, exceptions);
                }
            }
        };
    }

    /**
     * @return the located method signature, or null if locating failed.
     */
    public MethodSignature getLocatedMethod() {
        return this.locatedMethod;
    }

}
