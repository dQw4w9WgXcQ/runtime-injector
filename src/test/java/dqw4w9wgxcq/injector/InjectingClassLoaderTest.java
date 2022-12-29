package dqw4w9wgxcq.injector;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;

import java.io.File;
import java.net.URL;

public class InjectingClassLoaderTest {
    @Test
    void test() throws Exception {
        var file = new File("myjar.jar");
        var url = file.toURI().toURL();

        Class<?> clazz;
        try (var cl = new InjectingClassLoader(new URL[]{url}, null) {
            @Override
            protected boolean shouldInject(String className) {
                return className.equals("org.example.MyClass");
            }

            @Override
            protected byte[] inject(String className, byte[] bytes) {
                var cr = new ClassReader(bytes);

                var cn = new ClassNode();
                cr.accept(cn, 0);

                var method = cn.methods.stream()
                        .filter(m -> m.name.equals("returnsTrue"))
                        .findFirst()
                        .orElseThrow();

                InsnNode trueInsn = null;
                for (var instruction : method.instructions) {
                    if (instruction.getOpcode() == Opcodes.ICONST_1) {
                        trueInsn = (InsnNode) instruction;
                        break;
                    }
                }

                if (trueInsn == null) throw new RuntimeException("Could not find ICONST_1");

                method.instructions.insertBefore(trueInsn, new InsnNode(Opcodes.ICONST_0));
                method.instructions.remove(trueInsn);

                var cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                cn.accept(cw);

                return cw.toByteArray();
            }
        }) {
            clazz = cl.loadClass("org.example.MyClass");
        }

        var method = clazz.getDeclaredMethod("returnsTrue");
        method.setAccessible(true);

        var instance = clazz.getDeclaredConstructor().newInstance();
        var res = (boolean) method.invoke(instance);

        Assertions.assertFalse(res, "should return false after injection");
    }
}
