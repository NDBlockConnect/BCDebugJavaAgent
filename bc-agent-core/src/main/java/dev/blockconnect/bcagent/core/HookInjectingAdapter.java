package dev.blockconnect.bcagent.core;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.List;

/**
 * ASM visitor that injects hook dispatch calls into methods that have
 * registered hooks in {@link HookRegistry}.
 * <p>
 * Unlike {@link BCTransformer.BCAdviceAdapter} which always logs, this adapter
 * only activates for methods with matching hooks — minimal overhead when no
 * hooks are registered.
 */
public class HookInjectingAdapter extends AdviceAdapter {

    private final String className;
    private final String methodName;
    private final String descriptor;
    private final List<MethodHook> hooks;

    private int startTimeVar;

    protected HookInjectingAdapter(int api, MethodVisitor mv, int access,
                                   String className, String methodName,
                                   String descriptor, List<MethodHook> hooks) {
        super(api, mv, access, methodName, descriptor);
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.hooks = hooks;
    }

    @Override
    protected void onMethodEnter() {
        // Record start time for duration tracking
        invokeStatic(Type.getType("Ljava/lang/System;"),
            org.objectweb.asm.commons.Method.getMethod("long nanoTime()"));
        startTimeVar = newLocal(Type.LONG_TYPE);
        storeLocal(startTimeVar);

        // Dispatch to HookDispatcher.onEnter for each matching hook
        visitLdcInsn(className);
        visitLdcInsn(methodName);
        visitLdcInsn(descriptor);
        invokeStatic(Type.getType("Ldev/blockconnect/bcagent/core/HookDispatcher;"),
            org.objectweb.asm.commons.Method.getMethod(
                "void dispatchEntry(String,String,String)"));

        super.onMethodEnter();
    }

    @Override
    protected void onMethodExit(int opcode) {
        if (opcode == Opcodes.ATHROW) {
            visitLdcInsn(className);
            visitLdcInsn(methodName);
            visitLdcInsn(descriptor);
            loadLocal(startTimeVar);
            invokeStatic(Type.getType("Ldev/blockconnect/bcagent/core/HookDispatcher;"),
                org.objectweb.asm.commons.Method.getMethod(
                    "void dispatchExit(String,String,String,long)"));
        } else {
            visitLdcInsn(className);
            visitLdcInsn(methodName);
            visitLdcInsn(descriptor);
            loadLocal(startTimeVar);
            invokeStatic(Type.getType("Ldev/blockconnect/bcagent/core/HookDispatcher;"),
                org.objectweb.asm.commons.Method.getMethod(
                    "void dispatchExit(String,String,String,long)"));
        }
        super.onMethodExit(opcode);
    }
}
