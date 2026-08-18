package dev.blockconnect.bcagent.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.List;

/**
 * ClassFileTransformer that injects hook dispatch calls only into classes
 * that have registered hooks in {@link HookRegistry}.
 * <p>
 * This is separate from {@link BCTransformer} (which does general logging) so
 * that the two concerns are decoupled and can be toggled independently.
 */
public class HookTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className,
                             Class<?> classBeingRedefined,
                             ProtectionDomain protectionDomain,
                             byte[] classfileBuffer) {
        if (className == null) return null;

        HookRegistry registry = HookRegistry.getInstance();
        if (!registry.isActive() || !registry.hasHooks(className)) {
            return null;
        }

        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

            reader.accept(new HookClassVisitor(writer, className), ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable t) {
            AgentLogger.getInstance().error(
                "Hook transform failed for " + className + ": " + t.getMessage(), t);
            return null;
        }
    }

    static class HookClassVisitor extends org.objectweb.asm.ClassVisitor {
        private final String className;

        HookClassVisitor(ClassVisitor cv, String className) {
            super(Opcodes.ASM9, cv);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                          String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

            if (mv == null || (access & Opcodes.ACC_ABSTRACT) != 0
                || (access & Opcodes.ACC_NATIVE) != 0) {
                return mv;
            }

            // Check if any hooks match this method
            List<MethodHook> hooks = HookRegistry.getInstance().getHooks(className);
            if (hooks == null) return mv;

            boolean hasMatch = false;
            for (MethodHook hook : hooks) {
                if (hook.matches(className, name, descriptor)) { hasMatch = true; break; }
            }

            if (hasMatch) {
                return new HookInjectingAdapter(Opcodes.ASM9, mv, access,
                    className, name, descriptor, hooks);
            }
            return mv;
        }
    }
}
