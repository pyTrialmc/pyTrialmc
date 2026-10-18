package net.pytrial.transform;

import net.pytrial.mapping.MappingSet;
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

public final class AsmOverlayTransformer implements ClassTransformer {

    private static final String DISPATCHER = "net/pytrial/bridge/HookDispatcher";
    private static final String OVERLAY = "net.minecraft.client.gui.screens.LoadingOverlay";
    private static final String FADE_METHOD = "isReadyToFadeOut";
    private static final String FADE_DESCRIPTOR = "()Z";
    private static final String RENDER_CALL = "onOverlayRender";
    private static final String RENDER_CALL_DESCRIPTOR = "(Ljava/lang/Object;Ljava/lang/Object;II)V";
    private static final String FADE_CALL = "adjustFadeOut";
    private static final String FADE_CALL_DESCRIPTOR = "(Z)Z";

    private static final String[][] CANDIDATES = {
            {OVERLAY, "extractRenderState", "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"},
            {OVERLAY, "render", "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"}
    };

    private final String owner;
    private final String method;
    private final String descriptor;
    private final String fadeMethod;

    public AsmOverlayTransformer(MappingSet mappings) {
        String resolvedOwner = null;
        String resolvedMethod = null;
        String resolvedDescriptor = null;
        for (String[] candidate : CANDIDATES) {
            MappingSet.MethodRef ref = mappings.obfMethod(candidate[0], candidate[1], candidate[2]);
            if (ref != null) {
                resolvedOwner = ref.owner();
                resolvedMethod = ref.name();
                resolvedDescriptor = ref.descriptor();
                break;
            }
        }
        this.owner = resolvedOwner;
        this.method = resolvedMethod;
        this.descriptor = resolvedDescriptor;

        MappingSet.MethodRef fade = mappings.obfMethod(OVERLAY, FADE_METHOD, FADE_DESCRIPTOR);
        this.fadeMethod = fade != null ? fade.name() : null;
    }

    public boolean isResolved() {
        return owner != null;
    }

    public String resolvedTarget() {
        String target = owner != null
                ? owner.replace('/', '.') + "#" + method + descriptor
                : "<unresolved>";
        return fadeMethod != null ? target + " + #" + fadeMethod + FADE_DESCRIPTOR : target;
    }

    @Override
    public String name() {
        return "AsmOverlayTransformer";
    }

    @Override
    public byte[] transform(TransformContext context, byte[] original) {
        if (owner == null || !owner.equals(context.className())) {
            return null;
        }

        ClassNode node = new ClassNode();
        try {
            new ClassReader(original).accept(node, ClassReader.EXPAND_FRAMES);
        } catch (Throwable t) {
            PyTrialLog.error("Cannot parse the loading screen class", t);
            return null;
        }

        boolean changed = false;
        for (MethodNode candidate : node.methods) {
            if (candidate.instructions == null || candidate.instructions.size() == 0) {
                continue;
            }
            if (method != null && method.equals(candidate.name) && descriptor.equals(candidate.desc)) {
                candidate.instructions.insert(renderInjection());
                changed = true;
            } else if (fadeMethod != null && fadeMethod.equals(candidate.name)
                    && FADE_DESCRIPTOR.equals(candidate.desc)) {
                changed |= injectFadeGuard(candidate);
            }
        }

        if (!changed) {
            return null;
        }

        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            PyTrialLog.info("Hooked the loading screen: " + context.dottedName() + "#" + method);
            return writer.toByteArray();
        } catch (Throwable t) {
            PyTrialLog.error("Failed to write back the loading screen class", t);
            return null;
        }
    }

    private static InsnList renderInjection() {
        InsnList injection = new InsnList();
        injection.add(new VarInsnNode(Opcodes.ALOAD, 0));
        injection.add(new VarInsnNode(Opcodes.ALOAD, 1));
        injection.add(new VarInsnNode(Opcodes.ILOAD, 2));
        injection.add(new VarInsnNode(Opcodes.ILOAD, 3));
        injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, DISPATCHER, RENDER_CALL,
                RENDER_CALL_DESCRIPTOR, false));
        return injection;
    }

    private static boolean injectFadeGuard(MethodNode methodNode) {
        boolean patched = false;
        for (AbstractInsnNode instruction : methodNode.instructions) {
            if (instruction.getOpcode() != Opcodes.IRETURN) {
                continue;
            }
            InsnList patch = new InsnList();
            patch.add(new MethodInsnNode(Opcodes.INVOKESTATIC, DISPATCHER, FADE_CALL,
                    FADE_CALL_DESCRIPTOR, false));
            methodNode.instructions.insertBefore(instruction, patch);
            patched = true;
        }
        return patched;
    }
}
