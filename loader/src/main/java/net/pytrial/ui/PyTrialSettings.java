package net.pytrial.ui;

import com.google.gson.JsonObject;
import net.pytrial.util.PyTrialLog;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings of the loader itself, edited from the mod settings screen and stored in
 * {@code <installDir>/settings.json}.
 */
public final class PyTrialSettings {

    private static final String FILE_NAME = "settings.json";

    /** One row of the mod list shown by the settings screen. */
    public record ModEntry(String id, String state, boolean python) {
    }

    private static Path file;
    private static Path logFolder;
    private static final List<ModEntry> MOD_ENTRIES = new ArrayList<>();

    private static boolean creativeTabPaging = true;
    private static boolean modItemNames = true;
    private static boolean generatedResourcePack = true;

    private PyTrialSettings() {
    }

    public static synchronized void attach(Path installDir) {
        if (installDir == null) {
            return;
        }
        file = installDir.resolve(FILE_NAME);
        logFolder = installDir.resolve("logs");
        load();
    }

    public static synchronized void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject root = com.google.gson.JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            creativeTabPaging = read(root, "creativeTabPaging", creativeTabPaging);
            modItemNames = read(root, "modItemNames", modItemNames);
            generatedResourcePack = read(root, "generatedResourcePack", generatedResourcePack);
            PyTrialLog.info("Loaded the pyTrial settings from " + file);
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot read " + file + ": " + t.getMessage());
        }
    }

    public static synchronized void save() {
        if (file == null) {
            return;
        }
        try {
            JsonObject root = new JsonObject();
            root.addProperty("creativeTabPaging", creativeTabPaging);
            root.addProperty("modItemNames", modItemNames);
            root.addProperty("generatedResourcePack", generatedResourcePack);
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
            PyTrialLog.info("Saved the pyTrial settings to " + file);
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot write " + file + ": " + t.getMessage());
        }
    }

    private static boolean read(JsonObject root, String key, boolean fallback) {
        return root.has(key) ? root.get(key).getAsBoolean() : fallback;
    }

    public static boolean creativeTabPaging() {
        return creativeTabPaging;
    }

    public static boolean modItemNames() {
        return modItemNames;
    }

    public static boolean generatedResourcePack() {
        return generatedResourcePack;
    }

    public static synchronized void toggleCreativeTabPaging() {
        creativeTabPaging = !creativeTabPaging;
        save();
    }

    public static synchronized void toggleModItemNames() {
        modItemNames = !modItemNames;
        save();
    }

    public static synchronized void toggleGeneratedResourcePack() {
        generatedResourcePack = !generatedResourcePack;
        save();
    }

    public static synchronized void setModEntries(List<ModEntry> entries) {
        MOD_ENTRIES.clear();
        if (entries != null) {
            MOD_ENTRIES.addAll(entries);
        }
    }

    public static synchronized List<ModEntry> modEntries() {
        return List.copyOf(MOD_ENTRIES);
    }

    public static Path logFolder() {
        return logFolder;
    }

    /** Shown at the bottom of the settings screen, for example {@code pytrial/settings.json}. */
    public static String settingsFileName() {
        if (file == null) {
            return null;
        }
        Path parent = file.getParent();
        return parent == null ? file.getFileName().toString()
                : parent.getFileName() + "/" + file.getFileName();
    }
}
