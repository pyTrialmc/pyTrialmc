package net.pytrial.engine;

import net.pytrial.PyTrialEnvironment;
import net.pytrial.discovery.ModCandidate;

import java.nio.file.Path;

public interface PythonEngine extends AutoCloseable {

    String id();

    String version();

    boolean isAvailable();

    void initialize(PyTrialEnvironment environment) throws PythonEngineException;

    PythonRuntime createRuntime(ModCandidate mod, Path configDir) throws PythonEngineException;

    @Override
    void close();
}
