package net.pytrial.engine.py;

import net.pytrial.util.PyTrialLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class PythonLibrary {

    private static final String RESOURCE_DIR = "python/pyTrialmc/";
    private static final String[] FILES = {
            "__init__.py",
            "console.py",
            "color.py",
            "crypto.py",
            "runtime.py",
            "elements.py",
            "source.py",
            "project.py",
            "build.py",
            "cli.py"
    };

    private PythonLibrary() {
    }

    public static void deploy(Path libraryDir) {
        Path packageDir = libraryDir.resolve("pyTrialmc");
        int deployed = 0;
        for (String file : FILES) {
            if (copy(RESOURCE_DIR + file, packageDir.resolve(file))) {
                deployed++;
            }
        }
        if (deployed > 0) {
            PyTrialLog.info("Deployed the Python library: " + packageDir + " (" + deployed + " file(s))");
        } else {
            PyTrialLog.warn("No bundled Python library resource found: " + RESOURCE_DIR);
        }
    }

    private static boolean copy(String resource, Path target) {
        try (InputStream input = PythonLibrary.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                return false;
            }
            Files.createDirectories(target.getParent());
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            PyTrialLog.warn("Failed to deploy " + target.getFileName() + ": " + e.getMessage());
            return false;
        }
    }
}
