package net.pytrial.bootstrap.install;

import net.pytrial.bootstrap.launch.MinecraftLocator;
import net.pytrial.bootstrap.launch.VersionResolver;
import net.pytrial.bootstrap.util.BootstrapLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Installation logic shared by the client installer window and its command line. */
public final class ClientInstaller {

    public static final String BUNDLED_LOADER = "pytrial/loader/pytrial-loader.jar";
    public static final String BUNDLED_LOADER_NAME = "pytrial-loader.jar";
    public static final String DIRECTORY_NAME = Installer.DIRECTORY_NAME;

    /** Receives progress of a running installation. */
    public interface Listener {

        void step(String message);

        default void done(Installer.Result result) {
        }

        default void failed(Throwable error) {
        }
    }

    /** What an installation would do, resolved before anything is written. */
    public record Preview(Path gameDir, Path installDir, Path modsDir, String versionId) {
    }

    private ClientInstaller() {
    }

    public static Path detectGameDir() {
        return detectGameDir(null);
    }

    public static Path detectGameDir(String option) {
        return MinecraftLocator.resolve(option);
    }

    public static List<String> listVersions(Path gameDir) {
        return VersionResolver.listInstalledVersions(gameDir);
    }

    public static String latestRelease(Path gameDir) {
        return VersionResolver.findLatestRelease(gameDir);
    }

    public static Preview preview(Path gameDir, String versionId, Path modsDir) {
        Path installDir = gameDir.resolve(DIRECTORY_NAME);
        Path resolvedMods = modsDir != null ? modsDir : installDir.resolve("mods");
        String version = versionId != null && !versionId.isBlank() ? versionId : latestRelease(gameDir);
        return new Preview(gameDir, installDir, resolvedMods, version);
    }

    /** Extracts the loader shipped inside this installer, or null when there is none. */
    public static Path bundledLoader() throws IOException {
        try (InputStream input = ClientInstaller.class.getClassLoader()
                .getResourceAsStream(BUNDLED_LOADER)) {
            if (input == null) {
                BootstrapLog.warn("This installer carries no bundled loader, expecting --loader");
                return null;
            }
            Path target = Files.createTempDirectory("pytrial-installer").resolve(BUNDLED_LOADER_NAME);
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
            return target;
        }
    }

    public static Installer.Result install(Path gameDir, String versionId, Path modsDir,
                                           Path loaderJar, boolean withScripts,
                                           Listener listener) throws IOException {
        Preview preview = preview(gameDir, versionId, modsDir);
        listener.step("Game directory: " + preview.gameDir());
        listener.step("Minecraft version: " + (preview.versionId() != null
                ? preview.versionId() : "latest release"));
        listener.step("Install directory: " + preview.installDir());
        listener.step("Mods directory: " + preview.modsDir());

        Path loader = loaderJar != null ? loaderJar : bundledLoader();
        if (loader != null) {
            listener.step("Loader: " + loader.getFileName());
        }

        try {
            Installer.Result result = Installer.install(
                    preview.gameDir().toString(), versionId, loader, modsDir, withScripts);
            if (result.profileId != null) {
                listener.step("Start '" + result.profileId + "' from your launcher to play");
            }
            if (result.launchScript != null) {
                listener.step("Launch script: " + result.launchScript);
            }
            listener.done(result);
            return result;
        } catch (Throwable error) {
            listener.failed(error);
            throw error;
        }
    }
}
