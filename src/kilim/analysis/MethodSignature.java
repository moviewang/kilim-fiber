package kilim.analysis;

import asm5.org.objectweb.asm.Opcodes;
import asm5.org.objectweb.asm.Type;

/**
 * @author <a href="mailto:miles.wy.1@gmail.com">pf_miles</a>
 * 
 */
public class MethodSignature {

    private int access;
    private String name;
    private String desc;
    private String signature;
    private String[] exceptions;

    /**
     * @param access
     *            the method's access flags (see {@link Opcodes}). This
     *            parameter also indicates if the method is synthetic and/or
     *            deprecated.
     * 
     * @param name
     *            the method's name.
     * @param desc
     *            the method's descriptor (see {@link Type Type}).
     * @param signature
     *            the method's signature. May be <tt>null</tt> if the method
     *            parameters, return type and exceptions do not use generic
     *            types.
     * @param exceptions
     *            the internal names of the method's exception classes (see
     *            {@link Type#getInternalName() getInternalName}). May be
     *            <tt>null</tt>.
     */
    public MethodSignature(int access, String name, String desc, String signature, String[] exceptions) {
        this.access = access;
        this.name = name;
        this.desc = desc;
        this.signature = signature;
        this.exceptions = exceptions;
    }

    /**
     * test if this method throws a specified type of exception
     */
    public boolean hasThrows(Class<? extends Throwable> exType) {
        if (this.exceptions == null || this.exceptions.length == 0)
            return false;
        String intName = Type.getInternalName(exType);
        for (String ex : this.exceptions)
            if (intName.equals(ex))
                return true;
        return false;
    }

}
