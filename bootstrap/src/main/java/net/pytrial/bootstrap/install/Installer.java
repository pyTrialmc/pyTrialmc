package net.pytrial.bootstrap.install;

import net.pytrial.bootstrap.launch.LoaderLocator;
import net.pytrial.bootstrap.launch.MinecraftLocator;
import net.pytrial.bootstrap.launch.VersionResolver;
import net.pytrial.bootstrap.util.BootstrapLog;
import net.pytrial.bootstrap.util.Json;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class Installer {

    public static final String DIRECTORY_NAME = "pytrial";

    public static final class Result {
        public final Path installDir;
        public final Path loaderJar;
        public final Path bootstrapJar;
        public final Path modsDir;
        public final Path launchScript;
        public final String profileId;
        public final Path profileJson;

        Result(Path installDir, Path loaderJar, Path bootstrapJar, Path modsDir, Path launchScript,
               String profileId, Path profileJson) {
            this.installDir = installDir;
            this.loaderJar = loaderJar;
            this.bootstrapJar = bootstrapJar;
            this.modsDir = modsDir;
            this.launchScript = launchScript;
            this.profileId = profileId;
            this.profileJson = profileJson;
        }
    }

    private Installer() {
    }

    public static Result install(String gameDirOption, String versionId,
                                 Path loaderJar, Path modsDirOption, boolean withScripts) throws IOException {
        Path gameDir = MinecraftLocator.resolve(gameDirOption);
        Path installDir = gameDir.resolve(DIRECTORY_NAME);

        Path loaderTarget = installLoader(installDir, loaderJar);
        Path bootstrapTarget = withScripts ? installBootstrap(installDir) : null;
        Path modsDir = modsDirOption != null ? modsDirOption : installDir.resolve("mods");

        Files.createDirectories(modsDir);
        Files.createDirectories(installDir.resolve("config"));
        Files.createDirectories(installDir.resolve("cache"));

        MappingDownloader.ensure(gameDir, installDir, versionId);

        if (withScripts) {
            writeScripts(gameDir, installDir, loaderTarget, bootstrapTarget, modsDir);
        } else {
            removeObsoleteLaunchers(installDir);
        }
        writeManifest(gameDir, installDir, versionId, loaderTarget, bootstrapTarget, modsDir);

        String minecraftVersion = versionId != null && !versionId.isBlank()
                ? versionId
                : VersionResolver.findLatestRelease(gameDir);
        VersionProfile.Result profile = null;
        if (loaderTarget != null && minecraftVersion != null) {
            profile = VersionProfile.install(gameDir, minecraftVersion, loaderTarget, modsDir);
        } else {
            BootstrapLog.warn("No launcher profile created (loader jar or Minecraft version missing)");
        }

        BootstrapLog.info("Install complete: " + installDir);
        return new Result(installDir, loaderTarget, bootstrapTarget, modsDir,
                withScripts ? installDir.resolve(isWindows() ? "launch.cmd" : "launch.sh") : null,
                profile != null ? profile.id() : null,
                profile != null ? profile.json() : null);
    }

    /**
     * Drops the launcher wrappers written by older installs: the version profile is started from the
     * launcher itself, so nothing has to sit inside the game directory. Files that are locked (the
     * running installer jar) are reported and left alone.
     */
    private static void removeObsoleteLaunchers(Path installDir) {
        for (String name : new String[]{"launch.cmd", "launch.sh"}) {
            Path script = installDir.resolve(name);
            if (!Files.isRegularFile(script)) {
                continue;
            }
            try {
                Files.delete(script);
                BootstrapLog.info("Removed the old launcher script: " + script);
            } catch (IOException e) {
                BootstrapLog.warn("Cannot remove " + script + ": " + e.getMessage());
            }
        }

        Path bootstrapDir = installDir.resolve("bootstrap");
        if (!Files.isDirectory(bootstrapDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(bootstrapDir)) {
            for (Path entry : stream.filter(Files::isRegularFile).toList()) {
                try {
                    Files.delete(entry);
                    BootstrapLog.info("Removed the installed launcher copy: " + entry.getFileName());
                } catch (IOException e) {
                    BootstrapLog.warn("Cannot remove " + entry + " (it may be the running jar)");
                }
            }
        } catch (IOException e) {
            BootstrapLog.warn("Cannot read " + bootstrapDir + ": " + e.getMessage());
        }
        try (Stream<Path> stream = Files.list(bootstrapDir)) {
            if (stream.findAny().isEmpty()) {
                Files.delete(bootstrapDir);
            }
        } catch (IOException ignored) {
        }
    }

    private static Path installLoader(Path installDir, Path loaderJar) throws IOException {
        Path loaderDir = installDir.resolve("loader");
        Files.createDirectories(loaderDir);
        if (loaderJar == null) {
            BootstrapLog.warn("No loader jar provided, skipping loader installation");
            return null;
        }
        Path target = loaderDir.resolve(loaderJar.getFileName().toString());
        Files.copy(loaderJar, target, StandardCopyOption.REPLACE_EXISTING);
        BootstrapLog.info("Installed loader: " + target);
        return target;
    }

    private static Path installBootstrap(Path installDir) throws IOException {
        Path self = currentJar();
        if (self == null) {
            BootstrapLog.warn("Running from classes, skipping bootstrap jar installation (run gradle dist first)");
            return null;
        }
        Path bootstrapDir = installDir.resolve("bootstrap");
        Files.createDirectories(bootstrapDir);
        Path target = bootstrapDir.resolve(self.getFileName().toString());
        if (!self.equals(target)) {
            Files.copy(self, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static void writeScripts(Path gameDir, Path installDir, Path loaderJar,
                                     Path bootstrapJar, Path modsDir) throws IOException {
        String bin = Path.of(System.getProperty("java.home"), "bin").toString();
        String windowsJava = Path.of(bin, "javaw.exe").toString();
        String unixJava = Path.of(bin, "java").toString();
        String bootstrap = bootstrapJar != null
                ? bootstrapJar.toString()
                : "<path-to>/pytrial-bootstrap.jar";

        String command = "-jar \"" + bootstrap + "\" launch"
                + " --game-dir \"" + gameDir + "\""
                + " --mods \"" + modsDir + "\""
                + (loaderJar != null ? " --loader \"" + loaderJar + "\"" : "");

        Path logDir = installDir.resolve("logs");
        Path log = logDir.resolve("launch.log");

        Path cmd = installDir.resolve("launch.cmd");
        Files.writeString(cmd, "@echo off\r\n"
                + "setlocal\r\n"
                + "if not exist \"" + logDir + "\" mkdir \"" + logDir + "\"\r\n"
                + "rem PYTRIAL_JAVA overrides the runtime used to start the game\r\n"
                + "set \"JAVA_EXE=javaw\"\r\n"
                + "if defined PYTRIAL_JAVA set \"JAVA_EXE=%PYTRIAL_JAVA%\"\r\n"
                + "if not defined PYTRIAL_JAVA if exist \"" + windowsJava + "\" set \"JAVA_EXE="
                + windowsJava + "\"\r\n"
                + "\"%JAVA_EXE%\" " + command + " %* >> \"" + log + "\" 2>&1\r\n"
                + "if errorlevel 1 (\r\n"
                + "  echo.\r\n"
                + "  echo pyTrial launcher failed, last lines of " + log + ":\r\n"
                + "  powershell -NoProfile -Command \"Get-Content -LiteralPath '" + log + "' -Tail 25\"\r\n"
                + "  pause\r\n"
                + "  exit /b 1\r\n"
                + ")\r\n", StandardCharsets.UTF_8);

        Path sh = installDir.resolve("launch.sh");
        Files.writeString(sh, "#!/usr/bin/env sh\n"
                + "mkdir -p \"" + logDir + "\"\n"
                + "if [ -n \"${PYTRIAL_JAVA:-}\" ]; then JAVA_EXE=\"$PYTRIAL_JAVA\";\n"
                + "elif [ -x \"" + unixJava + "\" ]; then JAVA_EXE=\"" + unixJava + "\";\n"
                + "else JAVA_EXE=java; fi\n"
                + "\"$JAVA_EXE\" " + command + " \"$@\" >> \"" + log + "\" 2>&1\n"
                + "if [ $? -ne 0 ]; then\n"
                + "  echo \"pyTrial launcher failed, last lines of " + log + ":\"\n"
                + "  tail -n 25 \"" + log + "\"\n"
                + "fi\n", StandardCharsets.UTF_8);
        sh.toFile().setExecutable(true);
    }

    private static void writeManifest(Path gameDir, Path installDir, String versionId,
                                      Path loaderJar, Path bootstrapJar, Path modsDir) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("name", "pyTrial");
        manifest.put("version", "0.1.0-SNAPSHOT");
        manifest.put("installedAt", Instant.now().toString());
        manifest.put("gameDir", gameDir.toString());
        manifest.put("minecraftVersion", versionId != null ? versionId
                : VersionResolver.findLatestRelease(gameDir));
        manifest.put("loaderJar", loaderJar != null ? loaderJar.toString() : null);
        manifest.put("bootstrapJar", bootstrapJar != null ? bootstrapJar.toString() : null);
        manifest.put("modsDir", modsDir.toString());
        manifest.put("javaVersion", System.getProperty("java.version"));

        Path file = installDir.resolve("install.json");
        Json.write(file, manifest);
        BootstrapLog.info("Install manifest: " + file);
    }

    private static Path currentJar() {
        try {
            Path self = Path.of(Installer.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return Files.isRegularFile(self) ? self.toAbsolutePath() : null;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static Path resolveLoader(Path gameDir, Path explicit) {
        return LoaderLocator.locate(gameDir, explicit);
    }
}
