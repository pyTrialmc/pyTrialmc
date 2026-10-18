package net.pytrial.ui;

import net.pytrial.util.PyTrialLog;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * The "Mods" screen and the split of the Realms button on the title screen.
 *
 * <p>The loader has no compile time dependency on the game, so two things are done reflectively: the
 * screen class itself is generated (it has to extend {@code Screen}) and the widgets are built
 * through {@code Button.builder}. The Realms button is identified by its own localised label, then
 * halved: it keeps the left half and the right half becomes this screen.
 *
 * <p>The layout is two bordered panels: the mod list on the left, the pyTrial switches on the right.
 */
public final class ModSettingsScreen {

    private static final String SCREEN = "net/minecraft/client/gui/screens/Screen";
    private static final String GRAPHICS = "net/minecraft/client/gui/GuiGraphicsExtractor";
    private static final String COMPONENT = "net/minecraft/network/chat/Component";
    private static final String BUTTON = "net.minecraft.client.gui.components.Button";
    private static final String BUILDER = "net.minecraft.client.gui.components.Button$Builder";
    private static final String ON_PRESS = "net.minecraft.client.gui.components.Button$OnPress";
    private static final String WIDGET = "net.minecraft.client.gui.components.AbstractWidget";
    private static final String MINECRAFT = "net.minecraft.client.Minecraft";
    private static final String GUI_EVENT_LISTENER = "net/minecraft/client/gui/components/events/GuiEventListener";

    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 5;
    private static final int PANEL_PADDING = 8;
    private static final int CONTENT_MAX_WIDTH = 460;
    private static final int COLUMN_GAP = 12;
    private static final int TITLE_Y = 16;
    private static final int SEPARATOR_Y = 32;
    private static final int CONTENT_TOP = 46;
    private static final int DONE_WIDTH = 150;

    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_HEADER = 0xFFFFAA00;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF909090;
    private static final int COLOR_OK = 0xFF55FF55;
    private static final int COLOR_BAD = 0xFFFF5555;
    private static final int COLOR_TAG = 0xFF8A8AFF;
    private static final int COLOR_PANEL = 0x90101010;
    private static final int COLOR_BORDER = 0xFF3C3C3C;

    private static Class<?> screenClass;
    private static Object parentScreen;
    private static Object minecraft;

    private static Method addRenderableWidget;
    private static Method children;
    private static Method setScreenAndShow;
    private static Method setWidth;
    private static Method setHeight;
    private static Method setX;
    private static Method setY;
    private static Method getX;
    private static Method getY;
    private static Method getHeight;
    private static Method getWidth;
    private static Method getMessage;
    private static Method translatable;
    private static Method getString;
    private static Method literal;
    private static Method builderMethod;
    private static Method builderBounds;
    private static Method builderBuild;
    private static Method textMethod;
    private static Method centeredTextMethod;
    private static Method fillMethod;
    private static Method fontWidthMethod;
    private static Field screenWidthField;
    private static Field screenHeightField;
    private static Field fontField;

    private static int viewWidth = 320;
    private static int viewHeight = 240;
    private static int leftPanelX;
    private static int leftPanelY;
    private static int leftPanelWidth;
    private static int rightPanelX;
    private static int rightPanelWidth;
    private static int panelHeight;

    private static boolean resolved;
    private static boolean resolving;

    private ModSettingsScreen() {
    }

