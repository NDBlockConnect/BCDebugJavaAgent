package dev.blockconnect.bcagent.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Core ClassFileTransformer — instruments matching classes with method
 * entry/exit callbacks using the AdviceAdapter pattern.
 * <p>
 * Injected bytecode calls {@link MethodRecorder#onMethodEntry} at method start
 * and {@link MethodRecorder#onMethodExit} before every return instruction.
 */
public class BCTransformer implements ClassFileTransformer {

    private final AgentConfig config;

    public BCTransformer(AgentConfig config) {
        this.config = config;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className,
                             Class<?> classBeingRedefined,
                             ProtectionDomain protectionDomain,
                             byte[] classfileBuffer) {
        if (className == null) return null;

        // Fast path: skip non-matching classes
        if (!config.matchesClass(className.replace('/', '.'))) {
            return null;
        }

        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

            reader.accept(new BCClassVisitor(writer, config), ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable t) {
            // Never let transformer errors crash the JVM
            AgentLogger.getInstance().error(
                "Transform failed for " + className + ": " + t.getMessage(), t);
            return null;
        }
    }

    // ── Class visitor ───────────────────────────────────────

    static class BCClassVisitor extends org.objectweb.asm.ClassVisitor {
        private final AgentConfig config;
        private String className;

        BCClassVisitor(ClassVisitor cv, AgentConfig config) {
            super(Opcodes.ASM9, cv);
            this.config = config;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.className = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                          String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

            // Skip abstract/native methods — no body to instrument
            if (mv == null || (access & Opcodes.ACC_ABSTRACT) != 0
                || (access & Opcodes.ACC_NATIVE) != 0) {
                return mv;
            }

            if (config.logMethodEntry || config.logMethodExit) {
                return new BCAdviceAdapter(api, mv, access, name, descriptor,
                                           className, config);
            }
            return mv;
        }
    }

    // ── Advice adapter ──────────────────────────────────────

    static class BCAdviceAdapter extends AdviceAdapter {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final AgentConfig config;
        private int startTimeVar;

        protected BCAdviceAdapter(int api, MethodVisitor mv, int access,
                                   String name, String desc, String className,
                                   AgentConfig config) {
            super(api, mv, access, name, desc);
            this.className = className;
            this.methodName = name;
            this.descriptor = desc;
            this.config = config;
        }

        @Override
        protected void onMethodEnter() {
            if (config.logMethodEntry) {
                // Call MethodRecorder.onMethodEntry(className, methodName, descriptor)
                // and store the returned nano-time in a local variable
                visitLdcInsn(className);
                visitLdcInsn(methodName);
                visitLdcInsn(descriptor);
                invokeStatic(Type.getType("Ldev/blockconnect/bcagent/core/MethodRecorder;"),
                    org.objectweb.asm.commons.Method.getMethod(
                        "long onMethodEntry(String,String,String)"));
                startTimeVar = newLocal(Type.LONG_TYPE);
                storeLocal(startTimeVar);
            }
            super.onMethodEnter();
        }

        @Override
        protected void onMethodExit(int opcode) {
            if (config.logMethodExit && opcode != Opcodes.ATHROW) {
                // Call MethodRecorder.onMethodExit(className, methodName, descriptor, startTime)
                visitLdcInsn(className);
                visitLdcInsn(methodName);
                visitLdcInsn(descriptor);
                loadLocal(startTimeVar);
                invokeStatic(Type.getType("Ldev/blockconnect/bcagent/core/MethodRecorder;"),
                    org.objectweb.asm.commons.Method.getMethod(
                        "void onMethodExit(String,String,String,long)"));
            }
            if (opcode == Opcodes.ATHROW) {
                // Exception path — record the exception exit
                visitLdcInsn(className);
                visitLdcInsn(methodName);
                visitLdcInsn(descriptor);
                loadLocal(startTimeVar);
                invokeStatic(Type.getType("Ldev/blockconnect/bcagent/core/MethodRecorder;"),
                    org.objectweb.asm.commons.Method.getMethod(
                        "void onMethodException(String,String,String,long)"));
            }
            super.onMethodExit(opcode);
        }
    }
}
