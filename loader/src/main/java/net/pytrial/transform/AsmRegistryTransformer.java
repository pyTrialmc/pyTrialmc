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

public final class AsmRegistryTransformer implements ClassTransformer {

    private static final String DISPATCHER = "net/pytrial/bridge/HookDispatcher";
    private static final String TARGET = "net/minecraft/core/registries/BuiltInRegistries";
    private static final String BOOTSTRAP_METHOD = "bootStrap";
    private static final String BOOTSTRAP_DESCRIPTOR = "()V";
    private static final String FREEZE_METHOD = "freeze";

    @Override
    public String name() {
        return "AsmRegistryTransformer";
    }

    @Override
    public byte[] transform(TransformContext context, byte[] original) {
        if (!TARGET.equals(context.className())) {
            return null;
        }

        ClassNode node = new ClassNode();
        try {
            new ClassReader(original).accept(node, ClassReader.EXPAND_FRAMES);
        } catch (Throwable t) {
            PyTrialLog.error("Cannot parse the registry class", t);
            return null;
        }

        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (!BOOTSTRAP_METHOD.equals(method.name) || !BOOTSTRAP_DESCRIPTOR.equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode call)) {
                    continue;
                }
                if (!TARGET.equals(call.owner) || !FREEZE_METHOD.equals(call.name)) {
                    continue;
                }
                InsnList injection = new InsnList();
                injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, DISPATCHER,
                        "onRegistryBootStrap", "()V", false));
                method.instructions.insertBefore(insn, injection);
                changed = true;
                break;
            }
        }

        if (!changed) {
            PyTrialLog.warn("No injection point found in the registry bootstrap flow");
            return null;
        }

        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            PyTrialLog.info("Hooked the registry bootstrap flow (before vanilla freezes it)");
            return writer.toByteArray();
        } catch (Throwable t) {
            PyTrialLog.error("Failed to write back the registry class", t);
            return null;
        }
    }
}
