package net.pytrial.bootstrap.launch;

import com.google.gson.JsonObject;
import net.pytrial.bootstrap.util.BootstrapLog;
import net.pytrial.bootstrap.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class VersionResolver {

    private VersionResolver() {
    }

    public static List<String> listInstalledVersions(Path gameDir) {
        Path versionsDir = gameDir.resolve("versions");
        List<String> result = new ArrayList<>();
        if (!Files.isDirectory(versionsDir)) {
            return result;
        }
        try (Stream<Path> stream = Files.list(versionsDir)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                String id = dir.getFileName().toString();
                if (Files.isRegularFile(dir.resolve(id + ".json"))) {
                    result.add(id);
                }
            });
        } catch (IOException e) {
            BootstrapLog.warn("Failed to read the versions directory: " + e.getMessage());
        }
        result.sort(String::compareToIgnoreCase);
        return result;
    }

    public static String findLatestRelease(Path gameDir) {
        Path versionsDir = gameDir.resolve("versions");
        if (!Files.isDirectory(versionsDir)) {
            return null;
        }

        String bestId = null;
        String bestTime = "";
        boolean bestVanilla = true;
        try (Stream<Path> stream = Files.list(versionsDir)) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                String id = dir.getFileName().toString();
                Path json = dir.resolve(id + ".json");
                if (!Files.isRegularFile(json)) {
                    continue;
                }
                try {
                    JsonObject root = Json.readObject(json);
                    if (root.has("inheritsFrom")) {
                        continue;
                    }
                    String type = root.has("type") ? root.get("type").getAsString() : "";
                    if (!"release".equalsIgnoreCase(type)) {
                        continue;
                    }
                    String time = root.has("releaseTime") ? root.get("releaseTime").getAsString() : "";
                    String mainClass = root.has("mainClass") ? root.get("mainClass").getAsString() : "";
                    boolean vanilla = isVanillaMainClass(mainClass);

                    if (beats(id, time, vanilla, bestId, bestTime, bestVanilla)) {
                        bestId = id;
                        bestTime = time;
                        bestVanilla = vanilla;
                    }
                } catch (RuntimeException | IOException e) {
                    BootstrapLog.debug("Skipping unparsable version: " + id);
                }
            }
        } catch (IOException e) {
            BootstrapLog.warn("Version scan failed: " + e.getMessage());
        }
        if (bestId != null && !bestVanilla) {
            BootstrapLog.warn("Version " + bestId + " is started by another mod loader, and pyTrial is a"
                    + " standalone loader; pass --version with a plain Minecraft version if the game refuses to start");
        }
        return bestId;
    }

    /**
     * Preference order: a version started by the plain client, then an id that looks like a
     * Minecraft version, then the newest release, then the plainest id. Launchers keep one folder
     * per instance, so release times alone tend to pick whatever the folder order happens to be.
     */
    private static boolean beats(String id, String time, boolean vanilla,
                                 String bestId, String bestTime, boolean bestVanilla) {
        if (bestId == null) {
            return true;
        }
        if (vanilla != bestVanilla) {
            return vanilla;
        }
        boolean versionLike = versionLike(id);
        if (versionLike != versionLike(bestId)) {
            return versionLike;
        }
        int byTime = time.compareTo(bestTime);
        if (byTime != 0) {
            return byTime > 0;
        }
        return plainness(id) < plainness(bestId);
    }

    /** True for ids like {@code 26.3} or {@code 26.3-vanilla}, false for names people typed. */
    private static boolean versionLike(String id) {
        if (id.isEmpty() || !Character.isDigit(id.charAt(0))) {
            return false;
        }
        for (int index = 0; index < id.length(); index++) {
            char c = id.charAt(index);
            if (c > 127 || !(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_')) {
                return false;
            }
        }
        return true;
    }

    /** The class a plain client declares; anything else means another loader starts the game. */
    private static final String VANILLA_MAIN_CLASS = "net.minecraft.client.main.Main";

    private static boolean isVanillaMainClass(String mainClass) {
        return mainClass.isEmpty() || VANILLA_MAIN_CLASS.equals(mainClass);
    }

    /**
     * Release ids are digits and dots, so on equal release times the plainest id wins. Launchers
     * keep per instance folders that inherit the release time of the version they were cloned
     * from, which would otherwise make the pick depend on directory order.
     */
    private static int plainness(String id) {
        int unusual = 0;
        for (int index = 0; index < id.length(); index++) {
            char c = id.charAt(index);
            if (c != '.' && (c < '0' || c > '9')) {
                unusual++;
            }
        }
        return unusual;
    }

    public static Path resolveVersionJson(Path gameDir, String versionId) {
        String id = versionId;
        if (id == null || id.isBlank()) {
            id = findLatestRelease(gameDir);
            if (id == null) {
                Path versionsDir = gameDir.resolve("versions");
                if (!Files.isDirectory(versionsDir)) {
                    throw new IllegalStateException("No versions folder under " + gameDir
                            + ": this should be the .minecraft directory, not the game's own folder");
                }
                throw new IllegalStateException("No release version found under " + versionsDir
                        + ", pass one with --version <id>. Installed versions: " + listInstalledVersions(gameDir));
            }
            BootstrapLog.info("Auto-selected version: " + id);
        }

        Path json = gameDir.resolve("versions").resolve(id).resolve(id + ".json");
        if (!Files.isRegularFile(json)) {
            throw new IllegalStateException("Version json does not exist: " + json + " (installed versions: "
                    + listInstalledVersions(gameDir) + "）");
        }
        try {
            JsonObject root = Json.readObject(json);
            if (root.has("inheritsFrom")) {
                throw new IllegalStateException("Version " + id + " inherits from "
                        + root.get("inheritsFrom").getAsString()
                        + ", it is a launcher profile and has to be started from your launcher");
            }
        } catch (IOException e) {
            BootstrapLog.warn("Cannot read the version json: " + e.getMessage());
        }
        return json;
    }

    public static VersionJson read(Path versionJson) throws IOException {
        VersionJson version = Json.PRETTY.fromJson(Files.readString(versionJson), VersionJson.class);
        if (version == null || version.mainClass == null) {
            throw new IOException("Malformed version json, missing mainClass: " + versionJson);
        }
        return version;
    }

    public static Path clientJar(Path gameDir, String versionId) {
        return gameDir.resolve("versions").resolve(versionId).resolve(versionId + ".jar");
    }

    public static Comparator<String> conventionalOrder() {
        return String::compareToIgnoreCase;
    }
}
