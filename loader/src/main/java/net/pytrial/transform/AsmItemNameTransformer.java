package net.pytrial.transform;

import net.pytrial.util.PyTrialLog;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Replaces {@code Item.getName(ItemStack)} with a call into {@link net.pytrial.ui.ItemNames}.
 *
 * <p>Vanilla builds the name from the {@code ITEM_NAME} data component and otherwise falls back to
 * the translation key. Mod items are created by reflection, so their component initialisers never
 * run and the tooltip showed {@code item.pymod.lucky_block}. Taking the method over gives the mod
 * names a reliable route while keeping the vanilla behaviour for everything else.
 */
public final class AsmItemNameTransformer implements ClassTransformer {

    private static final String OWNER = "net/minecraft/world/item/Item";
    private static final String METHOD = "getName";
    private static final String STACK = "net/minecraft/world/item/ItemStack";
    private static final String COMPONENT = "net/minecraft/network/chat/Component";
    private static final String DESCRIPTOR = "(L" + STACK + ";)L" + COMPONENT + ";";
    private static final String HELPER = "net/pytrial/ui/ItemNames";
    private static final String HELPER_DESCRIPTOR = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";

    @Override
    public String name() {
        return "AsmItemNameTransformer";
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
            PyTrialLog.error("Cannot parse the item class", t);
            return null;
        }

        MethodNode target = null;
        for (MethodNode method : node.methods) {
            if (METHOD.equals(method.name) && DESCRIPTOR.equals(method.desc)) {
                target = method;
                break;
            }
        }
        if (target == null) {
            PyTrialLog.warn("Item#" + METHOD + " not found, mod item names stay untranslated");
            return null;
        }

        InsnList replacement = new InsnList();
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "of", HELPER_DESCRIPTOR, false));
        replacement.add(new TypeInsnNode(Opcodes.CHECKCAST, COMPONENT));
        replacement.add(new InsnNode(Opcodes.ARETURN));

        target.instructions.clear();
        if (target.tryCatchBlocks != null) {
            target.tryCatchBlocks.clear();
        }
        target.instructions.add(replacement);

        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            PyTrialLog.info("Hooked the item name lookup: " + resolvedTarget());
            return writer.toByteArray();
        } catch (Throwable t) {
            PyTrialLog.error("Failed to write back the item class", t);
            return null;
        }
    }
}
