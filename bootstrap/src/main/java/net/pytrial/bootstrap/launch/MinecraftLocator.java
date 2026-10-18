package net.pytrial.bootstrap.launch;

import net.pytrial.bootstrap.util.BootstrapLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public final class MinecraftLocator {

    /** Files that only a real game directory has. */
    private static final String[] GAME_DIR_MARKERS = {
            "libraries", "assets", "options.txt", "launcher_profiles.json", "versions"
    };

    private MinecraftLocator() {
    }

    public static Path defaultGameDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Paths.get(appData, ".minecraft");
            }
            return Paths.get(home, "AppData", "Roaming", ".minecraft");
        }
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "minecraft");
        }
        return Paths.get(home, ".minecraft");
    }

    public static Path resolve(String explicitGameDir) {
        Path gameDir = explicitGameDir == null || explicitGameDir.isBlank()
                ? defaultGameDir()
                : Paths.get(explicitGameDir).toAbsolutePath();

        if (!Files.isDirectory(gameDir)) {
            BootstrapLog.warn("Game directory does not exist: " + gameDir);
            return gameDir;
        }

        if (!Files.isDirectory(gameDir.resolve("versions"))) {
            Path parent = gameDirIfVersionsFolder(gameDir);
            if (parent != null) {
                BootstrapLog.warn("Game directory is a versions folder, using the game directory above it: "
                        + parent);
                gameDir = parent;
            }
        }

        if (!Files.isDirectory(gameDir.resolve("versions"))) {
            BootstrapLog.warn("No versions folder under the game directory, is this a valid .minecraft? " + gameDir);
        }
        return gameDir;
    }

    /**
     * Recovers when a picker lands on {@code <game dir>/versions}. Minecraft keeps the per version
     * folders there and every launcher shows them, so picking that level is a common mistake; the
     * parent is only accepted when it really looks like a game directory.
     */
    private static Path gameDirIfVersionsFolder(Path dir) {
        Path parent = dir.getParent();
        if (parent == null || !looksLikeGameDir(parent)) {
            return null;
        }
        if ("versions".equalsIgnoreCase(String.valueOf(dir.getFileName()))) {
            return parent;
        }
        return containsVersionFolders(dir) ? parent : null;
    }

    private static boolean looksLikeGameDir(Path dir) {
        for (String marker : GAME_DIR_MARKERS) {
            if (Files.exists(dir.resolve(marker))) {
                return true;
            }
        }
        return false;
    }

    /** True when the directory holds {@code <id>/<id>.json} pairs, which is what versions looks like. */
    private static boolean containsVersionFolders(Path dir) {
        try (Stream<Path> children = Files.list(dir)) {
            return children.filter(Files::isDirectory).anyMatch(child -> {
                String id = child.getFileName().toString();
                return Files.isRegularFile(child.resolve(id + ".json"));
            });
        } catch (IOException e) {
            return false;
        }
    }
}
