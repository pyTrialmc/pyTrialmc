package net.pytrial.discovery;

import net.pytrial.metadata.ModMetadata;

import java.nio.file.Path;

public record ModCandidate(ModMetadata metadata, Path root, Path pythonEntry, boolean singleFile) {
    public String id() {
        return metadata.id();
    }
}
