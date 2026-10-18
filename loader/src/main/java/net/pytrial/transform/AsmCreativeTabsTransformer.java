package net.pytrial.transform;

import net.pytrial.util.PyTrialLog;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Makes the creative tab bar usable for mod tabs.
 *
 * <ul>
 *   <li>the four fixed tab textures are expanded, because the vanilla code clamps a column to the
 *       array length (an inclusive clamp, so the length itself indexes out of bounds);</li>
 *   <li>{@code getTabX} stores the slot inside the current page instead of the raw column, and moves
 *       tabs of other pages off screen, which keeps drawing and click tests consistent;</li>
 *   <li>{@code keyPressed} forwards the key to the paging logic;</li>
 *   <li>{@code extractRenderState} draws the page label;</li>
 *   <li>the constructor resets the page to the first one.</li>
 * </ul>
 */
public final class AsmCreativeTabsTransformer implements ClassTransformer {

    private static final String OWNER = "net/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen";
    private static final String HELPER = "net/pytrial/ui/CreativeTabTextures";
    private static final String PAGES = "net/pytrial/ui/CreativeTabPages";
    private static final String TAB = "net/minecraft/world/item/CreativeModeTab";
    private static final String KEY_EVENT = "net/minecraft/client/input/KeyEvent";
    private static final String MOUSE_EVENT = "net/minecraft/client/input/MouseButtonEvent";
    private static final String GRAPHICS = "net/minecraft/client/gui/GuiGraphicsExtractor";

    private static final String TEXTURE_DESCRIPTOR = "[Lnet/minecraft/resources/Identifier;";
    private static final String EXPAND_DESCRIPTOR = "([Ljava/lang/Object;)[Ljava/lang/Object;";
    private static final String TAB_DESCRIPTOR = "L" + TAB + ";";
    private static final String KEY_DESCRIPTOR = "L" + KEY_EVENT + ";";
    private static final String RENDER_DESCRIPTOR = "(L" + GRAPHICS + ";IIF)V";
    private static final String CLICK_DESCRIPTOR = "(L" + MOUSE_EVENT + ";Z)Z";
    private static final String SLOT_DESCRIPTOR = "(Ljava/lang/Object;I)I";
    private static final String ON_KEY_DESCRIPTOR = "(Ljava/lang/Object;)V";
    private static final String ON_CLICK_DESCRIPTOR = "(Ljava/lang/Object;Ljava/lang/Object;)V";
    private static final String DRAW_DESCRIPTOR = "(Ljava/lang/Object;Ljava/lang/Object;II)V";

    private static final String[] TEXTURE_FIELDS = {
            "UNSELECTED_TOP_TABS",
            "SELECTED_TOP_TABS",
            "UNSELECTED_BOTTOM_TABS",
            "SELECTED_BOTTOM_TABS"
    };

    @Override
    public String name() {
        return "AsmCreativeTabsTransformer";
    }

    public String resolvedTarget() {
        return OWNER.replace('/', '.') + "#<clinit>, getTabX, keyPressed, extractRenderState";
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
            PyTrialLog.error("Cannot parse the creative inventory class", t);
            return null;
        }

