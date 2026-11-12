import java.io.*;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;

// Java 8's bundled ASM keeps this workaround independent of external libraries.
public final class MacJViewerCompat implements Opcodes {
    private static final String APP = "com/ami/kvm/jviewer/gui/JViewerApp";
    private static final String FRAME = "com/ami/kvm/jviewer/gui/JVFrame";
    private static final String MENU = "com/ami/kvm/jviewer/gui/JVMenu";
    private static final String KEYS = "com/ami/kvm/jviewer/kvmpkts/USBKeyProcessor";
    private static final String LAYOUT = "L" + KEYS + "$Layout;";

    private static boolean member(AbstractInsnNode instruction, String owner,
                                  String name, String desc) {
        if (instruction instanceof FieldInsnNode) {
            FieldInsnNode field = (FieldInsnNode) instruction;
            return field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(desc);
        }
        if (instruction instanceof MethodInsnNode) {
            MethodInsnNode method = (MethodInsnNode) instruction;
            return method.owner.equals(owner) && method.name.equals(name) && method.desc.equals(desc);
        }
        return false;
    }

    private static boolean hasAutoDetectMenu(JarFile jar) throws IOException {
        JarEntry entry = jar.getJarEntry(MENU + ".class");
        if (entry == null) return false;
        ClassNode menu = new ClassNode(ASM5);
        try (InputStream input = jar.getInputStream(entry)) {
            new ClassReader(input).accept(menu, ClassReader.SKIP_CODE);
        }
        for (MethodNode method : menu.methods) {
            if (method.name.equals("notifyLayoutAutoDetect") && method.desc.equals("(Z)V") &&
                (method.access & (ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) == ACC_PUBLIC)
                return true;
        }
        return false;
    }

    private static byte[] patchKeyboard(byte[] original) {
        ClassNode keys = new ClassNode(ASM5);
        new ClassReader(original).accept(keys, 0);
        int fields = 0;
        for (FieldNode field : keys.fields) {
            if ((field.access & ACC_STATIC) == 0 &&
                ((field.name.equals("layoutAutoDetect") && field.desc.equals("Z")) ||
                 (field.name.equals("currentLayout") && field.desc.equals(LAYOUT)))) fields++;
        }
        MethodNode detect = null;
        for (MethodNode method : keys.methods) {
            if (method.name.equals("doLayoutAutoDetect") && method.desc.equals("()V") &&
                (method.access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) == 0) detect = method;
        }
        if (!keys.name.equals(KEYS) || fields != 2 || detect == null)
            throw new IllegalStateException("Unsupported keyboard auto-detection layout");

        AbstractInsnNode[] gate = new AbstractInsnNode[4];
        int gateSize = 0;
        for (AbstractInsnNode instruction : detect.instructions.toArray()) {
            if (instruction.getOpcode() >= 0 && gateSize < gate.length) gate[gateSize++] = instruction;
        }
        if (gateSize != 4 || gate[0].getOpcode() != ALOAD || ((VarInsnNode) gate[0]).var != 0 ||
            gate[1].getOpcode() != GETFIELD || !member(gate[1], KEYS, "layoutAutoDetect", "Z") ||
            gate[2].getOpcode() != IFNE || gate[3].getOpcode() != RETURN)
            throw new IllegalStateException("Keyboard auto-detection flag guard missing");

        // Match only the known straight-line warning and English fallback block.
        // Leave successful detection, key mappings and existing stack frames intact.
        AbstractInsnNode warning = null;
        for (AbstractInsnNode instruction : detect.instructions.toArray()) {
            if (instruction instanceof LdcInsnNode &&
                "Language not supported:".equals(String.valueOf(((LdcInsnNode) instruction).cst).trim())) {
                if (warning != null) throw new IllegalStateException("Ambiguous keyboard warning");
                warning = instruction;
            }
        }
        if (warning == null) throw new IllegalStateException("Keyboard warning block missing");
        AbstractInsnNode first = warning;
        for (int count = 0; count < 4; count++) {
            do { first = first.getPrevious(); } while (first != null && first.getOpcode() < 0);
            if (first == null) throw new IllegalStateException("Incomplete keyboard warning block");
        }
        int[] opcodes = {GETSTATIC, NEW, DUP, INVOKESPECIAL, LDC, INVOKEVIRTUAL,
                         ALOAD, INVOKEVIRTUAL, INVOKEVIRTUAL, INVOKEVIRTUAL, GETSTATIC, ASTORE};
        Set<LabelNode> targets = new HashSet<LabelNode>();
        for (AbstractInsnNode instruction : detect.instructions.toArray()) {
            if (instruction instanceof JumpInsnNode) targets.add(((JumpInsnNode) instruction).label);
            if (instruction instanceof TableSwitchInsnNode || instruction instanceof LookupSwitchInsnNode)
                throw new IllegalStateException("Unsupported keyboard detection control flow");
        }
        if (!detect.tryCatchBlocks.isEmpty())
            throw new IllegalStateException("Unsupported keyboard detection exception handlers");
        AbstractInsnNode[] block = new AbstractInsnNode[opcodes.length];
        AbstractInsnNode next = first;
        for (int index = 0; index < opcodes.length; index++) {
            // Internal labels/branches would make replacing this block unsafe.
            while (next != null && next.getOpcode() < 0) {
                if (targets.contains(next))
                    throw new IllegalStateException("Branch into keyboard warning block");
                next = next.getNext();
            }
            if (next == null || next.getOpcode() != opcodes[index])
                throw new IllegalStateException("Unsupported keyboard warning instructions");
            block[index] = next;
            next = next.getNext();
        }
        String builder = "java/lang/StringBuilder";
        String append = "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
        if (!member(block[0], "java/lang/System", "err", "Ljava/io/PrintStream;") ||
            !((TypeInsnNode) block[1]).desc.equals(builder) ||
            !member(block[3], builder, "<init>", "()V") || block[4] != warning ||
            !member(block[5], builder, "append", append) ||
            !member(block[7], builder, "append", append) ||
            !member(block[8], builder, "toString", "()Ljava/lang/String;") ||
            !member(block[9], "java/io/PrintStream", "println", "(Ljava/lang/String;)V") ||
            !member(block[10], KEYS + "$Layout", "ENGLISH", LAYOUT))
            throw new IllegalStateException("Unsupported keyboard warning calls");

        MethodNode fallback = new MethodNode(ASM5);
        fallback.visitVarInsn(ALOAD, 0);
        fallback.visitInsn(ICONST_0);
        fallback.visitFieldInsn(PUTFIELD, KEYS, "layoutAutoDetect", "Z");
        fallback.visitMethodInsn(INVOKESTATIC, APP, "getInstance", "()L" + APP + ";", false);
        fallback.visitMethodInsn(INVOKEVIRTUAL, APP, "getMainWindow", "()L" + FRAME + ";", false);
        fallback.visitMethodInsn(INVOKEVIRTUAL, FRAME, "getMenu", "()L" + MENU + ";", false);
        fallback.visitInsn(ICONST_0);
        fallback.visitMethodInsn(INVOKEVIRTUAL, MENU, "notifyLayoutAutoDetect", "(Z)V", false);
        fallback.visitFieldInsn(GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
        fallback.visitLdcInsn("Keyboard language auto-detection unavailable; keeping the current layout. " +
                             "Choose your layout in Keyboard > Language.");
        fallback.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        fallback.visitInsn(RETURN);
        detect.instructions.insertBefore(first, fallback.instructions);
        for (AbstractInsnNode instruction : block) detect.instructions.remove(instruction);
        detect.maxStack = Math.max(detect.maxStack, 2);
        ClassWriter writer = new ClassWriter(0);
        keys.accept(writer);
        return writer.toByteArray();
    }

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
        try (JarFile source = new JarFile(args[0]);
             JarInputStream input = new JarInputStream(new FileInputStream(args[0]));
             JarOutputStream output = new JarOutputStream(new FileOutputStream(args[1]))) {
            boolean autoDetectMenu = hasAutoDetectMenu(source);
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
                if (name.equals(KEYS + ".class")) {
                    try {
                        if (!autoDetectMenu) throw new IllegalStateException("Keyboard menu API missing");
                        contents = patchKeyboard(contents);
                    } catch (IllegalStateException unsupported) {
                        System.err.println("Keyboard auto-detection workaround unavailable for this firmware: " +
                            unsupported.getMessage() + ". Use Keyboard > Language manually if typing is slow.");
                    }
                }
                output.putNextEntry(new JarEntry(name));
                output.write(contents);
                output.closeEntry();
            }
        }
        if (!patched) throw new IllegalStateException("JViewerApp class missing");
    }
}
