package net.pytrial.ui;

import net.pytrial.util.PyTrialLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Paging for the creative inventory tab bar.
 *
 * <p>Vanilla holds seven tab slots per row, one row above and one below the panel, and vanilla
 * already fills both rows. Mod tabs therefore live on further pages, seven slots per row. A button
 * row above the tab strip switches the page, and the left and right arrow keys work as well. Tabs of
 * the other pages are moved off screen by {@link #slot(Object, int)}, which keeps rendering, hovering
 * and clicking correct without touching those methods.
 */
public final class CreativeTabPages {

    public static final int SLOTS_PER_ROW = 7;
    public static final int OFFSCREEN_SLOT = 4096;

    private static final String CREATIVE_MODE_TABS = "net.minecraft.world.item.CreativeModeTabs";
    private static final String CREATIVE_MODE_TAB = "net.minecraft.world.item.CreativeModeTab";
    private static final String MINECRAFT = "net.minecraft.client.Minecraft";
    private static final String GLFW = "org.lwjgl.glfw.GLFW";

    private static final String COLUMN = "column";
    private static final String TABS = "tabs";
    private static final String KEY = "key";
    private static final String GET_INSTANCE = "getInstance";
    private static final String TEXT = "centeredText";
    private static final String FILL = "fill";
    private static final String WIDTH = "width";

    private static final int LEFT_FALLBACK = 263;
    private static final int RIGHT_FALLBACK = 262;

    private static final int BUTTON_WIDTH = 22;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 6;
    private static final int ROW_ABOVE_STRIP = 54;
    private static final int TEXT_INSET = 5;

    private static final int COLOR_BUTTON = 0xC0101010;
    private static final int COLOR_BUTTON_HOVERED = 0xC060A0D0;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private static volatile int page;
    private static volatile boolean announced;

    private static Method tabsMethod;
    private static Method columnMethod;
    private static Method keyMethod;
    private static Method fillMethod;
    private static Method textMethod;
    private static Method widthMethod;
    private static Method eventXMethod;
    private static Method eventYMethod;
    private static Object font;
    private static boolean fontResolved;
    private static boolean graphicsResolved;

    private CreativeTabPages() {
    }

    private record Rect(int x1, int y1, int x2, int y2) {

        int centerX() {
            return (x1 + x2) / 2;
        }

        boolean contains(double x, double y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    /** Called from the screen constructor: every creative screen starts on the first page. */
    public static void reset() {
        page = 0;
    }

    public static int page() {
        return page;
    }

    public static void setPage(int value) {
        int pages = totalPages();
        int clamped = Math.max(0, Math.min(pages - 1, value));
        if (clamped == page) {
            return;
        }
        page = clamped;
        PyTrialLog.info("Creative tab page: " + (clamped + 1) + "/" + pages);
    }

    /**
     * Replaces the column used by {@code getTabX}: the slot inside the current page, or an
     * off-screen slot when the tab belongs to another page.
     */
    public static int slot(Object tab, int column) {
        if (column / SLOTS_PER_ROW != page) {
            return OFFSCREEN_SLOT;
        }
        return column % SLOTS_PER_ROW;
    }

    public static void onKey(Object event) {
        if (event == null || totalPages() < 2) {
            return;
        }
        int key = readKey(event);
        if (key == glfwKey("GLFW_KEY_LEFT", LEFT_FALLBACK)) {
            setPage(page <= 0 ? totalPages() - 1 : page - 1);
        } else if (key == glfwKey("GLFW_KEY_RIGHT", RIGHT_FALLBACK)) {
            setPage(page + 1 >= totalPages() ? 0 : page + 1);
        }
    }

    /** Called before the screen handles a click: the page buttons sit outside the panel. */
    public static void onClick(Object screen, Object event) {
        if (screen == null || event == null || totalPages() < 2) {
            return;
        }
        try {
            double x = readCoordinate(event);
            double y = readCoordinate(event, true);
            if (previousButton(screen).contains(x, y)) {
                setPage(page <= 0 ? totalPages() - 1 : page - 1);
            } else if (nextButton(screen).contains(x, y)) {
                setPage(page + 1 >= totalPages() ? 0 : page + 1);
            }
        } catch (Throwable ignored) {
        }
    }

    public static int totalPages() {
        if (!PyTrialSettings.creativeTabPaging()) {
            return 1;
        }
        int highest = 0;
        for (Object tab : tabs()) {
            int column = column(tab);
            if (column > highest) {
                highest = column;
            }
        }
        return highest / SLOTS_PER_ROW + 1;
    }

    /** Called from the screen render: draws the page buttons above the tab strip. */
    public static void draw(Object graphics, Object screen, int mouseX, int mouseY) {
        if (graphics == null || screen == null) {
            return;
        }
        int pages = totalPages();
        if (pages < 2) {
            return;
        }
        try {
            resolveFont();
            resolveGraphics(graphics);
            if (fillMethod == null || textMethod == null || font == null) {
                return;
            }

            Rect previous = previousButton(screen);
            Rect next = nextButton(screen);
            Rect label = labelBox(screen);
            int textY = previous.y1() + TEXT_INSET;

            fillMethod.invoke(graphics, previous.x1(), previous.y1(), previous.x2(), previous.y2(),
                    previous.contains(mouseX, mouseY) ? COLOR_BUTTON_HOVERED : COLOR_BUTTON);
            fillMethod.invoke(graphics, next.x1(), next.y1(), next.x2(), next.y2(),
                    next.contains(mouseX, mouseY) ? COLOR_BUTTON_HOVERED : COLOR_BUTTON);
            textMethod.invoke(graphics, font, "<", previous.centerX(), textY, COLOR_TEXT);
            textMethod.invoke(graphics, font, ">", next.centerX(), textY, COLOR_TEXT);
            textMethod.invoke(graphics, font, "Page " + (page + 1) + "/" + pages,
                    label.centerX(), textY, COLOR_TEXT);

            if (!announced) {
                announced = true;
                PyTrialLog.info("Creative tab paging ready: " + pages + " page(s)");
            }
        } catch (Throwable ignored) {
        }
    }

    private static Rect previousButton(Object screen) {
        return button(screen, 0);
    }

    private static Rect nextButton(Object screen) {
        return button(screen, 1);
    }

    private static Rect button(Object screen, int index) {
        int labelWidth = labelWidth();
        int total = BUTTON_WIDTH * 2 + BUTTON_GAP * 2 + labelWidth;
        int start = fieldValue(screen, "leftPos")
                + Math.max(0, (fieldValue(screen, "imageWidth") - total) / 2);
        int x = start + index * (BUTTON_WIDTH + BUTTON_GAP + labelWidth + BUTTON_GAP);
        int y = fieldValue(screen, "topPos") - ROW_ABOVE_STRIP;
        return new Rect(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT);
    }

    private static Rect labelBox(Object screen) {
        Rect previous = previousButton(screen);
        int x = previous.x2() + BUTTON_GAP;
        return new Rect(x, previous.y1(), x + labelWidth(), previous.y2());
    }

    private static int labelWidth() {
        return Math.max(BUTTON_WIDTH, measure("Page 8/8"));
    }

    private static int measure(String text) {
        try {
            if (widthMethod != null && font != null) {
                Object value = widthMethod.invoke(font, text);
                return value instanceof Integer width ? width : 0;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static List<?> tabs() {
        try {
            ClassLoader loader = CreativeTabPages.class.getClassLoader();
            if (tabsMethod == null) {
                Class<?> tabsClass = Class.forName(CREATIVE_MODE_TABS, false, loader);
                Class<?> tabClass = Class.forName(CREATIVE_MODE_TAB, false, loader);
                tabsMethod = tabsClass.getMethod(TABS);
                columnMethod = tabClass.getMethod(COLUMN);
            }
            Object value = tabsMethod.invoke(null);
            return value instanceof List<?> list ? list : List.of();
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static int column(Object tab) {
        try {
            if (columnMethod == null) {
                return 0;
            }
            Object value = columnMethod.invoke(tab);
            return value instanceof Integer column ? column : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int readKey(Object event) {
        try {
            if (keyMethod == null) {
                keyMethod = event.getClass().getMethod(KEY);
            }
            Object value = keyMethod.invoke(event);
            return value instanceof Integer key ? key : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static double readCoordinate(Object event) {
        return readCoordinate(event, false);
    }

    private static double readCoordinate(Object event, boolean vertical) {
        try {
            Method method = vertical ? eventYMethod : eventXMethod;
            if (method == null) {
                method = event.getClass().getMethod(vertical ? "y" : "x");
                if (vertical) {
                    eventYMethod = method;
                } else {
                    eventXMethod = method;
                }
            }
            Object value = method.invoke(event);
            return value instanceof Double number ? number : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int glfwKey(String name, int fallback) {
        try {
            return Class.forName(GLFW, false, CreativeTabPages.class.getClassLoader())
                    .getField(name).getInt(null);
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static void resolveFont() {
        if (fontResolved) {
            return;
        }
        fontResolved = true;
        try {
            ClassLoader loader = CreativeTabPages.class.getClassLoader();
            Class<?> minecraftClass = Class.forName(MINECRAFT, false, loader);
            Object minecraft = minecraftClass.getMethod(GET_INSTANCE).invoke(null);
            font = minecraftClass.getField("font").get(minecraft);
            if (font != null) {
                widthMethod = font.getClass().getMethod(WIDTH, String.class);
            }
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot resolve the creative tab page font: " + t.getMessage());
        }
    }

    private static void resolveGraphics(Object graphics) {
        resolveFont();
        if (graphicsResolved) {
            return;
        }
        graphicsResolved = true;
        try {
            for (Method candidate : graphics.getClass().getMethods()) {
                if (FILL.equals(candidate.getName()) && candidate.getParameterCount() == 5
                        && candidate.getParameterTypes()[0] == int.class) {
                    fillMethod = candidate;
                    continue;
                }
                if (!TEXT.equals(candidate.getName()) || candidate.getParameterCount() != 5) {
                    continue;
                }
                Class<?>[] parameters = candidate.getParameterTypes();
                if (parameters[1] == String.class && parameters[2] == int.class) {
                    textMethod = candidate;
                }
            }
            if (fillMethod == null || textMethod == null) {
                PyTrialLog.warn("No usable fill/text method on " + graphics.getClass().getName());
            }
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot resolve the creative tab page buttons: " + t.getMessage());
        }
    }

    private static int fieldValue(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(target);
                return value instanceof Integer number ? number : 0;
            } catch (NoSuchFieldException ignored) {
                continue;
            } catch (Throwable t) {
                return 0;
            }
        }
        return 0;
    }
}
