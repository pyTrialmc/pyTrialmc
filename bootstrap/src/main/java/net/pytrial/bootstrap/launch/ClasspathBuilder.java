package net.pytrial.bootstrap.launch;

import net.pytrial.bootstrap.util.BootstrapLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ClasspathBuilder {

    public static final class Result {
        public final List<Path> classpath;
        public final Path nativesDirectory;
        public final List<String> missingLibraries;

        Result(List<Path> classpath, Path nativesDirectory, List<String> missingLibraries) {
            this.classpath = classpath;
            this.nativesDirectory = nativesDirectory;
            this.missingLibraries = missingLibraries;
        }

        public String joined() {
            return String.join(java.io.File.pathSeparator, classpath.stream().map(Path::toString).toList());
        }
    }

    private ClasspathBuilder() {
    }

    public static Result build(Path gameDir, String versionId, VersionJson version) throws IOException {
        Path libraryRoot = gameDir.resolve("libraries");
        Path nativesDirectory = gameDir.resolve("pytrial").resolve("natives").resolve(versionId);
        List<Path> classpath = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();

        Map<String, Boolean> features = Map.of();

        for (VersionJson.Library library : version.libraries) {
            if (!RuleEvaluator.allows(library.rules, features)) {
                continue;
            }
            MavenCoordinate coordinate = MavenCoordinate.parse(library.name);

            if (library.natives != null && !library.natives.isEmpty()) {
                Path nativeJar = resolveNativeJar(libraryRoot, coordinate, library);
                if (nativeJar != null) {
                    extractNatives(nativeJar, nativesDirectory, library.extract);
                }

                continue;
            }

            Path jar = locate(libraryRoot, coordinate, library, versionId);
            if (jar == null) {
                missing.add(coordinate.toString());
                continue;
            }
            String key = jar.toString();
            if (seen.add(key)) {
                classpath.add(jar);
            }
        }

        Path clientJar = VersionResolver.clientJar(gameDir, versionId);
        if (Files.isRegularFile(clientJar)) {
            if (seen.add(clientJar.toString())) {
                classpath.add(clientJar);
            }
        } else {
            missing.add("client jar (" + clientJar + ")");
        }

        if (!missing.isEmpty()) {
            BootstrapLog.warn(missing.size() + " library file(s) missing; start this version once with the official launcher, then retry:");
            missing.forEach(item -> BootstrapLog.warn("  - " + item));
        }
        return new Result(classpath, nativesDirectory, missing);
    }

    private static Path locate(Path libraryRoot, MavenCoordinate coordinate,
                               VersionJson.Library library, String versionId) {
        if (library.downloads != null && library.downloads.artifact != null
                && library.downloads.artifact.path != null) {
            Path byPath = libraryRoot.resolve(library.downloads.artifact.path);
            if (Files.isRegularFile(byPath)) {
                return byPath;
            }
        }
        Path byCoordinate = libraryRoot.resolve(coordinate.path());
        if (Files.isRegularFile(byCoordinate)) {
            return byCoordinate;
        }

        Path local = libraryRoot.getParent().resolve("versions").resolve(versionId)
                .resolve(coordinate.artifact() + "-" + coordinate.version() + ".jar");
        return Files.isRegularFile(local) ? local : null;
    }

    private static Path resolveNativeJar(Path libraryRoot, MavenCoordinate coordinate,
                                         VersionJson.Library library) {
        String osKey = RuleEvaluator.currentOsName();
        String classifier = library.natives.get(osKey);
        if (classifier == null) {
            return null;
        }

        if (library.downloads != null && library.downloads.classifiers != null) {
            VersionJson.Artifact artifact = library.downloads.classifiers.get(classifier);
            if (artifact != null && artifact.path != null) {
                Path byPath = libraryRoot.resolve(artifact.path);
                if (Files.isRegularFile(byPath)) {
                    return byPath;
                }
            }
        }
        Path byCoordinate = libraryRoot.resolve(coordinate.withClassifier(classifier).path());
        if (Files.isRegularFile(byCoordinate)) {
            return byCoordinate;
        }
        BootstrapLog.debug("Missing natives package: " + coordinate.withClassifier(classifier));
        return null;
    }

    private static void extractNatives(Path nativeJar, Path targetDirectory, VersionJson.Extract extract)
            throws IOException {
        Files.createDirectories(targetDirectory);
        List<String> excludes = extract != null && extract.exclude != null ? extract.exclude : List.of();

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(nativeJar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF/")) {
                    continue;
                }
                boolean excluded = excludes.stream().anyMatch(name::startsWith);
                if (excluded) {
                    continue;
                }
                Path target = targetDirectory.resolve(name).normalize();
                if (!target.startsWith(targetDirectory)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    static void writeStream(InputStream in, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
