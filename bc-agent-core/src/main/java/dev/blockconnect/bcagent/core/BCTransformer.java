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

public class BCTransformer implements ClassFileTransformer {

    private final AgentConfig config;
    private final InstrumentationTracker tracker = new InstrumentationTracker();

    public BCTransformer(AgentConfig config) {
        this.config = config;
    }

    /** Tracker access for live-filter retransform operations. */
    public InstrumentationTracker getTracker() {
        return tracker;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className,
                             Class<?> classBeingRedefined,
                             ProtectionDomain protectionDomain,
                             byte[] classfileBuffer) {
        if (className == null) return null;

        if (!config.matchesClass(className.replace('/', '.'))) {
            return null;
        }

        boolean retransform = classBeingRedefined != null;
        if (retransform) {
            // Live filter extension: only classes that were OUT of scope at
            // load time may be instrumented now; re-running the advice
            // adapter over already-instrumented bytes would double-count.
            if (tracker.alreadyInstrumented(className)) return null;
            if (!tracker.tryClaim(className)) return null;
        } else {
            if (!tracker.tryClaim(className)) return null;
        }

        try {
            if (config.logClassLoad) {
                AgentLogger.getInstance().info("Class loaded: " + className.replace('/', '.'));
            }

            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

            reader.accept(new BCClassVisitor(writer, config), ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable t) {
            tracker.release(className);
            AgentLogger.getInstance().error(
                "Transform failed for " + className + ": " + t.getMessage(), t);
            return null;
        }
    }

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

            if (mv == null || (access & Opcodes.ACC_ABSTRACT) != 0
                || (access & Opcodes.ACC_NATIVE) != 0) {
                return mv;
            }

            if (config.logMethodEntry || config.logMethodExit || config.logFieldAccess) {
                return new BCAdviceAdapter(api, mv, access, name, descriptor,
                                           className, config);
            }
            return mv;
        }
    }

    static class BCAdviceAdapter extends AdviceAdapter {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final AgentConfig config;
        private int startTimeVar = -1;

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
            if (config.logMethodEntry || config.logMethodExit) {
                invokeStatic(Type.getType("Ljava/lang/System;"),
                    org.objectweb.asm.commons.Method.getMethod("long nanoTime()"));
                startTimeVar = newLocal(Type.LONG_TYPE);
                storeLocal(startTimeVar);
            }
            if (config.logMethodEntry) {
                visitLdcInsn(className);
                visitLdcInsn(methodName);
                visitLdcInsn(descriptor);
                invokeStatic(Type.getType("Ldev/blockconnect/bcagent/core/MethodRecorder;"),
                    org.objectweb.asm.commons.Method.getMethod(
                        "long onMethodEntry(String,String,String)"));
                pop2();
            }
            super.onMethodEnter();
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (config.logFieldAccess) {
                String ownerDot = owner.replace('/', '.');
                switch (opcode) {
                    case Opcodes.GETFIELD:
                        AgentLogger.getInstance().trace("GETFIELD " + ownerDot + "." + name);
                        break;
                    case Opcodes.PUTFIELD:
                        AgentLogger.getInstance().trace("PUTFIELD " + ownerDot + "." + name);
                        break;
                    case Opcodes.GETSTATIC:
                        AgentLogger.getInstance().trace("GETSTATIC " + ownerDot + "." + name);
                        break;
                    case Opcodes.PUTSTATIC:
                        AgentLogger.getInstance().trace("PUTSTATIC " + ownerDot + "." + name);
                        break;
                }
            }
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }

        @Override
        protected void onMethodExit(int opcode) {
            if (config.logMethodExit && opcode != Opcodes.ATHROW) {
                visitLdcInsn(className);
                visitLdcInsn(methodName);
                visitLdcInsn(descriptor);
                loadLocal(startTimeVar);
                invokeStatic(Type.getType("Ldev/blockconnect/bcagent/core/MethodRecorder;"),
                    org.objectweb.asm.commons.Method.getMethod(
                        "void onMethodExit(String,String,String,long)"));
            }
            if (opcode == Opcodes.ATHROW) {
                if (config.logMethodExit && startTimeVar >= 0) {
                    visitLdcInsn(className);
                    visitLdcInsn(methodName);
                    visitLdcInsn(descriptor);
                    loadLocal(startTimeVar);
                    invokeStatic(Type.getType("Ldev/blockconnect/bcagent/core/MethodRecorder;"),
                        org.objectweb.asm.commons.Method.getMethod(
                            "void onMethodException(String,String,String,long)"));
                }
            }
            super.onMethodExit(opcode);
        }
    }
}
