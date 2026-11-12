import java.io.*;
import java.util.Locale;
import java.util.jar.*;
import jdk.internal.org.objectweb.asm.*;

// Java 8's bundled ASM keeps this workaround independent of external libraries.
public final class MacJViewerCompat implements Opcodes {
    private static byte[] patch(byte[] original) {
        ClassReader reader = new ClassReader(original);
        final int[] fields = {0};
        final int[] methods = {0};
        reader.accept(new ClassVisitor(ASM5) {
            public FieldVisitor visitField(int access, String name, String desc,
                                           String signature, Object value) {
                if ((access & ACC_STATIC) == 0 &&
                    ((name.equals("status") && desc.equals("B")) ||
                     (name.equals("m_softKeyboard") && desc.equals(
                        "Lcom/ami/kvm/jviewer/softkeyboard/SoftKeyboard;")))) fields[0]++;
                return null;
            }
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                if (!(name.equals("onKeybdLED") && desc.equals("(B)V")) &&
                    !(name.equals("syncLED") && desc.equals("()V"))) return null;
                if ((access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) != 0)
                    throw new IllegalStateException("Unsupported JViewer keyboard LED method flags");
                methods[0]++;
                return new MethodVisitor(ASM5) {
                    private int nativeCalls;
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String desc, boolean isInterface) {
                        if (owner.equals("com/ami/iusb/FloppyRedir") &&
                            name.equals("ReadKeybdLEDStatus") && desc.equals("()B")) nativeCalls++;
                    }
                    public void visitEnd() {
                        if (nativeCalls != 1)
                            throw new IllegalStateException("Unsupported JViewer keyboard LED native calls");
                    }
                };
            }
        }, 0);
        if (fields[0] != 2 || methods[0] != 2)
            throw new IllegalStateException("Unsupported JViewer keyboard LED layout; original JAR unchanged");
        final ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(ASM5, writer) {
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, desc, signature, exceptions);
                boolean ledEvent = name.equals("onKeybdLED") && desc.equals("(B)V");
                boolean ledSync = name.equals("syncLED") && desc.equals("()V");
                if (!ledEvent && !ledSync) return method;
                method.visitCode();
                if (ledEvent) {
                    // Keep the remote LED state and soft keyboard display, without JNI.
                    String app = "com/ami/kvm/jviewer/gui/JViewerApp";
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ILOAD, 1);
                    method.visitFieldInsn(PUTFIELD, app, "status", "B");
                    method.visitVarInsn(ALOAD, 0);
                    method.visitFieldInsn(GETFIELD, app, "m_softKeyboard",
                                          "Lcom/ami/kvm/jviewer/softkeyboard/SoftKeyboard;");
                    method.visitVarInsn(ILOAD, 1);
                    method.visitMethodInsn(INVOKEVIRTUAL,
                        "com/ami/kvm/jviewer/softkeyboard/SoftKeyboard", "setLEDs", "(B)V", false);
                }
                method.visitInsn(RETURN);
                method.visitMaxs(2, ledEvent ? 2 : 1);
                method.visitEnd();
                return null;
            }
        }, 0);
        return writer.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected input and output JAR paths");
        boolean patched = false;
        try (JarInputStream input = new JarInputStream(new FileInputStream(args[0]));
             JarOutputStream output = new JarOutputStream(new FileOutputStream(args[1]))) {
            JarEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextJarEntry()) != null) {
                String name = entry.getName();
                String upperName = name.toUpperCase(Locale.ROOT);
                // The compatibility copy cannot retain the vendor's signatures.
                if (upperName.startsWith("META-INF/") &&
                    (upperName.endsWith(".SF") || upperName.endsWith(".RSA") ||
                     upperName.endsWith(".DSA") || upperName.endsWith(".EC") ||
                     upperName.startsWith("META-INF/SIG-") ||
                     upperName.equals("META-INF/MANIFEST.MF"))) continue;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                byte[] contents = bytes.toByteArray();
                if (name.equals("com/ami/kvm/jviewer/gui/JViewerApp.class")) {
                    contents = patch(contents);
                    patched = true;
                }
                output.putNextEntry(new JarEntry(name));
                output.write(contents);
                output.closeEntry();
            }
        }
        if (!patched) throw new IllegalStateException("JViewerApp class missing");
    }
}
