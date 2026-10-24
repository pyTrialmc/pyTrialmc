package net.pytrial.ui;

import net.pytrial.bridge.HookDispatcher;
import net.pytrial.util.PyTrialLog;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The screen a mod opens with {@code register_gui} / {@code open_gui}.
 *
 * <p>Like {@link ModSettingsScreen} the loader has no compile time dependency on the game, so the
 * screen class is generated (it has to extend {@code Screen}) and every call into the game goes
 * through reflection. A declaration carries a title, a list of slots and a list of buttons; the
 * screen draws the slots as a grid of labels and the buttons as real widgets, and reports a click
 * back to Python as the {@code gui_click} hook.
 */
public final class ModGuiScreen {

    private static final String SCREEN = "net/minecraft/client/gui/screens/Screen";
    private static final String GRAPHICS = "net/minecraft/client/gui/GuiGraphicsExtractor";
    private static final String COMPONENT = "net/minecraft/network/chat/Component";
    private static final String BUTTON = "net.minecraft.client.gui.components.Button";
    private static final String BUILDER = "net.minecraft.client.gui.components.Button$Builder";
    private static final String ON_PRESS = "net.minecraft.client.gui.components.Button$OnPress";
    private static final String MINECRAFT = "net.minecraft.client.Minecraft";

    private static final int SLOT_COLUMNS = 9;
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_GAP = 2;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final int BUTTON_WIDTH = 150;

    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF909090;
    private static final int COLOR_SLOT = 0xFF2B2B2B;
    private static final int COLOR_SLOT_EDGE = 0xFF555555;
    private static final int COLOR_PANEL = 0xFF101010;

    /** A declared GUI: what to draw and which buttons to offer. */
    public record Definition(String id, String title, List<String> slots, List<String> buttons) {
    }

    private static final Map<String, Definition> REGISTRY = new LinkedHashMap<>();
    private static volatile String current;

    private static boolean prepared;
    private static Object minecraft;
    private static Method literal;
    private static Method setScreenAndShow;
    private static Method builderMethod;
    private static Method builderBounds;
    private static Method builderBuild;
    private static Method textMethod;
    private static Method centeredTextMethod;
    private static Method fillMethod;
    private static Field fontField;
    private static Class<?> screenClass;

    private ModGuiScreen() {
    }

    public static synchronized void declare(String id, String title, List<String> slots, List<String> buttons) {
        REGISTRY.put(id, new Definition(id, title == null || title.isBlank() ? id : title,
                List.copyOf(slots), List.copyOf(buttons)));
        PyTrialLog.info("Declared gui " + id + " (" + slots.size() + " slot(s), " + buttons.size()
                + " button(s))");
    }

    public static synchronized boolean isDeclared(String id) {
        return REGISTRY.containsKey(id);
    }

    public static void open(String id) {
        try {
            prepare();
            Definition definition = REGISTRY.get(id);
            if (definition == null) {
                PyTrialLog.warn("No gui declared with id '" + id + "'");
                return;
            }
            if (screenClass == null) {
                screenClass = generateScreenClass();
            }
            current = id;
            Object screen = screenClass.getConstructor(load(COMPONENT, false))
                    .newInstance(literal.invoke(null, definition.title()));
            setScreenAndShow.invoke(minecraft, screen);
        } catch (Throwable t) {
            PyTrialLog.error("Cannot open gui '" + id + "'", t);
        }
    }

