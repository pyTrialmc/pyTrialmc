package net.pytrial.ui;

import net.pytrial.util.PyTrialLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Display names of mod content.
 *
 * <p>Vanilla resolves an item name through the language table, so a mod block would show its raw key
 * ({@code item.pymod.lucky_block}) until it is translated. The loader keeps the names declared by the
 * mods and writes them into the client language table, which is re-filled on every resource reload.
 */
public final class ModTranslations {

    private static final String LANGUAGE = "net.minecraft.locale.Language";
    private static final String STORAGE = "storage";

    private static final Map<String, String> ENTRIES = new LinkedHashMap<>();

    private static Object appliedTo;
    private static boolean announced;

    private ModTranslations() {
    }

    /** Registers the display name of one item and of its block form. */
    public static void put(String namespace, String path, String display) {
        if (namespace == null || path == null || path.isBlank()) {
            return;
        }
        String name = display == null || display.isBlank() ? prettify(path) : display;
        ENTRIES.put("item." + namespace + "." + path, name);
        ENTRIES.put("block." + namespace + "." + path, name);
        appliedTo = null;
    }

    /** The declared name of a translation key, or null. */
    public static String name(String key) {
        return key == null ? null : ENTRIES.get(key);
    }

    /** "lucky_block" becomes "Lucky Block". */
    public static String prettify(String path) {
        StringBuilder result = new StringBuilder();
        for (String part : path.split("[_./-]")) {
            if (part.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                result.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return result.isEmpty() ? path : result.toString();
    }

    public static int size() {
        return ENTRIES.size();
    }

    /** Writes the collected names into the language table of the client. */
    public static synchronized void applyAll() {
        if (ENTRIES.isEmpty()) {
            return;
        }
        try {
            Object language = currentLanguage();
            if (language == null || language == appliedTo) {
                return;
            }
            Object storage = storageOf(language);
            if (!(storage instanceof Map<?, ?>)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, String> strings = (Map<String, String>) storage;
            strings.putAll(ENTRIES);
            appliedTo = language;
            if (!announced) {
                announced = true;
                PyTrialLog.info("Injected " + ENTRIES.size() + " name entries into the language table");
            }
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot inject the mod names: " + t.getMessage());
        }
    }

    private static Object currentLanguage() throws Exception {
        Class<?> type = Class.forName(LANGUAGE, false, ModTranslations.class.getClassLoader());
        try {
            Method getInstance = type.getMethod("getInstance");
            return getInstance.invoke(null);
        } catch (NoSuchMethodException e) {
            return type.getField("instance").get(null);
        }
    }

    private static Object storageOf(Object language) throws Exception {
        for (Class<?> type = language.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(STORAGE);
                field.setAccessible(true);
                return field.get(language);
            } catch (NoSuchFieldException ignored) {
                continue;
            }
        }
        return null;
    }
}
