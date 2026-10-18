package net.pytrial.bootstrap.launch;

import net.pytrial.bootstrap.util.BootstrapLog;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class LoaderLocator {

    private LoaderLocator() {
    }

    public static Path locate(Path gameDir, Path explicit) {
        if (explicit != null) {
            if (Files.isRegularFile(explicit)) {
                return explicit.toAbsolutePath();
            }
            throw new IllegalStateException("Specified loader jar does not exist: " + explicit);
        }

        String property = System.getProperty("pytrial.loaderJar");
        if (property != null && !property.isBlank() && Files.isRegularFile(Path.of(property))) {
            return Path.of(property).toAbsolutePath();
        }

        Path alongside = newestIn(bootstrapDirectory());
        if (alongside != null) {
            return alongside;
        }

        Path installed = newestIn(gameDir.resolve("pytrial").resolve("loader"));
        if (installed != null) {
            return installed;
        }

        BootstrapLog.warn("pytrial-loader-*.jar not found, starting vanilla (no Python mods will be loaded)");
        return null;
    }

    private static Path bootstrapDirectory() {
        try {
            Path self = Path.of(LoaderLocator.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return Files.isDirectory(self) ? self : self.getParent();
        } catch (URISyntaxException | RuntimeException e) {
            return Path.of(".");
        }
    }

    private static Path newestIn(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> candidates = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("pytrial-loader"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparingLong(LoaderLocator::lastModified).reversed())
                    .toList();
            return candidates.isEmpty() ? null : candidates.get(0).toAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