    public static void close() {
        try {
            prepare();
            setScreenAndShow.invoke(minecraft, (Object) null);
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot close the gui: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ screen body

    /** Called from the generated {@code init}: builds one button per declared button. */
    static void init(Object screen) {
        Definition definition = REGISTRY.get(current);
        if (definition == null) {
            return;
        }
        try {
            Method addRenderable = screen.getClass().getMethod("addRenderableWidget",
                    load("net/minecraft/client/gui/components/events/GuiEventListener", true));
            int count = definition.buttons().size() + 1;
            int width = intField(screen, "width", 320);
            int height = intField(screen, "height", 240);
            int startY = height - 30 - count * (BUTTON_HEIGHT + BUTTON_GAP);
            int x = (width - BUTTON_WIDTH) / 2;
            int index = 0;
            for (String label : definition.buttons()) {
                addRenderable.invoke(screen,
                        button(label, definition.id(), label, x, startY + index * (BUTTON_HEIGHT + BUTTON_GAP)));
                index++;
            }
            addRenderable.invoke(screen,
                    button("Done", definition.id(), null, x, startY + index * (BUTTON_HEIGHT + BUTTON_GAP)));
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot build the gui widgets: " + t.getMessage());
        }
    }

    private static int intField(Object target, String name, int fallback) {
        try {
            Field field = target.getClass().getField(name);
            return field.getInt(target);
        } catch (Throwable t) {
            try {
                Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(target);
            } catch (Throwable ignored) {
                return fallback;
            }
        }
    }

    /** Called from the generated {@code extractRenderState}, after the widgets have been drawn. */
    static void render(Object graphics) {
        Definition definition = REGISTRY.get(current);
        if (definition == null) {
            return;
        }
        try {
            Object font = fontField.get(minecraft);
            int width = viewWidth();
            int top = 30;
            centeredTextMethod.invoke(graphics, font, definition.title(), width / 2, top, COLOR_TITLE);
            textMethod.invoke(graphics, font, definition.id(), 20, top, COLOR_DIM);

            int columns = Math.max(1, Math.min(SLOT_COLUMNS, definition.slots().size()));
            int gridWidth = columns * SLOT_SIZE + (columns - 1) * SLOT_GAP;
            int gridX = (width - gridWidth) / 2;
            int gridY = top + 26;
            int rows = Math.max(1, (definition.slots().size() + columns - 1) / columns);
            fillMethod.invoke(graphics, gridX - 4, gridY - 4,
                    gridX + gridWidth + 4, gridY + rows * (SLOT_SIZE + SLOT_GAP) + 4, COLOR_PANEL);

            for (int i = 0; i < definition.slots().size(); i++) {
                int x = gridX + (i % columns) * (SLOT_SIZE + SLOT_GAP);
                int y = gridY + (i / columns) * (SLOT_SIZE + SLOT_GAP);
                fillMethod.invoke(graphics, x, y, x + SLOT_SIZE, y + SLOT_SIZE, COLOR_SLOT);
                fillMethod.invoke(graphics, x, y, x + SLOT_SIZE, y + 1, COLOR_SLOT_EDGE);
                String label = shortLabel(definition.slots().get(i));
                textMethod.invoke(graphics, font, label, x + 3, y + 6, COLOR_TEXT);
            }

            if (definition.slots().isEmpty()) {
                textMethod.invoke(graphics, font, "no slots declared", gridX, gridY, COLOR_DIM);
            }
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot render the gui: " + t.getMessage());
        }
    }

    private static String shortLabel(String slot) {
        String value = slot == null ? "" : slot;
        int colon = value.indexOf(':');
        String path = colon >= 0 ? value.substring(colon + 1) : value;
        return path.length() > 2 ? path.substring(0, 2) : path;
    }

    private static int viewWidth() {
        return guiScaled("getGuiScaledWidth", 320);
    }

    private static int viewHeight() {
        return guiScaled("getGuiScaledHeight", 240);
    }

    private static int guiScaled(String method, int fallback) {
        try {
            Object window = minecraft.getClass().getMethod("getWindow").invoke(minecraft);
            return (int) window.getClass().getMethod(method).invoke(window);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static Object button(String label, String guiId, String buttonId, int x, int y) throws Exception {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("onPress".equals(name)) {
                if (buttonId == null) {
                    close();
                } else {
                    HookDispatcher.dispatch("gui_click", new Object[]{guiId, buttonId});
                }
                return null;
            }
            if ("toString".equals(name)) {
                return "pyTrialGuiButton";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == (args == null ? null : args[0]);
            }
            return null;
        };
        Object onPress = Proxy.newProxyInstance(loader(), new Class<?>[]{load(ON_PRESS, true)}, handler);
        Object component = literal.invoke(null, label);
        Object builder = builderMethod.invoke(null, component, onPress);
        builderBounds.invoke(builder, x, y, BUTTON_WIDTH, BUTTON_HEIGHT);
        return builderBuild.invoke(builder);
    }

    // ------------------------------------------------------------------ plumbing

    private static Class<?> generateScreenClass() throws Exception {
        String name = "net/pytrial/ui/GeneratedGuiScreen";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, SCREEN, null);

        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(L" + COMPONENT + ";)V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, SCREEN, "<init>", "(L" + COMPONENT + ";)V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PROTECTED, "init", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, SCREEN, "init", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESTATIC, "net/pytrial/ui/ModGuiScreen", "init",
                "(Ljava/lang/Object;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor render = writer.visitMethod(Opcodes.ACC_PUBLIC, "extractRenderState",
                "(L" + GRAPHICS + ";IIF)V", null, null);
        render.visitCode();
        render.visitVarInsn(Opcodes.ALOAD, 0);
        render.visitVarInsn(Opcodes.ALOAD, 1);
        render.visitVarInsn(Opcodes.ILOAD, 2);
        render.visitVarInsn(Opcodes.ILOAD, 3);
        render.visitVarInsn(Opcodes.FLOAD, 4);
        render.visitMethodInsn(Opcodes.INVOKESPECIAL, SCREEN, "extractRenderState",
                "(L" + GRAPHICS + ";IIF)V", false);
        render.visitVarInsn(Opcodes.ALOAD, 1);
        render.visitMethodInsn(Opcodes.INVOKESTATIC, "net/pytrial/ui/ModGuiScreen", "render",
                "(Ljava/lang/Object;)V", false);
        render.visitInsn(Opcodes.RETURN);
        render.visitMaxs(0, 0);
        render.visitEnd();

        writer.visitEnd();
        return MethodHandles.lookup().defineClass(writer.toByteArray());
    }

    private static synchronized void prepare() throws Exception {
        if (prepared) {
            return;
        }
        Class<?> minecraftClass = load(MINECRAFT, true);
        minecraft = minecraftClass.getMethod("getInstance").invoke(null);
        literal = load(COMPONENT, false).getMethod("literal", String.class);
        setScreenAndShow = minecraftClass.getMethod("setScreenAndShow", load(SCREEN, true));

        Class<?> builderClass = load(BUILDER, true);
        builderMethod = builderClass.getMethod("builder", load(COMPONENT, false), load(ON_PRESS, true));
        builderBounds = builderClass.getMethod("bounds", int.class, int.class, int.class, int.class);
        builderBuild = builderClass.getMethod("build");

        Class<?> graphicsClass = load(GRAPHICS, false);
        for (Method candidate : graphicsClass.getMethods()) {
            if (candidate.getParameterCount() != 5 || candidate.getParameterTypes()[1] != String.class
                    || candidate.getParameterTypes()[2] != int.class) {
                continue;
            }
            if ("text".equals(candidate.getName())) {
                textMethod = candidate;
            } else if ("centeredText".equals(candidate.getName())) {
                centeredTextMethod = candidate;
            }
        }
        if (centeredTextMethod == null) {
            centeredTextMethod = textMethod;
        }
        fillMethod = graphicsClass.getMethod("fill", int.class, int.class, int.class, int.class, int.class);

        fontField = minecraftClass.getField("font");
        fontField.setAccessible(true);
        prepared = true;
    }

    private static ClassLoader loader() {
        return ModGuiScreen.class.getClassLoader();
    }

    private static Class<?> load(String internalName, boolean initialize) throws ClassNotFoundException {
        return Class.forName(internalName.replace('/', '.'), initialize, loader());
    }
}
