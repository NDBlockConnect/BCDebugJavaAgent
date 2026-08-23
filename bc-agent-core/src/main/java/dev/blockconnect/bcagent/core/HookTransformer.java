package dev.blockconnect.bcagent.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

/**
 * ClassFileTransformer that injects hook dispatch calls into classes holding
 * registered hooks.
 * <p>
 * Retransform-safe: an injection tracker guarantees each method is instrument
 * exactly once across initial load and later {@code retransformClasses}
 * passes, so hot-reloading hooks never stacks duplicate dispatch calls.
 */
public class HookTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className,
                             Class<?> classBeingRedefined,
                             ProtectionDomain protectionDomain,
                             byte[] classfileBuffer) {
        if (className == null) return null;

        // Never transform the agent's own classes, and skip everything while
        // the registry itself is still being defined (P0 fix from Alpha.3 —
        // resolving HookRegistry mid-definition re-entered its load and blew
        // up with a duplicate-definition LinkageError).
        if (className.startsWith("dev/blockconnect/bcagent")) return null;

        HookRegistry registry;
        List<MethodHook> hooks;
        try {
            registry = HookRegistry.getInstance();
            if (!registry.isActive()) return null;
            hooks = registry.getHooks(className);
        } catch (Throwable t) {
            // Registry not resolvable yet — skip rather than break class loading.
            return null;
        }
        if (hooks == null || hooks.isEmpty()) return null;

        List<String[]> pendingClaims = new ArrayList<>(hooks.size());
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

            reader.accept(new HookClassVisitor(writer, className, hooks,
                registry.getInjectionTracker(), pendingClaims), ClassReader.EXPAND_FRAMES);

            if (pendingClaims.isEmpty()) {
                // Nothing new to inject (pure retransform of known methods).
                return null;
            }
            return writer.toByteArray();
        } catch (Throwable t) {
            for (String[] claim : pendingClaims) {
                registry.getInjectionTracker().release(claim[0], claim[1], claim[2]);
            }
            AgentLogger.getInstance().error(
                "Hook transform failed for " + className + ": " + t.getMessage(), t);
            return null;
        }
    }

    static class HookClassVisitor extends org.objectweb.asm.ClassVisitor {
        private final String className;
        private final List<MethodHook> hooks;
        private final HookInjectionTracker tracker;
        private final List<String[]> pendingClaims;

        HookClassVisitor(ClassVisitor cv, String className, List<MethodHook> hooks,
                          HookInjectionTracker tracker, List<String[]> pendingClaims) {
            super(Opcodes.ASM9, cv);
            this.className = className;
            this.hooks = hooks;
            this.tracker = tracker;
            this.pendingClaims = pendingClaims;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                          String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

            if (mv == null || (access & Opcodes.ACC_ABSTRACT) != 0
                || (access & Opcodes.ACC_NATIVE) != 0) {
                return mv;
            }

            boolean anyNew = false;
            for (MethodHook hook : hooks) {
                if (!hook.matches(className, name, descriptor)) continue;
                if (tracker.alreadyInjected(className, name, descriptor)) continue;
                if (tracker.tryClaim(className, name, descriptor)) {
                    pendingClaims.add(new String[]{className, name, descriptor});
                    anyNew = true;
                }
            }
            if (!anyNew) return mv;

            return new HookInjectingAdapter(Opcodes.ASM9, mv, access,
                className, name, descriptor, hooks);
        }
    }
}
