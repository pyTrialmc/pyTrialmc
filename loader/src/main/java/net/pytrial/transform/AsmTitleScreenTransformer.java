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
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Splits the Realms button on the title screen.
 *
 * <p>{@code TitleScreen.init} lays out the menu buttons; the injected call runs after that and gives
 * the Realms button the left half of its slot and the pyTrial mod settings the right half.
 */
public final class AsmTitleScreenTransformer implements ClassTransformer {

    private static final String OWNER = "net/minecraft/client/gui/screens/TitleScreen";
    private static final String METHOD = "init";
    private static final String DESCRIPTOR = "()V";
    private static final String HELPER = "net/pytrial/ui/ModSettingsScreen";
    private static final String ATTACH_DESCRIPTOR = "(Ljava/lang/Object;)V";

    @Override
    public String name() {
        return "AsmTitleScreenTransformer";
    }

    public String resolvedTarget() {
        return OWNER.replace('/', '.') + "#" + METHOD + DESCRIPTOR;
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
            PyTrialLog.error("Cannot parse the title screen", t);
            return null;
        }

        MethodNode init = null;
        for (MethodNode method : node.methods) {
            if (METHOD.equals(method.name) && DESCRIPTOR.equals(method.desc)) {
                init = method;
                break;
            }
        }
        if (init == null || init.instructions == null) {
            PyTrialLog.warn("TitleScreen#" + METHOD + " not found, the Realms button stays untouched");
            return null;
        }

        AbstractInsnNode lastReturn = null;
        for (AbstractInsnNode instruction : init.instructions) {
            if (instruction.getOpcode() == Opcodes.RETURN) {
                lastReturn = instruction;
            }
        }
        if (lastReturn == null) {
            return null;
        }

        InsnList injection = new InsnList();
        injection.add(new VarInsnNode(Opcodes.ALOAD, 0));
        injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "attach", ATTACH_DESCRIPTOR, false));
        init.instructions.insertBefore(lastReturn, injection);

        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            PyTrialLog.info("Hooked the title screen: " + resolvedTarget());
            return writer.toByteArray();
        } catch (Throwable t) {
            PyTrialLog.error("Failed to write back the title screen", t);
            return null;
        }
    }
}
