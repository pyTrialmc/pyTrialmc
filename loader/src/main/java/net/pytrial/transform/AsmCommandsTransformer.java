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

public final class AsmCommandsTransformer implements ClassTransformer {

    private static final String DISPATCHER = "net/pytrial/bridge/HookDispatcher";
    private static final String TARGET = "net/minecraft/commands/Commands";
    private static final String CONSTRUCTOR = "<init>";

    @Override
    public String name() {
        return "AsmCommandsTransformer";
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
            PyTrialLog.error("Cannot parse the command registration class", t);
            return null;
        }

        boolean changed = false;
        for (MethodNode method : node.methods) {
            if (!CONSTRUCTOR.equals(method.name)) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() != Opcodes.RETURN) {
                    continue;
                }
                InsnList injection = new InsnList();
                injection.add(new VarInsnNode(Opcodes.ALOAD, 0));
                injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, DISPATCHER,
                        "onCommandsCreated", "(Ljava/lang/Object;)V", false));
                method.instructions.insertBefore(insn, injection);
                changed = true;
            }
        }

        if (!changed) {
            return null;
        }

        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            PyTrialLog.info("Hooked the command registration flow: " + context.dottedName());
            return writer.toByteArray();
        } catch (Throwable t) {
            PyTrialLog.error("Failed to write back the command registration class", t);
            return null;
        }
    }
}
