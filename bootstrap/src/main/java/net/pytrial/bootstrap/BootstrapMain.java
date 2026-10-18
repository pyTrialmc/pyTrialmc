package net.pytrial.bootstrap;

import com.google.gson.JsonObject;
import net.pytrial.bootstrap.install.Installer;
import net.pytrial.bootstrap.launch.GameLauncher;
import net.pytrial.bootstrap.launch.LaunchOptions;
import net.pytrial.bootstrap.launch.LoaderLocator;
import net.pytrial.bootstrap.launch.MinecraftLocator;
import net.pytrial.bootstrap.launch.VersionResolver;
import net.pytrial.bootstrap.util.BootstrapLog;
import net.pytrial.bootstrap.util.Json;
import net.pytrial.bootstrap.util.LauncherLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class BootstrapMain {

    private BootstrapMain() {
    }

    public static void main(String[] args) {
        try {
            int exit = run(args);
            if (exit != 0) {
                System.exit(exit);
            }
        } catch (Exception e) {
            BootstrapLog.error(e.getMessage() != null ? e.getMessage() : e.toString());
            if (Boolean.getBoolean("pytrial.debug")) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    private static int run(String[] args) throws Exception {
        String command = "launch";
        int start = 0;
        if (args.length > 0 && !args[0].startsWith("-")) {
            command = args[0];
            start = 1;
        }
        List<String> rest = new ArrayList<>(List.of(args).subList(start, args.length));

        return switch (command) {
            case "launch" -> launch(parseOptions(rest), false);
            case "info" -> launch(parseOptions(rest), true);
            case "install" -> install(parseOptions(rest));
            case "versions", "list-versions" -> listVersions(parseOptions(rest));
            case "mods", "list-mods" -> listMods(parseOptions(rest));
            case "help", "--help", "-h" -> {
                printUsage();
                yield 0;
            }
            default -> {
                BootstrapLog.error("Unknown command: " + command);
                printUsage();
                yield 1;
            }
        };
    }

    private static int launch(LaunchOptions options, boolean forceDryRun) throws Exception {
        options.gameDir = MinecraftLocator.resolve(options.gameDir != null ? options.gameDir.toString() : null);
        LauncherLog.attach(logFile(options.gameDir));
        options.loaderJar = LoaderLocator.locate(options.gameDir, options.loaderJar);
        if (forceDryRun) {
            options.dryRun = true;
        }
        return GameLauncher.launch(options);
    }

    private static int install(LaunchOptions options) throws IOException {
        Path gameDir = MinecraftLocator.resolve(options.gameDir != null ? options.gameDir.toString() : null);
        LauncherLog.attach(logFile(gameDir));
        Path loaderJar = LoaderLocator.locate(gameDir, options.loaderJar);
        Path mods = options.modsDir;
        Installer.Result result = Installer.install(gameDir.toString(), options.versionId, loaderJar, mods,
                options.installScripts);
        if (result.profileId != null) {
            BootstrapLog.info("Launcher profile: " + result.profileId
                    + " (start it from your launcher to play)");
        }
        if (result.launchScript != null) {
            BootstrapLog.info("Launch script: " + result.launchScript);
        }
        return 0;
    }

    private static int listVersions(LaunchOptions options) {
        Path gameDir = MinecraftLocator.resolve(options.gameDir != null ? options.gameDir.toString() : null);
        String latest = VersionResolver.findLatestRelease(gameDir);
        List<String> versions = VersionResolver.listInstalledVersions(gameDir);
        if (versions.isEmpty()) {
            BootstrapLog.warn("No installed version found: " + gameDir.resolve("versions"));
            return 1;
        }
        BootstrapLog.info("Installed versions (game dir: " + gameDir + "):");
        for (String version : versions) {
            System.out.println("  " + version + (version.equals(latest) ? "   <- latest release (default)" : ""));
        }
        return 0;
    }

    private static int listMods(LaunchOptions options) throws IOException {
        Path modsDir;
        if (options.modsDir != null) {
            modsDir = options.modsDir;
        } else if (options.gameDir != null) {
            modsDir = options.gameDir.resolve(Installer.DIRECTORY_NAME).resolve("mods");
        } else {
            Path gameDir = MinecraftLocator.resolve(null);
            modsDir = gameDir.resolve(Installer.DIRECTORY_NAME).resolve("mods");
        }

        if (!Files.isDirectory(modsDir)) {
            BootstrapLog.warn("Mods directory does not exist: " + modsDir);
            return 1;
        }

        BootstrapLog.info("Scanning directory: " + modsDir);
        int count = 0;
        try (Stream<Path> stream = Files.list(modsDir)) {
            for (Path entry : stream.sorted().toList()) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    Path metadata = entry.resolve("pytrial.mod.json");
                    if (Files.isRegularFile(metadata)) {
                        JsonObject json = Json.readObject(metadata);
                        count++;
                        System.out.println("  [package] " + asString(json, "id") + " " + asString(json, "version")
                                + "  (" + name + ")");
                    } else {
                        System.out.println("  [skipped] " + name + " has no pytrial.mod.json");
                    }
                } else if (name.endsWith(".py") && !name.startsWith("_")) {
                    count++;
                    System.out.println("  [single file] " + name);
                }
            }
        }
        BootstrapLog.info("Found " + count + " Python mod(s)");
        return 0;
    }

    private static String asString(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "?";
    }

    private static Path logFile(Path gameDir) {
        return gameDir.resolve(Installer.DIRECTORY_NAME).resolve("logs").resolve(LauncherLog.FILE_NAME);
    }

    private static LaunchOptions parseOptions(List<String> args) {
        LaunchOptions options = new LaunchOptions();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            switch (arg) {
                case "--version", "-v" -> options.versionId = next(args, ++i, arg);
                case "--game-dir", "--gamedir" -> options.gameDir = Path.of(next(args, ++i, arg));
                case "--mods", "--mods-dir" -> options.modsDir = Path.of(next(args, ++i, arg));
                case "--loader" -> options.loaderJar = Path.of(next(args, ++i, arg));
                case "--player", "--username" -> options.playerName = next(args, ++i, arg);
                case "--dry-run" -> options.dryRun = true;
                case "--scripts" -> options.installScripts = true;
                case "--" -> {
                    for (int j = i + 1; j < args.size(); j++) {
                        options.extraGameArgs.add(args.get(j));
                    }
                    return options;
                }
                default -> {
                    if (arg.startsWith("--")) {
                        BootstrapLog.warn("Ignoring unknown argument: " + arg);
                    } else {
                        options.extraGameArgs.add(arg);
                    }
                }
            }
        }
        return options;
    }

    private static String next(List<String> args, int index, String option) {
        if (index >= args.size()) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args.get(index);
    }

    private static void printUsage() {
        System.out.println("""
                pyTrial - write Minecraft Java Edition mods in Python

                Usage:
                  pyTrial launch   [options] [-- extra arguments forwarded to the game]
                  pyTrial install  [options]
                  pyTrial info     [options]      resolve the launch plan without starting (dry-run)
                  pyTrial versions [options]      list installed Minecraft versions
                  pyTrial mods     [options]      list Python mods in the mods directory

                Options:
                  --version <id>     Minecraft version id, defaults to the latest release
                  --game-dir <dir>   game directory, defaults to the system .minecraft
                  --mods <dir>       Python mod directory, defaults to <gameDir>/pytrial/mods
                  --loader <jar>     path to the pytrial-loader jar
                  --player <name>    offline player name (default Player)
                  --dry-run          only print the launch command
                 --scripts          install also writes launch.cmd / launch.sh (install command)
                  -Dpytrial.debug=true  enable debug logging
                """);
    }
}
