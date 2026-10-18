package net.pytrial.transform;

import net.pytrial.util.PyTrialLog;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Writes the mod display names into the client language table.
 *
 * <p>The table is refilled on every resource reload, so the entries are applied at the end of
 * {@code LanguageManager.onResourceManagerReload}, where the fresh table is already installed.
 */
public final class AsmLanguageTransformer implements ClassTransformer {

    private static final String OWNER = "net/minecraft/client/resources/language/LanguageManager";
    private static final String METHOD = "onResourceManagerReload";
    private static final String HELPER = "net/pytrial/ui/ModTranslations";
    private static final String APPLY_DESCRIPTOR = "()V";

    @Override
    public String name() {
        return "AsmLanguageTransformer";
    }

    public String resolvedTarget() {
        return OWNER.replace('/', '.') + "#" + METHOD;
    }

    @Override
    public byte[] transform(TransformContext context, byte[] original) {
        if (!OWNER.equals(context.className())) {
            return null;
        }

        ClassNode node = new ClassNode();
        try {
            new ClassReader(original).accept(node, ClassReader.EXPAND_FRAMES);
        } catch (Throwable t) {
            PyTrialLog.error("Cannot parse the language manager", t);
            return null;
        }

        MethodNode reload = null;
        for (MethodNode method : node.methods) {
            if (METHOD.equals(method.name) && method.instructions != null) {
                reload = method;
                break;
            }
        }
        if (reload == null) {
            PyTrialLog.warn("LanguageManager#" + METHOD + " not found, mod names stay untranslated");
            return null;
        }

        AbstractInsnNode lastReturn = null;
        for (AbstractInsnNode instruction : reload.instructions) {
            if (instruction.getOpcode() == Opcodes.RETURN) {
                lastReturn = instruction;
            }
        }
        if (lastReturn == null) {
            return null;
        }

        InsnList injection = new InsnList();
        injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "applyAll", APPLY_DESCRIPTOR, false));
        reload.instructions.insertBefore(lastReturn, injection);

        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            PyTrialLog.info("Hooked the language manager: mod names are applied after every reload");
            return writer.toByteArray();
        } catch (Throwable t) {
            PyTrialLog.error("Failed to write back the language manager", t);
            return null;
        }
    }
}
