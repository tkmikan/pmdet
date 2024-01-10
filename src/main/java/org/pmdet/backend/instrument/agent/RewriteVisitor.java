package org.pmdet.backend.instrument.agent;

import org.objectweb.asm.*;

import java.util.Arrays;
import java.util.List;

public class RewriteVisitor extends ClassVisitor {
    private String className;

    public RewriteVisitor(ClassWriter writer, String className) {
        super(Opcodes.ASM9, writer);
        this.className = className;
    }

    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv;
        mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (className.equals("android/os/Parcel")) {
            if (name.equals("<clinit>")) {
                System.out.println("Agent: Adding loadLibrary to " + name + " " + desc);
                mv.visitLdcInsn("android_runtime_host");
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "loadLibrary", "(Ljava/lang/String;)V", false);
            }

            // check public methods
            if ((access & Opcodes.ACC_PUBLIC) == Opcodes.ACC_PUBLIC) {
                if ((name.startsWith("read") || name.startsWith("create")) && (name.endsWith("Array") || name.endsWith("List") || name.endsWith("Map") || name.endsWith("Set"))) {
//                    System.out.println("Agent: Hook method: " + name + " " + desc);
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitLdcInsn("Parcel." + name + " too large with size: ");
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/pmdet/backend/instrument/agent/ArraySizeChecker", "checkParcelTop", "(Ljava/lang/Object;Ljava/lang/String;)V", false);
                }
            }
            // parcel.setDataPosition() ?
        }
        return new MethodVisitor(api, mv) {

            private void checkArraySizeAtStackTop(String message) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(message);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/pmdet/backend/instrument/agent/ArraySizeChecker", "check", "(ILjava/lang/String;)V", false);
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                if (opcode == Opcodes.NEWARRAY) {
                    System.out.println("Hook newarray: " + operand);
                    checkArraySizeAtStackTop("newarray too large with size: ");
                }
                super.visitIntInsn(opcode, operand);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                if (opcode == Opcodes.ANEWARRAY) {
                    System.out.println("Hook anewarray: " + type);
                    checkArraySizeAtStackTop("anewarray too large with size: ");
                }
                super.visitTypeInsn(opcode, type);
            }


            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                System.out.println("Hook multianewarray: " + descriptor + " " + numDimensions);
                mv.visitLdcInsn(numDimensions);
                mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);

                for (int i = numDimensions - 1; i >= 0; --i) {
                    // ... n arr
                    mv.visitInsn(Opcodes.DUP_X1);
                    mv.visitInsn(Opcodes.SWAP);
                    // ... arr arr n
                    mv.visitLdcInsn(i);
                    mv.visitInsn(Opcodes.SWAP);
                    // ... arr arr index n
                    mv.visitInsn(Opcodes.IASTORE);
                }

                for (int j = 0; j < 2; ++j) {
                    for (int i = 0; i < numDimensions; ++i) {
                        // ... arr
                        mv.visitInsn(Opcodes.DUP);
                        mv.visitLdcInsn(i);
                        // ... arr arr index
                        mv.visitInsn(Opcodes.IALOAD);
                        // ... arr n
                        mv.visitInsn(Opcodes.SWAP);
                        // ... n arr
                    }
                }
                mv.visitInsn(Opcodes.POP);

                for (int i = 0; i < numDimensions - 1; ++i) {
                    checkArraySizeAtStackTop("multianewarray too large with partial size: ");    // prevent overflow
                    mv.visitInsn(Opcodes.IMUL);
                }
                checkArraySizeAtStackTop("multianewarray too large with total size: ");
                mv.visitInsn(Opcodes.POP);

                super.visitMultiANewArrayInsn(descriptor, numDimensions);
                System.out.println("Hook multi array: " + descriptor + " " + numDimensions + " done!");
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String mname, String descriptor, boolean isInterface) {
                if (opcode == Opcodes.INVOKESPECIAL) {
//                    System.out.println("INVOKE SPECIAL: " + owner + " " + name + " " + descriptor);

                    // these constructors call `new XXX[n]`. But adding hook in constructor may introduce great overhead.
                    // so we hook the calling site of constructor.
                    List<String> types = Arrays.asList("java/util/ArrayList", "java/util/Vector", "java/util/HashSet", "java/util/HashMap");
                    if (mname.equals("<init>") && types.contains(owner)) {
                        if (descriptor.equals("(I)V")) {
                            checkArraySizeAtStackTop("new " + owner + " too large with size: ");
                        } else if (descriptor.equals("(II)V")) {
                            mv.visitInsn(Opcodes.SWAP);
                            checkArraySizeAtStackTop("new " + owner + " too large with size: ");
                            mv.visitInsn(Opcodes.SWAP);
                        }
                    }
                }
                if (opcode == Opcodes.INVOKEVIRTUAL) {
                    if (mname.equals("ensureCapacity") && owner.equals("java/util/ArrayList")) {
                        System.out.println("Hook ensureCapacity: " + owner + " " + mname + " " + descriptor);
                        checkArraySizeAtStackTop("java/util/ArrayList.ensureCapacity too large with size: ");
                    }
                }
                if (opcode == Opcodes.INVOKESTATIC) {
                    if (mname.startsWith("log") && owner.equals("java/lang/System")) {
                        System.out.println("Hook log: " + owner + " " + mname + " " + descriptor);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/pmdet/backend/instrument/agent/MockSystemLog", "log", descriptor, false);
                        // no super
                        return;
                    }
                }
                super.visitMethodInsn(opcode, owner, mname, descriptor, isInterface);
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                if (opcode == Opcodes.IF_ICMPLT || opcode == Opcodes.IF_ICMPGE || opcode == Opcodes.IF_ICMPGT || opcode == Opcodes.IF_ICMPLE || opcode == Opcodes.IF_ICMPEQ || opcode == Opcodes.IF_ICMPNE) {
//                    System.out.println("Hook if_icmp: " + opcode);
                    mv.visitInsn(Opcodes.DUP2);
                    mv.visitLdcInsn(System.identityHashCode(label));
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "org/pmdet/backend/instrument/agent/LoopChecker", "recordCmp", "(III)V", false);
                }
                super.visitJumpInsn(opcode, label);
            }

        };
    }
}
