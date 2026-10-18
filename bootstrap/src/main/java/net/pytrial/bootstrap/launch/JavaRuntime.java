package net.pytrial.bootstrap.launch;

import net.pytrial.bootstrap.util.BootstrapLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Chooses the JVM the game runs on.
 *
 * <p>This launcher is compiled for Java 21, but Minecraft 26.x is compiled for Java 25, so the JVM
 * that runs the launcher is almost never a JVM the game can run on. The version json says what the
 * game needs ({@code javaVersion.majorVersion}); this picks the smallest installed JVM that
 * satisfies it, looking where launchers and IDEs keep them.
 */
public final class JavaRuntime {

    private static final int RUNTIME_TREE_DEPTH = 4;

    private JavaRuntime() {
    }

    public record Candidate(Path home, int major) {
    }

    /** Returns the java executable to start the game with. Never throws: an unusable pick is reported. */
    public static Path resolve(VersionJson version, Path gameDir) {
        String override = System.getenv("PYTRIAL_JAVA");
        if (override != null && !override.isBlank()) {
            BootstrapLog.info("Using the runtime from PYTRIAL_JAVA: " + override);
            return Paths.get(override);
        }

        Path ownHome = Path.of(System.getProperty("java.home"));
        int own = Runtime.version().feature();
        int required = version != null && version.javaVersion != null ? version.javaVersion.majorVersion : 0;

        if (required <= own) {
            return executable(ownHome);
        }

        List<Candidate> candidates = discover(gameDir);
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (candidate.major() < required) {
                continue;
            }
            if (best == null || candidate.major() < best.major()) {
                best = candidate;
            }
        }
        if (best != null) {
            BootstrapLog.info("Minecraft " + id(version) + " needs Java " + required
                    + ", starting it with Java " + best.major() + " from " + best.home());
            return executable(best.home());
        }

        Candidate newest = candidates.stream().max(Comparator.comparingInt(Candidate::major)).orElse(null);
        BootstrapLog.warn("Minecraft " + id(version) + " needs Java " + required
                + ", but no matching runtime was found"
                + (newest != null ? " (the newest found is Java " + newest.major() + ")" : "")
                + "; the game will most likely fail with UnsupportedClassVersionError."
                + " Install Java " + required + " or point PYTRIAL_JAVA at one");
        return executable(ownHome);
    }

    private static String id(VersionJson version) {
        return version != null && version.id != null ? version.id : "";
    }

    private static Path executable(Path home) {
        Path candidate = home.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
        return Files.isRegularFile(candidate) ? candidate : Paths.get("java");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static List<Candidate> discover(Path gameDir) {
        Set<Path> homes = new LinkedHashSet<>();
        scan(homes, Path.of(System.getProperty("java.home")), 0);

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            scan(homes, Paths.get(javaHome), 0);
        }

        String home = System.getProperty("user.home", "");
        String programFiles = System.getenv("ProgramFiles") != null
                ? System.getenv("ProgramFiles") : "C:\\Program Files";
        String localAppData = System.getenv("LOCALAPPDATA") != null
                ? System.getenv("LOCALAPPDATA") : home + "\\AppData\\Local";

        for (String root : List.of(
                home + "\\.jdks",
                programFiles + "\\Java",
                programFiles + "\\Eclipse Adoptium",
                programFiles + "\\Microsoft",
                programFiles + "\\Amazon Corretto",
                programFiles + "\\Zulu",
                programFiles + "\\BellSoft",
                programFiles + "\\JetBrains",
                localAppData + "\\Programs\\Eclipse Adoptium",
                "/usr/lib/jvm",
                "/Library/Java/JavaVirtualMachines")) {
            scan(homes, Paths.get(root), 2);
        }

        if (gameDir != null) {
            scan(homes, gameDir.resolve("runtime"), RUNTIME_TREE_DEPTH);
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Path candidate : homes) {
            int major = majorOf(candidate);
            if (major > 0 && Files.isRegularFile(candidate.resolve("bin").resolve(
                    isWindows() ? "java.exe" : "java"))) {
                candidates.add(new Candidate(candidate, major));
            }
        }
        return candidates;
    }

    /** Adds every directory under the root that looks like a JDK home, to the given depth. */
    private static void scan(Set<Path> homes, Path root, int depth) {
        if (root == null || depth < 0 || !Files.isDirectory(root)) {
            return;
        }
        if (Files.isRegularFile(root.resolve("release"))
                || Files.isRegularFile(root.resolve("bin").resolve(isWindows() ? "java.exe" : "java"))) {
            homes.add(root);
            return;
        }
        if (depth == 0) {
            return;
        }
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.filter(Files::isDirectory).toList()) {
                scan(homes, child, depth - 1);
            }
        } catch (IOException ignored) {
            // An unreadable directory simply contributes no runtimes.
        }
    }

    /** Reads {@code JAVA_VERSION="25.0.1"} from the release file next to bin. */
    private static int majorOf(Path home) {
        Path release = home.resolve("release");
        if (!Files.isRegularFile(release)) {
            return -1;
        }
        try {
            for (String line : Files.readAllLines(release, StandardCharsets.UTF_8)) {
                if (!line.startsWith("JAVA_VERSION=")) {
                    continue;
                }
                String value = line.substring("JAVA_VERSION=".length()).replace("\"", "").trim();
                String[] parts = value.split("[.\\-_+]");
                if (parts.length == 0) {
                    return -1;
                }
                String major = "1".equals(parts[0]) && parts.length > 1 ? parts[1] : parts[0];
                return Integer.parseInt(major.trim());
            }
        } catch (IOException | NumberFormatException e) {
            return -1;
        }
        return -1;
    }
}
