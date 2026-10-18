package net.pytrial.discovery;

import com.google.gson.JsonObject;
import net.pytrial.PyTrial;
import net.pytrial.metadata.MetadataParser;
import net.pytrial.modpack.ModPackage;
import net.pytrial.util.Json;
import net.pytrial.util.PyTrialLog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class ModDiscoverer {

    private ModDiscoverer() {
    }

    public static List<ModCandidate> discover(Path modsDir, Path unpackDir) {
        List<ModCandidate> candidates = new ArrayList<>();
        if (!Files.isDirectory(modsDir)) {
            PyTrialLog.warn("Mods directory does not exist: " + modsDir);
            return candidates;
        }

        try (Stream<Path> stream = Files.list(modsDir)) {
            List<Path> entries = stream.sorted(Comparator.comparing(Path::toString)).toList();
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (isIgnored(name)) {
                    continue;
                }
                if (Files.isDirectory(entry)) {
                    ModCandidate candidate = fromDirectory(entry);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                } else if (isPythonFile(name)) {
                    candidates.add(singleFile(entry));
                } else if (ModPackage.isPackage(entry)) {
                    ModCandidate candidate = fromPackage(entry, unpackDir);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                } else if (isJarFile(name)) {
                    ModCandidate candidate = fromJar(entry, unpackDir);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        } catch (IOException e) {
            PyTrialLog.error("Failed to scan the mods directory: " + modsDir, e);
        }

        PyTrialLog.info("Found " + candidates.size() + " Python mod(s) in " + modsDir);
        return candidates;
    }

    private static ModCandidate fromDirectory(Path directory) {
        Path metadataFile = directory.resolve(PyTrial.MOD_METADATA_FILE);
        if (!Files.isRegularFile(metadataFile)) {
            Path fallback = directory.resolve("main.py");
            if (Files.isRegularFile(fallback)) {
                PyTrialLog.warn("Skipping " + directory.getFileName() + ": missing " + PyTrial.MOD_METADATA_FILE);
            }
            return null;
        }

        try {
            JsonObject json = Json.readObject(metadataFile);
            String fallbackId = directory.getFileName().toString().toLowerCase().replace(' ', '_');
            var metadata = MetadataParser.parse(directory, json, fallbackId);
            Path entry = directory.resolve(metadata.entry()).normalize();

            if (!entry.startsWith(directory)) {
                PyTrialLog.error("Entry of mod " + metadata.id() + " escapes the mod directory: " + metadata.entry());
                return null;
            }
            if (!Files.isRegularFile(entry)) {
                PyTrialLog.error("Entry file of mod " + metadata.id() + " does not exist: " + entry);
                return null;
            }
            return new ModCandidate(metadata, directory, entry, false);
        } catch (Exception e) {
            PyTrialLog.error("Failed to parse " + metadataFile + ": " + e.getMessage());
            return null;
        }
    }

    private static ModCandidate fromJar(Path jar, Path unpackDir) {
        if (unpackDir == null) {
            PyTrialLog.warn("Ignoring " + jar.getFileName() + ": no unpack directory provided");
            return null;
        }
        if (!containsMetadata(jar)) {
            return null;
        }

        String fileName = jar.getFileName().toString();
        Path target = unpackDir.resolve(fileName.substring(0, fileName.length() - 4));
        try {
            extract(jar, target);
        } catch (IOException e) {
            PyTrialLog.error("Failed to unpack mod jar: " + jar, e);
            return null;
        }
        return fromDirectory(target);
    }

    private static ModCandidate fromPackage(Path pkg, Path unpackDir) {
        if (unpackDir == null) {
            PyTrialLog.warn("Ignoring " + pkg.getFileName() + ": no unpack directory provided");
            return null;
        }
        byte[] archive;
        try {
            archive = ModPackage.read(pkg);
        } catch (IOException e) {
            PyTrialLog.error("Failed to open mod package " + pkg.getFileName() + ": " + e.getMessage());
            return null;
        }
        if (!containsMetadata(archive)) {
            return null;
        }

        String fileName = pkg.getFileName().toString();
        Path target = unpackDir.resolve(
                fileName.substring(0, fileName.length() - ModPackage.EXTENSION.length()));
        try {
            extract(archive, target);
        } catch (IOException e) {
            PyTrialLog.error("Failed to unpack the mod package: " + pkg, e);
            return null;
        }
        return fromDirectory(target);
    }

    private static boolean containsMetadata(byte[] archive) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (PyTrial.MOD_METADATA_FILE.equals(entry.getName())) {
                    return true;
                }
            }
        } catch (IOException e) {
            PyTrialLog.warn("Cannot read the mod package: " + e.getMessage());
        }
        return false;
    }

    private static boolean containsMetadata(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry(PyTrial.MOD_METADATA_FILE) != null;
        } catch (IOException e) {
            PyTrialLog.warn("Cannot read " + jar.getFileName() + ": " + e.getMessage());
            return false;
        }
    }

    private static void extract(byte[] archive, Path target) throws IOException {
        extract(new ByteArrayInputStream(archive), target);
    }

    private static void extract(Path jar, Path target) throws IOException {
        try (InputStream input = Files.newInputStream(jar)) {
            extract(input, target);
        }
    }

    private static void extract(InputStream input, Path target) throws IOException {
        Files.createDirectories(target);
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF/")) {
                    continue;
                }
                Path destination = target.resolve(name).normalize();
                if (!destination.startsWith(target)) {
                    continue;
                }
                Files.createDirectories(destination.getParent());
                Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static ModCandidate singleFile(Path pythonFile) {
        var metadata = MetadataParser.forSingleFile(pythonFile);
        return new ModCandidate(metadata, pythonFile.getParent(), pythonFile, true);
    }

    private static boolean isIgnored(String name) {
        return name.startsWith("_") || name.startsWith(".") || name.endsWith(".disabled");
    }

    private static boolean isPythonFile(String name) {
        return name.endsWith(".py") && !name.equals("__init__.py");
    }

    private static boolean isJarFile(String name) {
        return name.endsWith(".jar");
    }
}
