package net.pytrial.mapping;

import net.pytrial.PyTrialEnvironment;
import net.pytrial.util.PyTrialLog;

import java.nio.file.Files;
import java.nio.file.Path;

public final class MappingLoader {

    private MappingLoader() {
    }

    public static Path mappingFile(PyTrialEnvironment environment, String versionId) {
        return environment.installDir().resolve("mappings").resolve(versionId + "-client.txt");
    }

    public static MappingSet load(PyTrialEnvironment environment) {
        String versionId = environment.minecraftVersion();
        if (versionId == null || versionId.isBlank()) {
            PyTrialLog.debug("No game version given, skipping the mappings load");
            return MappingSet.EMPTY;
        }

        Path file = mappingFile(environment, versionId);
        if (!Files.isRegularFile(file)) {
            PyTrialLog.info("No mappings file for " + versionId + ", injecting with official (unobfuscated) names");
            return MappingSet.EMPTY;
        }

        try {
            long start = System.currentTimeMillis();
            MappingSet mappings = MappingParser.parse(file);
            PyTrialLog.info("Loaded mappings: " + mappings.classCount() + " class(es) in "
                    + (System.currentTimeMillis() - start) + "ms");
            return mappings;
        } catch (Throwable t) {
            PyTrialLog.error("Failed to parse mappings: " + t.getMessage());
            return MappingSet.EMPTY;
        }
    }
}
