package net.pytrial.ui;

import net.pytrial.mapping.MappingSet;
import net.pytrial.util.PyTrialLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class SplashRenderer {

    private static final String OVERLAY = "net.minecraft.client.gui.screens.LoadingOverlay";
    private static final String MINECRAFT = "net.minecraft.client.Minecraft";
    private static final String GUI_GRAPHICS = "net.minecraft.client.gui.GuiGraphics";
    private static final String GET_INSTANCE_DESCRIPTOR = "()Lnet/minecraft/client/Minecraft;";
    private static final String DRAW_STRING_DESCRIPTOR =
            "(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I";
    private static final String WIDTH_DESCRIPTOR = "(Ljava/lang/String;)I";
    private static final String CENTERED_TEXT = "centeredText";
    private static final String[] TEXT_METHODS = {CENTERED_TEXT, "drawString"};
    private static final String GUI_WIDTH_METHOD = "guiWidth";
    private static final String GUI_HEIGHT_METHOD = "guiHeight";

    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int WARN_COLOR = 0xFFFFD166;
    private static final int ERROR_COLOR = 0xFFFF6B6B;
    private static final float PROGRESS_BAR_HEIGHT_RATIO = 0.8325f;
    private static final int LAST_LINE_OFFSET = 18;
    private static final int LINE_HEIGHT = 10;
    private static final int MARGIN = 8;
    private static final String ELLIPSIS = "...";

    private final MappingSet mappings;

    private volatile boolean initialized;
    private volatile boolean textUsable;
    private volatile boolean headLogged;
    private Object minecraftInstance;
    private Field fontField;
    private Class<?> fontClass;
    private Method fontWidth;
    private Method textMethod;
    private Method guiWidth;
    private Method guiHeight;
    private boolean centered;

    public SplashRenderer(MappingSet mappings) {
        this.mappings = mappings;
    }

    public boolean isUsable() {
        return textUsable;
    }

    public void render(Object overlay, Object graphics, int mouseX, int mouseY) {
        if (overlay == null) {
            return;
        }
        try {
            if (!initialized) {
                initialize(overlay);
            }
            drawLog(graphics);
        } catch (Throwable ignored) {
        }
    }

    private synchronized void initialize(Object overlay) {
        initialized = true;
        initializeText(overlay.getClass().getClassLoader());
    }

    private void initializeText(ClassLoader loader) {
        try {
            Class<?> minecraftClass = load(loader, MINECRAFT);
            MappingSet.MethodRef getInstance = mappings.obfMethod(MINECRAFT, "getInstance", GET_INSTANCE_DESCRIPTOR);
            if (getInstance == null) {
                return;
            }
            Method getter = minecraftClass.getDeclaredMethod(getInstance.name());
            getter.setAccessible(true);
            minecraftInstance = getter.invoke(null);
            if (minecraftInstance == null) {
                return;
            }

            String fontFieldName = mappings.obfFieldName(MINECRAFT, "font");
            if (fontFieldName == null) {
                return;
            }
            fontField = minecraftClass.getDeclaredField(fontFieldName);
            fontField.setAccessible(true);
            fontClass = fontField.getType();

            MappingSet.MethodRef widthRef = mappings.obfMethod(fontClass.getName(), "width", WIDTH_DESCRIPTOR);
            if (widthRef != null) {
                fontWidth = fontClass.getDeclaredMethod(widthRef.name(), String.class);
                fontWidth.setAccessible(true);
            }
        } catch (Throwable t) {
            PyTrialLog.warn("Loading screen text bridge init failed: " + t.getMessage());
        }
    }

    private void drawLog(Object graphics) {
        if (graphics == null || minecraftInstance == null || fontField == null) {
            return;
        }
        try {
            if (textMethod == null && !resolveTextMethod(graphics.getClass())) {
                return;
            }
            List<PyTrialLog.Entry> entries = PyTrialLog.history();
            if (entries.isEmpty()) {
                return;
            }
            Object font = fontField.get(minecraftInstance);
            if (font == null) {
                return;
            }
            int width = dimension(guiWidth, graphics);
            int height = dimension(guiHeight, graphics);
            if (width <= 0 || height <= 0) {
                return;
            }
            if (!headLogged) {
                headLogged = true;
                PyTrialLog.info("Loading screen text at " + width + "x" + height + ", showing the latest line only");
            }
            int available = Math.max(16, width - MARGIN * 2);
            int y = Math.max(LINE_HEIGHT, (int) (height * PROGRESS_BAR_HEIGHT_RATIO) - LAST_LINE_OFFSET);
            drawLine(graphics, font, entries.get(entries.size() - 1), width, y, available);
        } catch (Throwable ignored) {
        }
    }

    private void drawLine(Object graphics, Object font, PyTrialLog.Entry entry, int width, int y, int available) {
        String text = fit(font, sanitize(entry.text()), available);
        int color = colorFor(entry.level());
        try {
            if (centered) {
                textMethod.invoke(graphics, font, text, width / 2, y, color);
            } else {
                textMethod.invoke(graphics, font, text, Math.max(MARGIN, (width - measure(font, text)) / 2), y, color);
            }
        } catch (Throwable ignored) {
        }
    }

    private static int colorFor(String level) {
        return switch (level) {
            case "WARN" -> WARN_COLOR;
            case "ERROR" -> ERROR_COLOR;
            default -> TEXT_COLOR;
        };
    }

    private static String sanitize(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            builder.append(character >= ' ' && character <= '~' ? character : '?');
        }
        return builder.toString();
    }

    private String fit(Object font, String text, int available) {
        if (measure(font, text) <= available) {
            return text;
        }
        String candidate = text;
        while (candidate.length() > ELLIPSIS.length()
                && measure(font, candidate + ELLIPSIS) > available) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate + ELLIPSIS;
    }

    private synchronized boolean resolveTextMethod(Class<?> graphicsClass) {
        try {
            guiWidth = graphicsClass.getMethod(GUI_WIDTH_METHOD);
            guiHeight = graphicsClass.getMethod(GUI_HEIGHT_METHOD);
        } catch (Throwable t) {
            PyTrialLog.warn("Loading screen size accessors unavailable: " + t.getMessage());
        }
        for (String candidate : candidates()) {
            try {
                textMethod = graphicsClass.getMethod(candidate, fontClass, String.class,
                        int.class, int.class, int.class);
                centered = CENTERED_TEXT.equals(candidate);
                textUsable = true;
                PyTrialLog.info("Loading screen text method: " + graphicsClass.getSimpleName() + "#" + candidate);
                return true;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return false;
    }

    private List<String> candidates() {
        List<String> names = new ArrayList<>();
        MappingSet.MethodRef mapped = mappings.obfMethod(GUI_GRAPHICS, "drawString", DRAW_STRING_DESCRIPTOR);
        if (mapped != null && !mapped.name().equals("drawString")) {
            names.add(mapped.name());
        }
        for (String name : TEXT_METHODS) {
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private Class<?> load(ClassLoader loader, String officialName) throws ClassNotFoundException {
        return Class.forName(mappings.obfClassName(officialName).replace('/', '.'), false, loader);
    }

    private int dimension(Method method, Object graphics) {
        if (method == null) {
            return 0;
        }
        try {
            Object value = method.invoke(graphics);
            return value instanceof Integer size ? size : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private int measure(Object font, String text) {
        if (fontWidth == null) {
            return text.length() * 6;
        }
        try {
            Object result = fontWidth.invoke(font, text);
            return result instanceof Integer value ? value : text.length() * 6;
        } catch (Throwable t) {
            return text.length() * 6;
        }
    }
}