    /** Called from the patched {@code TitleScreen.init()}. */
    public static void attach(Object titleScreen) {
        if (titleScreen == null) {
            return;
        }
        parentScreen = titleScreen;
        try {
            resolve();
            if (getMessage == null || children == null) {
                return;
            }
            String realmsLabel = label("menu.online");
            if (realmsLabel == null) {
                return;
            }
            Object realms = null;
            StringBuilder seen = new StringBuilder();
            for (Object child : list(children.invoke(titleScreen))) {
                try {
                    Object message = getMessage.invoke(child);
                    String text = String.valueOf(getString.invoke(message));
                    seen.append('[').append(text).append(']');
                    if (realmsLabel.equals(text)) {
                        realms = child;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (realms == null) {
                PyTrialLog.warn("The Realms button (" + realmsLabel + ") was not found; widgets: " + seen);
                return;
            }
            int width = (int) getWidth.invoke(realms);
            int height = (int) getHeight.invoke(realms);
            int x = (int) getX.invoke(realms);
            int half = Math.max(60, width / 2 - 2);
            setWidth.invoke(realms, half);

            Object button = button("Mods", ModSettingsScreen::open);
            setX.invoke(button, x + width - half);
            setY.invoke(button, (int) getY.invoke(realms));
            setWidth.invoke(button, half);
            setHeight.invoke(button, height);
            addRenderableWidget.invoke(titleScreen, button);
            PyTrialLog.info("Split the Realms button: the right half opens the pyTrial mod settings");
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot split the Realms button: " + t);
        }
    }

    /** Opens the settings screen. */
    public static void open() {
        try {
            resolve();
            if (screenClass == null) {
                screenClass = generateScreenClass();
            }
            Object screen = screenClass.getConstructor(load(COMPONENT, false))
                    .newInstance(literal.invoke(null, "pyTrial Mod Settings"));
            setScreenAndShow.invoke(minecraft, screen);
        } catch (Throwable t) {
            PyTrialLog.error("Cannot open the mod settings screen", t);
        }
    }

    /** Builds the widgets of the generated screen. */
    public static void init(Object screen) {
        try {
            resolve();
            measure(screen);

            int buttonWidth = rightPanelWidth - PANEL_PADDING * 2;
            int x = rightPanelX + PANEL_PADDING;
            int y = leftPanelY + PANEL_PADDING + 14;

            add(screen, toggleLabel("Creative tab pages", PyTrialSettings.creativeTabPaging()),
                    PyTrialSettings::toggleCreativeTabPaging, x, y, buttonWidth);
            y += BUTTON_HEIGHT + BUTTON_GAP;
            add(screen, toggleLabel("Mod item names", PyTrialSettings.modItemNames()),
                    PyTrialSettings::toggleModItemNames, x, y, buttonWidth);
            y += BUTTON_HEIGHT + BUTTON_GAP;
            add(screen, toggleLabel("Generated resource pack", PyTrialSettings.generatedResourcePack()),
                    PyTrialSettings::toggleGeneratedResourcePack, x, y, buttonWidth);
            y += BUTTON_HEIGHT + BUTTON_GAP * 2;
            add(screen, "Open log folder", ModSettingsScreen::openLogFolder, x, y, buttonWidth);

            add(screen, "Done", ModSettingsScreen::close,
                    (viewWidth - DONE_WIDTH) / 2, viewHeight - 30, DONE_WIDTH);
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot build the mod settings screen: " + t);
        }
    }

    /** Drawn before the widgets: the dim layer and the two panels. */
    public static void renderBehind(Object screen, Object graphics) {
        try {
            resolve();
            if (graphics == null || fillMethod == null) {
                return;
            }
            measure(screen);
            fill(graphics, 0, 0, viewWidth, viewHeight, 0xC0000000);
            panel(graphics, leftPanelX, leftPanelY, leftPanelWidth, panelHeight);
            panel(graphics, rightPanelX, leftPanelY, rightPanelWidth, panelHeight);
        } catch (Throwable ignored) {
        }
    }

    /** Drawn after the widgets: everything made of text. */
    public static void renderFront(Object screen, Object graphics) {
        try {
            resolve();
            if (graphics == null || textMethod == null || minecraft == null) {
                return;
            }
            measure(screen);
            Object font = font();

            centeredText(graphics, font, "pyTrial Mod Settings", viewWidth / 2, TITLE_Y, COLOR_TITLE);
            fill(graphics, 14, SEPARATOR_Y, viewWidth - 14, SEPARATOR_Y + 1, 0x40FFFFFF);

            List<PyTrialSettings.ModEntry> entries = PyTrialSettings.modEntries();
            text(graphics, font, "Mods (" + entries.size() + ")",
                    leftPanelX + PANEL_PADDING, leftPanelY + PANEL_PADDING, COLOR_HEADER);
            text(graphics, font, "Settings",
                    rightPanelX + PANEL_PADDING, leftPanelY + PANEL_PADDING, COLOR_HEADER);

            int y = leftPanelY + PANEL_PADDING + 16;
            int bottom = leftPanelY + panelHeight - 10;
            if (entries.isEmpty()) {
                text(graphics, font, "No mods loaded", leftPanelX + PANEL_PADDING, y, COLOR_DIM);
            }
            for (PyTrialSettings.ModEntry entry : entries) {
                if (y + 22 > bottom) {
                    text(graphics, font, "...", leftPanelX + PANEL_PADDING, y, COLOR_DIM);
                    break;
                }
                text(graphics, font, entry.id(), leftPanelX + PANEL_PADDING, y, COLOR_TEXT);
                String state = entry.state();
                int color = "LOADED".equals(state) ? COLOR_OK
                        : ("FAILED".equals(state) ? COLOR_BAD : COLOR_DIM);
                text(graphics, font, state, leftPanelX + PANEL_PADDING, y + 11, color);
                if (entry.python()) {
                    text(graphics, font, "python",
                            leftPanelX + PANEL_PADDING + fontWidth(font, state) + 8, y + 11, COLOR_TAG);
                }
                y += 26;
            }

            String footer = PyTrialSettings.settingsFileName();
            if (footer != null) {
                text(graphics, font, footer,
                        leftPanelX + PANEL_PADDING, leftPanelY + panelHeight - 12, COLOR_DIM);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void measure(Object screen) {
        int width = intField(screen, screenWidthField);
        int height = intField(screen, screenHeightField);
        if (width > 0) {
            viewWidth = width;
        }
        if (height > 0) {
            viewHeight = height;
        }
        int contentWidth = Math.min(CONTENT_MAX_WIDTH, Math.max(240, viewWidth - 40));
        int left = (viewWidth - contentWidth) / 2;
        leftPanelWidth = (contentWidth - COLUMN_GAP) * 42 / 100;
        rightPanelWidth = contentWidth - COLUMN_GAP - leftPanelWidth;
        leftPanelX = left;
        rightPanelX = left + leftPanelWidth + COLUMN_GAP;
        leftPanelY = CONTENT_TOP;
        panelHeight = Math.min(150, Math.max(70, viewHeight - CONTENT_TOP - 46));
    }

    private static void panel(Object graphics, int x, int y, int width, int height) {
        fill(graphics, x, y, x + width, y + height, COLOR_PANEL);
        fill(graphics, x, y, x + width, y + 1, COLOR_BORDER);
        fill(graphics, x, y + height - 1, x + width, y + height, COLOR_BORDER);
        fill(graphics, x, y, x + 1, y + height, COLOR_BORDER);
        fill(graphics, x + width - 1, y, x + width, y + height, COLOR_BORDER);
    }

    private static void openLogFolder() {
        try {
            java.nio.file.Path folder = PyTrialSettings.logFolder();
            if (folder == null || !java.nio.file.Files.isDirectory(folder)) {
                PyTrialLog.warn("The log folder does not exist yet");
                return;
            }
            java.awt.Desktop.getDesktop().open(folder.toFile());
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot open the log folder: " + t.getMessage());
        }
    }

    private static void close() {
        try {
            resolve();
            setScreenAndShow.invoke(minecraft, parentScreen);
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot close the mod settings screen: " + t.getMessage());
        }
    }

    private static String toggleLabel(String name, boolean enabled) {
        return name + ": " + (enabled ? "ON" : "OFF");
    }

    private static void add(Object screen, String label, Runnable action, int x, int y, int width) {
        try {
            Object button = button(label, action);
            setX.invoke(button, x);
            setY.invoke(button, y);
            setWidth.invoke(button, width);
            setHeight.invoke(button, BUTTON_HEIGHT);
            addRenderableWidget.invoke(screen, button);
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot add the button " + label + ": " + t.getMessage());
        }
    }

    /** Buttons need a fresh label every time, otherwise the toggle would not show its state. */
    private static Object button(String label, Runnable action) throws Exception {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("onPress".equals(method.getName())) {
                action.run();
                // The label of a toggle lives in the button, so the screen is rebuilt to show it.
                if (parentScreen != null && !"Done".equals(label)) {
                    open();
                }
                return null;
            }
            return defaultAnswer(proxy, method, args);
        };
        Object onPress = Proxy.newProxyInstance(loader(), new Class<?>[]{load(ON_PRESS, true)}, handler);
        Object component = literal.invoke(null, label);
        Object builder = builderMethod.invoke(null, component, onPress);
        builderBounds.invoke(builder, 0, 0, 0, 0);
        return builderBuild.invoke(builder);
    }

    private static Object defaultAnswer(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "pyTrialButtonAction";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
            default -> null;
        };
    }

    private static String label(String key) {
        try {
            Object component = translatable.invoke(null, key);
            return String.valueOf(getString.invoke(component));
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }

    private static ClassLoader loader() {
        return ModSettingsScreen.class.getClassLoader();
    }

    /** The ASM constants are internal names, Class.forName wants the dotted form. */
    private static Class<?> load(String internalName, boolean initialize) throws ClassNotFoundException {
        return Class.forName(internalName.replace('/', '.'), initialize, loader());
    }

    private static Object font() throws Exception {
        return fontField.get(minecraft);
    }

    private static int intField(Object target, Field field) {
        try {
            return field == null ? 0 : field.getInt(target);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void text(Object graphics, Object font, String value, int x, int y, int color) {
        try {
            textMethod.invoke(graphics, font, value, x, y, color);
        } catch (Throwable ignored) {
        }
    }

    private static void centeredText(Object graphics, Object font, String value, int x, int y, int color) {
        try {
            centeredTextMethod.invoke(graphics, font, value, x, y, color);
        } catch (Throwable ignored) {
        }
    }

    private static void fill(Object graphics, int x1, int y1, int x2, int y2, int color) {
        try {
            fillMethod.invoke(graphics, x1, y1, x2, y2, color);
        } catch (Throwable ignored) {
        }
    }

    private static int fontWidth(Object font, String value) {
        try {
            return (int) fontWidthMethod.invoke(font, value);
        } catch (Throwable t) {
            return value.length() * 6;
        }
    }

    /** Extends Screen, which cannot be done at compile time without the game on the classpath. */
    private static Class<?> generateScreenClass() throws Exception {
        String name = "net/pytrial/ui/GeneratedModScreen";
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
        init.visitMethodInsn(Opcodes.INVOKESTATIC, "net/pytrial/ui/ModSettingsScreen", "init",
                "(Ljava/lang/Object;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor render = writer.visitMethod(Opcodes.ACC_PUBLIC, "extractRenderState",
                "(L" + GRAPHICS + ";IIF)V", null, null);
        render.visitCode();
        render.visitVarInsn(Opcodes.ALOAD, 0);
        render.visitVarInsn(Opcodes.ALOAD, 1);
        render.visitMethodInsn(Opcodes.INVOKESTATIC, "net/pytrial/ui/ModSettingsScreen", "renderBehind",
                "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
        render.visitVarInsn(Opcodes.ALOAD, 0);
        render.visitVarInsn(Opcodes.ALOAD, 1);
        render.visitVarInsn(Opcodes.ILOAD, 2);
        render.visitVarInsn(Opcodes.ILOAD, 3);
        render.visitVarInsn(Opcodes.FLOAD, 4);
        render.visitMethodInsn(Opcodes.INVOKESPECIAL, SCREEN, "extractRenderState",
                "(L" + GRAPHICS + ";IIF)V", false);
        render.visitVarInsn(Opcodes.ALOAD, 0);
        render.visitVarInsn(Opcodes.ALOAD, 1);
        render.visitMethodInsn(Opcodes.INVOKESTATIC, "net/pytrial/ui/ModSettingsScreen", "renderFront",
                "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
        render.visitInsn(Opcodes.RETURN);
        render.visitMaxs(0, 0);
        render.visitEnd();

        writer.visitEnd();
        return MethodHandles.lookup().defineClass(writer.toByteArray());
    }

    private static void resolve() {
        if (resolved || resolving) {
            return;
        }
        resolving = true;
        try {
            Class<?> screenClassType = load(SCREEN, false);
            addRenderableWidget = screenClassType.getDeclaredMethod("addRenderableWidget",
                    load(GUI_EVENT_LISTENER, false));
            try {
                addRenderableWidget.setAccessible(true);
            } catch (Throwable ignored) {
            }
            children = screenClassType.getMethod("children");
            screenWidthField = screenClassType.getField("width");
            screenHeightField = screenClassType.getField("height");

            Class<?> minecraftClass = load(MINECRAFT, true);
            minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            setScreenAndShow = minecraftClass.getMethod("setScreenAndShow", screenClassType);
            fontField = minecraftClass.getField("font");

            Class<?> widgetClass = load(WIDGET, true);
            setWidth = widgetClass.getMethod("setWidth", int.class);
            setHeight = widgetClass.getMethod("setHeight", int.class);
            setX = widgetClass.getMethod("setX", int.class);
            setY = widgetClass.getMethod("setY", int.class);
            getX = widgetClass.getMethod("getX");
            getY = widgetClass.getMethod("getY");
            getHeight = widgetClass.getMethod("getHeight");
            getWidth = widgetClass.getMethod("getWidth");
            getMessage = widgetClass.getMethod("getMessage");

            Class<?> componentClass = load(COMPONENT, true);
            translatable = componentClass.getMethod("translatable", String.class);
            literal = componentClass.getMethod("literal", String.class);
            getString = componentClass.getMethod("getString");

            Class<?> buttonClass = load(BUTTON, true);
            Class<?> builderClass = load(BUILDER, true);
            Class<?> onPressClass = load(ON_PRESS, true);
            builderMethod = buttonClass.getMethod("builder", componentClass, onPressClass);
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
            fontWidthMethod = Class.forName("net.minecraft.client.gui.Font", true, loader())
                    .getMethod("width", String.class);

            resolved = true;
        } catch (Throwable t) {
            PyTrialLog.warn("Mod settings screen is unavailable: " + t);
        } finally {
            resolving = false;
        }
    }
}
