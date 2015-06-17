package kilim.mirrors;

/**
 * Mirrors provides a uniform facade for class and method related information
 * (via ClassMirror and MethodMirror). This information is obtained either
 * through loaded Class objects or parsed bytecode.
 */
public interface Mirrors {
    /**
     * find mirrored class
     */
    abstract public ClassMirror classForName(String className) throws ClassMirrorNotFoundException;

    /**
     * mirror a class in a class loading manner
     */
    public abstract ClassMirror mirror(Class<?> clazz);

    /**
     * mirror a class in a bytecode analysis manner
     */
    public abstract ClassMirror mirror(String className, byte[] bytecode);

}
