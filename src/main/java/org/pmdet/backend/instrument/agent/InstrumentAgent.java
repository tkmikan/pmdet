package org.pmdet.backend.instrument.agent;

import org.objectweb.asm.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class InstrumentAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("Java Agent loaded!");
        String frameworkDir = System.getProperty("framework.dir");
        if (frameworkDir == null) {
            throw new RuntimeException("framework.dir not set");
        }
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className.startsWith("java/") || className.startsWith("jdk/") || className.startsWith("sun/") || className.startsWith("com/sun/") || className.startsWith("com/code_intelligence/")) {
                    return null;
                }

                System.out.println("Agent: Loading class: " + className);
                String classSourcePath = protectionDomain.getCodeSource().getLocation().getPath();
                System.out.println("Agent: Location: " + classSourcePath);
                if (!classSourcePath.startsWith(frameworkDir) && !className.startsWith("org/pmdet/backend/parcelable/")) {
                    return null;
                }

                ClassReader reader = new ClassReader(classfileBuffer);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

                try {
                    ClassVisitor visitor = new RewriteVisitor(writer, className);
                    reader.accept(visitor, 0);
                } catch (Throwable e) {
                    System.out.println("transform exception!");
                    e.printStackTrace();
                }
                System.out.println("Agent: Transform Finish for class: " + className);
                return writer.toByteArray();
            }
        });
    }
}