        int patched = 0;
        patched += expandTextures(node);
        patched += patchConstructor(node);
        patched += patchTabPosition(node);
        patched += patchKeyInput(node);
        patched += patchMouseInput(node);
        patched += patchRender(node);
        if (patched == 0) {
            return null;
        }

        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            PyTrialLog.info("Hooked the creative inventory: " + patched + " change(s), "
                    + node.methods.size() + " method(s) inspected");
            return writer.toByteArray();
        } catch (Throwable t) {
            PyTrialLog.error("Failed to write back the creative inventory class", t);
            return null;
        }
    }

    /** Expands the four fixed tab textures so higher columns stay in bounds. */
    private static int expandTextures(ClassNode node) {
        MethodNode clinit = findMethod(node, "<clinit>", "()V");
        if (clinit == null || clinit.instructions == null) {
            return 0;
        }

        InsnList injection = new InsnList();
        int patched = 0;
        for (String field : TEXTURE_FIELDS) {
            if (!hasField(node, field)) {
                continue;
            }
            injection.add(new FieldInsnNode(Opcodes.GETSTATIC, OWNER, field, TEXTURE_DESCRIPTOR));
            injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "expand", EXPAND_DESCRIPTOR, false));
            injection.add(new TypeInsnNode(Opcodes.CHECKCAST, TEXTURE_DESCRIPTOR));
            injection.add(new FieldInsnNode(Opcodes.PUTSTATIC, OWNER, field, TEXTURE_DESCRIPTOR));
            patched++;
        }
        if (patched == 0) {
            return 0;
        }

        AbstractInsnNode lastReturn = lastReturn(clinit);
        if (lastReturn == null) {
            return 0;
        }
        clinit.instructions.insertBefore(lastReturn, injection);
        return 1;
    }

    private static int patchConstructor(ClassNode node) {
        int patched = 0;
        for (MethodNode method : node.methods) {
            if (!"<init>".equals(method.name) || method.instructions == null) {
                continue;
            }
            InsnList injection = new InsnList();
            injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PAGES, "reset", "()V", false));
            method.instructions.insert(injection);
            patched++;
        }
        return patched;
    }

    /** getTabX: rewrite the column it stores into the slot of the current page. */
    private static int patchTabPosition(ClassNode node) {
        MethodNode getTabX = findMethod(node, "getTabX", "(" + TAB_DESCRIPTOR + ")I");
        if (getTabX == null || getTabX.instructions == null) {
            PyTrialLog.warn("getTabX not found, creative tab pages will not be laid out");
            return 0;
        }

        AbstractInsnNode anchor = null;
        for (AbstractInsnNode instruction = getTabX.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode call) || !"column".equals(call.name)) {
                continue;
            }
            AbstractInsnNode next = skipMetadata(instruction.getNext());
            if (next instanceof VarInsnNode store && store.getOpcode() == Opcodes.ISTORE) {
                anchor = store;
                break;
            }
        }
        if (anchor == null) {
            PyTrialLog.warn("The column of getTabX was not found, page layout skipped");
            return 0;
        }

        int local = ((VarInsnNode) anchor).var;
        InsnList injection = new InsnList();
        injection.add(new VarInsnNode(Opcodes.ALOAD, 1));
        injection.add(new VarInsnNode(Opcodes.ILOAD, local));
        injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PAGES, "slot", SLOT_DESCRIPTOR, false));
        injection.add(new VarInsnNode(Opcodes.ISTORE, local));
        getTabX.instructions.insert(anchor, injection);
        return 1;
    }

    private static int patchKeyInput(ClassNode node) {
        MethodNode keyPressed = findMethod(node, "keyPressed", "(" + KEY_DESCRIPTOR + ")Z");
        if (keyPressed == null || keyPressed.instructions == null) {
            PyTrialLog.warn("keyPressed not found, the creative tab page keys are unavailable");
            return 0;
        }
        InsnList injection = new InsnList();
        injection.add(new VarInsnNode(Opcodes.ALOAD, 1));
        injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PAGES, "onKey", ON_KEY_DESCRIPTOR, false));
        keyPressed.instructions.insert(injection);
        return 1;
    }

    private static int patchMouseInput(ClassNode node) {
        MethodNode mouseClicked = findMethod(node, "mouseClicked", CLICK_DESCRIPTOR);
        if (mouseClicked == null || mouseClicked.instructions == null) {
            PyTrialLog.warn("mouseClicked not found, the creative tab page buttons are not clickable");
            return 0;
        }
        InsnList injection = new InsnList();
        injection.add(new VarInsnNode(Opcodes.ALOAD, 0));
        injection.add(new VarInsnNode(Opcodes.ALOAD, 1));
        injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PAGES, "onClick", ON_CLICK_DESCRIPTOR, false));
        mouseClicked.instructions.insert(injection);
        return 1;
    }

    private static int patchRender(ClassNode node) {
        MethodNode render = findMethod(node, "extractRenderState", RENDER_DESCRIPTOR);
        if (render == null || render.instructions == null) {
            PyTrialLog.warn("extractRenderState not found, the creative tab page label is unavailable");
            return 0;
        }
        AbstractInsnNode lastReturn = lastReturn(render);
        if (lastReturn == null) {
            return 0;
        }
        InsnList injection = new InsnList();
        injection.add(new VarInsnNode(Opcodes.ALOAD, 1));
        injection.add(new VarInsnNode(Opcodes.ALOAD, 0));
        injection.add(new VarInsnNode(Opcodes.ILOAD, 2));
        injection.add(new VarInsnNode(Opcodes.ILOAD, 3));
        injection.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PAGES, "draw", DRAW_DESCRIPTOR, false));
        render.instructions.insertBefore(lastReturn, injection);
        return 1;
    }

    private static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static AbstractInsnNode lastReturn(MethodNode method) {
        AbstractInsnNode last = null;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == Opcodes.RETURN) {
                last = instruction;
            }
        }
        return last;
    }

    private static AbstractInsnNode skipMetadata(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction;
        while (current != null && current.getOpcode() < 0) {
            current = current.getNext();
        }
        return current;
    }

    private static boolean hasField(ClassNode node, String name) {
        for (FieldNode field : node.fields) {
            if (name.equals(field.name) && TEXTURE_DESCRIPTOR.equals(field.desc)) {
                return true;
            }
        }
        return false;
    }
}
