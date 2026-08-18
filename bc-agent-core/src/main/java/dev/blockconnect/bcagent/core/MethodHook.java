package dev.blockconnect.bcagent.core;

/**
 * Describes a method hook — a class+method+descriptor pattern and a callback
 * to invoke when the method is entered or exited.
 * <p>
 * The className uses internal JVM format (dots replaced by slashes).
 */
public class MethodHook {

    /** Target class in internal format (e.g. "net/minecraft/client/Minecraft"). */
    public final String className;

    /** Target method name (e.g. "tick"), or "*" for all methods in the class. */
    public final String methodName;

    /** Target method descriptor, or "*" for any descriptor. */
    public final String descriptor;

    /** Callback invoked at method entry. */
    public final HookCallback onEnter;

    /** Callback invoked at method exit (normal or exceptional). */
    public final HookCallback onExit;

    /** Whether to intercept the exception path. */
    public final boolean catchExceptions;

    /** Human-readable description for logging. */
    public final String description;

    public MethodHook(String className, String methodName, String descriptor,
                      HookCallback onEnter, HookCallback onExit,
                      boolean catchExceptions, String description) {
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.onEnter = onEnter;
        this.onExit = onExit;
        this.catchExceptions = catchExceptions;
        this.description = description;
    }

    /** Check if this hook matches the given method. */
    public boolean matches(String cls, String name, String desc) {
        if (!className.equals(cls)) return false;
        if (!"*".equals(methodName) && !methodName.equals(name)) return false;
        if (!"*".equals(descriptor) && !descriptor.equals(desc)) return false;
        return true;
    }

    @Override
    public String toString() {
        return className + '#' + methodName + descriptor + " [" + description + "]";
    }

    /** Functional interface for hook callbacks. */
    @FunctionalInterface
    public interface HookCallback {
        /**
         * @param className  internal class name
         * @param methodName method name
         * @param descriptor method descriptor
         * @param args       method arguments (may be null for entry hooks without arg capture)
         * @param result     return value (null for entry hooks or void methods)
         * @param exception   thrown exception (null for normal exits)
         */
        void invoke(String className, String methodName, String descriptor,
                    Object[] args, Object result, Throwable exception);
    }
}
