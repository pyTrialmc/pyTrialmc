package net.pytrial;

import net.pytrial.util.PyTrialLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PyTrialEnvironment {

    public static final String DEFAULT_MAIN_CLASS = "net.minecraft.client.main.Main";

    private final Path gameDir;
    private final Path installDir;
    private final Path modsDir;
    private final Path configDir;
    private final Path cacheDir;
    private final Path loaderJar;
    private final String minecraftVersion;
    private final String mainClass;

    private PyTrialEnvironment(Path gameDir, Path modsDir, Path loaderJar,
                               String minecraftVersion, String mainClass) {
        this.gameDir = gameDir;
        this.installDir = gameDir.resolve(PyTrial.INSTALL_DIR);
        this.modsDir = modsDir != null ? modsDir : installDir.resolve("mods");
        this.configDir = installDir.resolve("config");
        this.cacheDir = installDir.resolve("cache");
        this.loaderJar = loaderJar;
        this.minecraftVersion = minecraftVersion;
        this.mainClass = mainClass != null && !mainClass.isBlank() ? mainClass : DEFAULT_MAIN_CLASS;
    }

    public static PyTrialEnvironment fromAgentArgs(String agentArgs) {
        Map<String, String> args = parseAgentArgs(agentArgs);

        Path gameDir = Path.of(option(args, "game-dir", "pytrial.gameDir", defaultGameDir()));
        Path modsDir = optionalPath(args, "mods", "pytrial.modsDir").orElse(null);
        Path loaderJar = optionalPath(args, "loader-jar", "pytrial.loaderJar").orElse(null);
        String version = option(args, "version", "pytrial.version", null);
        String mainClass = option(args, "main-class", "pytrial.mainClass", DEFAULT_MAIN_CLASS);

        return new PyTrialEnvironment(gameDir.toAbsolutePath(), modsDir, loaderJar, version, mainClass);
    }

    public void prepare() {
        for (Path dir : new Path[]{installDir, modsDir, configDir, cacheDir}) {
            try {
                Files.createDirectories(dir);
            } catch (Exception e) {
                PyTrialLog.warn("Failed to create directory: " + dir + " (" + e.getMessage() + ")");
            }
        }
    }

    public Path gameDir() {
        return gameDir;
    }

    public Path installDir() {
        return installDir;
    }

    public Path modsDir() {
        return modsDir;
    }

    public Path configDir() {
        return configDir;
    }

    public Path pythonLibraryDir() {
        return installDir.resolve("python");
    }

    public Path cacheDir() {
        return cacheDir;
    }

    public Path loaderJar() {
        return loaderJar;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public String mainClass() {
        return mainClass;
    }

    public Path configDirOf(String modId) {
        return configDir.resolve(modId);
    }

    private static String defaultGameDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return appData != null ? Path.of(appData, ".minecraft").toString()
                    : Path.of(home, "AppData", "Roaming", ".minecraft").toString();
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "minecraft").toString();
        }
        return Path.of(home, ".minecraft").toString();
    }

    private static Map<String, String> parseAgentArgs(String agentArgs) {
        Map<String, String> result = new LinkedHashMap<>();
        if (agentArgs == null || agentArgs.isBlank()) {
            return result;
        }
        for (String token : agentArgs.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("--")) {
                trimmed = trimmed.substring(2);
            }
            int eq = trimmed.indexOf('=');
            if (eq < 0) {
                result.put(trimmed, "true");
            } else {
                result.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        }
        return result;
    }

    private static String option(Map<String, String> args, String agentKey, String propertyKey, String fallback) {
        String value = args.get(agentKey);
        if (value == null || value.isBlank()) {
            value = System.getProperty(propertyKey);
        }
        return value != null && !value.isBlank() ? value : fallback;
    }

    private static Optional<Path> optionalPath(Map<String, String> args, String agentKey, String propertyKey) {
        String value = option(args, agentKey, propertyKey, null);
        return value == null ? Optional.empty() : Optional.of(Path.of(value));
    }
}
