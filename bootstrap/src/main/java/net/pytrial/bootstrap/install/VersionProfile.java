package net.pytrial.bootstrap.install;

import com.google.gson.JsonObject;
import net.pytrial.bootstrap.launch.VersionResolver;
import net.pytrial.bootstrap.util.BootstrapLog;
import net.pytrial.bootstrap.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Installs pyTrial as a launcher version profile, the way the Fabric and Forge installers do.
 *
 * <p>The profile inherits the vanilla version and adds the loader as a {@code -javaagent}, so the
 * main class stays {@code net.minecraft.client.main.Main}. After installation the version shows up
 * in the launcher version list and the game is started from there, without any script.
 */
public final class VersionProfile {

    public static final String GROUP = "net.pytrial";
    public static final String ARTIFACT = "pytrial-loader";
    public static final String GROUP_PATH = "net/pytrial";
    public static final String FALLBACK_VERSION = "0.1.0-SNAPSHOT";
    public static final String FALLBACK_MAIN_CLASS = "net.minecraft.client.main.Main";

    public record Result(String id, Path json, Path library) {
    }

    private VersionProfile() {
    }

    public static Result install(Path gameDir, String minecraftVersion, Path installedLoader, Path modsDir)
            throws IOException {
        String loaderVersion = readVersion(installedLoader);
        Path library = installedLoader;

        String id = ARTIFACT + "-" + loaderVersion + "-" + minecraftVersion;
        Path versionDir = gameDir.resolve("versions").resolve(id);
        Files.createDirectories(versionDir);
        Path json = versionDir.resolve(id + ".json");

        String mainClass = readMainClass(gameDir, minecraftVersion);
        String time = readReleaseTime(gameDir, minecraftVersion);

        List<Object> jvm = new ArrayList<>();
        jvm.add("-Dpytrial.gameDir=" + gameDir);
        jvm.add("-Dpytrial.modsDir=" + modsDir);
        jvm.add("-Dpytrial.version=" + minecraftVersion);
        jvm.add("-javaagent:" + library + "="
                + "--game-dir=" + gameDir
                + ",--mods=" + modsDir
                + ",--version=" + minecraftVersion
                + ",--main-class=" + mainClass);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", id);
        profile.put("inheritsFrom", minecraftVersion);
        profile.put("type", "release");
        profile.put("time", time);
        profile.put("releaseTime", time);
        profile.put("mainClass", mainClass);
        profile.put("arguments", Map.of("jvm", jvm, "game", List.of()));
        Json.write(json, profile);

        BootstrapLog.info("Launcher profile: " + id);
        BootstrapLog.info("  version json: " + json);
        BootstrapLog.info("  loader agent: " + library);
        return new Result(id, json, library);
    }

    /** Reads the loader version from the jar manifest so the maven path stays exact. */
    private static String readVersion(Path loaderJar) {
        if (loaderJar == null) {
            return FALLBACK_VERSION;
        }
        try (JarFile jar = new JarFile(loaderJar.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String version = manifest.getMainAttributes().getValue("Implementation-Version");
                if (version != null && !version.isBlank()) {
                    return version;
                }
            }
        } catch (IOException e) {
            BootstrapLog.warn("Cannot read the loader version: " + e.getMessage());
        }
        return FALLBACK_VERSION;
    }

    /** Keeps the profile from looking newer than the version it inherits. */
    private static String readReleaseTime(Path gameDir, String minecraftVersion) {
        try {
            Path parent = VersionResolver.resolveVersionJson(gameDir, minecraftVersion);
            JsonObject root = Json.readObject(parent);
            if (root.has("releaseTime") && !root.get("releaseTime").isJsonNull()) {
                return root.get("releaseTime").getAsString();
            }
        } catch (RuntimeException | IOException e) {
            BootstrapLog.warn("Cannot read the release time of " + minecraftVersion + ": " + e.getMessage());
        }
        return Instant.now().toString();
    }

    private static String readMainClass(Path gameDir, String minecraftVersion) {
        try {
            Path parent = VersionResolver.resolveVersionJson(gameDir, minecraftVersion);
            JsonObject root = Json.readObject(parent);
            if (root.has("mainClass") && !root.get("mainClass").isJsonNull()) {
                return root.get("mainClass").getAsString();
            }
        } catch (RuntimeException | IOException e) {
            BootstrapLog.warn("Cannot read the main class of " + minecraftVersion + ": " + e.getMessage());
        }
        return FALLBACK_MAIN_CLASS;
    }
}
