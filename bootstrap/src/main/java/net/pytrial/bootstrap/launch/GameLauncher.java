package net.pytrial.bootstrap.launch;

import net.pytrial.bootstrap.util.BootstrapLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameLauncher {

    public static final class Plan {
        public final List<String> command;
        public final Path gameDir;

        Plan(List<String> command, Path gameDir) {
            this.command = command;
            this.gameDir = gameDir;
        }

        public String pretty() {
            StringBuilder sb = new StringBuilder();
            for (String part : command) {
                if (sb.length() > 0) {
                    sb.append(System.lineSeparator()).append("  ");
                }
                sb.append(part);
            }
            return sb.toString();
        }
    }

    private GameLauncher() {
    }

    public static Plan buildPlan(LaunchOptions options) throws IOException {
        Path gameDir = options.gameDir;
        Path versionJsonPath = VersionResolver.resolveVersionJson(gameDir, options.versionId);
        VersionJson version = VersionResolver.read(versionJsonPath);
        String versionId = stripJson(versionJsonPath.getFileName().toString());

        ClasspathBuilder.Result libraries = ClasspathBuilder.build(gameDir, versionId, version);

        Path modsDir = options.effectiveModsDir();
        Files.createDirectories(modsDir);

        Map<String, String> variables = createVariables(options, gameDir, version, versionId,
                libraries, options.loaderJar);

        List<String> command = new ArrayList<>();
        command.add(JavaRuntime.resolve(version, gameDir).toString());

        List<String> jvmArgs = ArgumentBuilder.build(
                version.arguments != null ? version.arguments.jvm : null,
                variables, Map.of("has_custom_resolution", false));
        command.addAll(jvmArgs);

        String loaderJar = variables.get("pytrial_loader_jar");
        if (loaderJar != null && !loaderJar.isBlank()) {
            String agentArg = loaderJar + "=--game-dir=" + gameDir
                    + ",--mods=" + modsDir
                    + ",--version=" + versionId
                    + ",--main-class=" + safeMainClass(version);
            command.add("-javaagent:" + agentArg);
        } else {
            BootstrapLog.warn("pytrial-loader jar not found, starting vanilla (no Python mods will be loaded)");
        }
        command.add("-Dpytrial.gameDir=" + gameDir);
        command.add("-Dpytrial.modsDir=" + modsDir);
        command.add("-Dpytrial.version=" + versionId);
        command.add("-Dpytrial.loaderJar=" + variables.get("pytrial_loader_jar"));

        String classpath = libraries.joined();
        command.add("-cp");
        command.add(classpath);

        command.add(safeMainClass(version));

        List<String> gameArgs = version.minecraftArguments != null
                ? ArgumentBuilder.buildLegacy(version.minecraftArguments, variables)
                : ArgumentBuilder.build(version.arguments != null ? version.arguments.game : null,
                variables, Map.of("is_demo_user", false, "has_custom_resolution", false));
        command.addAll(gameArgs);
        command.addAll(options.extraGameArgs);

        return new Plan(command, gameDir);
    }

    public static int launch(LaunchOptions options) throws IOException, InterruptedException {
        Plan plan = buildPlan(options);
        if (options.dryRun) {
            BootstrapLog.info("Launch command (dry-run, nothing started):");
            System.out.println("  " + plan.pretty());
            return 0;
        }

        BootstrapLog.info("Starting Minecraft...");
        BootstrapLog.debug("Command: " + plan.pretty());

        ProcessBuilder builder = new ProcessBuilder(plan.command);
        builder.directory(options.gameDir.toFile());
        builder.inheritIO();
        Process process = builder.start();
        int exit = process.waitFor();
        BootstrapLog.info("Game process exited with code " + exit);
        return exit;
    }

    private static Map<String, String> createVariables(LaunchOptions options, Path gameDir,
                                                       VersionJson version, String versionId,
                                                       ClasspathBuilder.Result libraries,
                                                       Path loaderJar) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("classpath", libraries.joined());
        variables.put("natives_directory", libraries.nativesDirectory.toString());
        variables.put("launcher_name", "pyTrial");
        variables.put("launcher_version", "0.1.0");
        variables.put("library_directory", gameDir.resolve("libraries").toString());
        variables.put("classpath_separator", java.io.File.pathSeparator);
        variables.put("version_name", versionId);
        variables.put("game_directory", gameDir.toString());
        variables.put("assets_root", gameDir.resolve("assets").toString());
        variables.put("assets_index_name", version.assets != null ? version.assets : versionId);
        variables.put("auth_player_name", options.playerName);
        variables.put("auth_uuid", options.uuid);
        variables.put("auth_access_token", options.accessToken);
        variables.put("clientid", "pytrial");
        variables.put("auth_xuid", "0");
        variables.put("user_type", "legacy");
        variables.put("version_type", version.type != null ? version.type : "release");
        variables.put("resolution_width", "854");
        variables.put("resolution_height", "480");
        variables.put("pytrial_loader_jar", loaderJar != null ? loaderJar.toString() : "");
        return variables;
    }

    private static String safeMainClass(VersionJson version) {
        return version.mainClass != null ? version.mainClass : "net.minecraft.client.main.Main";
    }

    private static String stripJson(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }
}
