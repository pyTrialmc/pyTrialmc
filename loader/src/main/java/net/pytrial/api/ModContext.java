package net.pytrial.api;

import net.pytrial.metadata.ModMetadata;
import net.pytrial.util.PyTrialLog;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ModContext {

    private final ModMetadata metadata;
    private final Path root;
    private final Path configDir;
    private final Path cacheDir;

    public ModContext(ModMetadata metadata, Path root, Path configDir, Path cacheDir) {
        this.metadata = metadata;
        this.root = root;
        this.configDir = configDir;
        this.cacheDir = cacheDir;
    }

    public String modId() {
        return metadata.id();
    }

    public String version() {
        return metadata.version();
    }

    public ModMetadata metadata() {
        return metadata;
    }

    public String loaderVersion() {
        return net.pytrial.PyTrial.version();
    }

    public String minecraftVersion() {
        return PyTrialApi.environment()
                .map(net.pytrial.PyTrialEnvironment::minecraftVersion)
                .orElse("unknown");
    }

    public Path root() {
        return root;
    }

    public Path configDir() {
        return configDir;
    }

    public Path cacheDir() {
        return cacheDir;
    }

    public Path resolveResource(String relativePath) {
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("Path escapes the mod directory: " + relativePath);
        }
        return resolved;
    }

    public Path resolveConfig(String relativePath) {
        Path resolved = configDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(configDir)) {
            throw new SecurityException("Path escapes the config directory: " + relativePath);
        }
        try {
            Files.createDirectories(resolved.getParent());
        } catch (Exception e) {
            PyTrialLog.warn("Failed to create the config directory: " + resolved.getParent());
        }
        return resolved;
    }

    public void log(String message) {
        PyTrialLog.mod(modId(), message);
    }

    public void warn(String message) {
        PyTrialLog.mod(modId(), "WARN  " + message);
    }

    public void error(String message) {
        PyTrialLog.mod(modId(), "ERROR " + message);
    }

    @Override
    public String toString() {
        return "ModContext(" + modId() + "@" + version() + ")";
    }
}
