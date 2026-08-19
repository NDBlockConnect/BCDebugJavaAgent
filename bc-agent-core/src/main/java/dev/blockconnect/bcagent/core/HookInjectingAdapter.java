package dev.blockconnect.bcagent.core;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.util.List;

public class HookInjectingAdapter extends AdviceAdapter {

    private final String className;
    private final String methodName;
    private final String descriptor;
    private final List<MethodHook> hooks;
    private final Type[] argTypes;
    private final boolean isStatic;

    private int startTimeVar;
    private int argsArrayVar = -1;

    protected HookInjectingAdapter(int api, MethodVisitor mv, int access,
                                   String className, String methodName,
                                   String descriptor, List<MethodHook> hooks) {
        super(api, mv, access, methodName, descriptor);
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.hooks = hooks;
        this.argTypes = Type.getArgumentTypes(descriptor);
        this.isStatic = (access & Opcodes.ACC_STATIC) != 0;
    }

    @Override
    protected void onMethodEnter() {
        invokeStatic(Type.getType("Ljava/lang/System;"),
            org.objectweb.asm.commons.Method.getMethod("long nanoTime()"));
        startTimeVar = newLocal(Type.LONG_TYPE);
        storeLocal(startTimeVar);

        boolean needsArgs = hooks.stream().anyMatch(h -> h.onEnter != null);
        if (needsArgs && argTypes.length > 0) {
            push(argTypes.length);
            visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
            for (int i = 0; i < argTypes.length; i++) {
                visitInsn(Opcodes.DUP);
                push(i);
                loadArg(i);
                box(argTypes[i]);
                visitInsn(Opcodes.AASTORE);
            }
            argsArrayVar = newLocal(Type.getType("[Ljava/lang/Object;"));
            storeLocal(argsArrayVar);
        }

        visitLdcInsn(className);
        visitLdcInsn(methodName);
        visitLdcInsn(descriptor);
        if (needsArgs && argsArrayVar >= 0) {
            loadLocal(argsArrayVar);
        } else {
            visitInsn(Opcodes.ACONST_NULL);
        }
        invokeStatic(Type.getType("Ldev/blockconnect/bcagent/core/HookDispatcher;"),
            org.objectweb.asm.commons.Method.getMethod(
                "void dispatchEntry(String,String,String,Object[])"));

        super.onMethodEnter();
    }

    @Override
    protected void onMethodExit(int opcode) {
        if (opcode == Opcodes.ATHROW) {
            int excVar = newLocal(Type.getType("Ljava/lang/Throwable;"));
            visitInsn(Opcodes.DUP);
            storeLocal(excVar);

            visitLdcInsn(className);
            visitLdcInsn(methodName);
            visitLdcInsn(descriptor);
            loadLocal(startTimeVar);
            visitInsn(Opcodes.ACONST_NULL);
            loadLocal(excVar);
            invokeStatic(Type.getType("Ldev/blockconnect/bcagent/core/HookDispatcher;"),
                org.objectweb.asm.commons.Method.getMethod(
                    "void dispatchExit(String,String,String,long,Object,Throwable)"));
        } else {
            Type returnType = Type.getReturnType(descriptor);
            if (returnType == Type.VOID_TYPE) {
                visitLdcInsn(className);
                visitLdcInsn(methodName);
                visitLdcInsn(descriptor);
                loadLocal(startTimeVar);
                visitInsn(Opcodes.ACONST_NULL);
                visitInsn(Opcodes.ACONST_NULL);
                invokeStatic(Type.getType("Ldev/blockconnect/bcagent/core/HookDispatcher;"),
                    org.objectweb.asm.commons.Method.getMethod(
                        "void dispatchExit(String,String,String,long,Object,Throwable)"));
            } else {
                int resultVar = newLocal(Type.getType("Ljava/lang/Object;"));
                if (returnType.getSize() == 1) {
                    visitInsn(Opcodes.DUP);
                } else {
                    visitInsn(Opcodes.DUP2);
                }
                box(returnType);
                storeLocal(resultVar);

                visitLdcInsn(className);
                visitLdcInsn(methodName);
                visitLdcInsn(descriptor);
                loadLocal(startTimeVar);
                loadLocal(resultVar);
                visitInsn(Opcodes.ACONST_NULL);
                invokeStatic(Type.getType("Ldev/blockconnect/bcagent/core/HookDispatcher;"),
                    org.objectweb.asm.commons.Method.getMethod(
                        "void dispatchExit(String,String,String,long,Object,Throwable)"));
            }
        }
        super.onMethodExit(opcode);
    }
}
