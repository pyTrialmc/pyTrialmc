package net.pytrial.engine;

import net.pytrial.util.PyTrialLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

public final class PythonEngineFactory {

    private PythonEngineFactory() {
    }

    public static List<PythonEngine> all() {
        List<PythonEngine> engines = new ArrayList<>();
        try {
            ServiceLoader<PythonEngine> loader =
                    ServiceLoader.load(PythonEngine.class, PythonEngine.class.getClassLoader());
            for (PythonEngine engine : loader) {
                engines.add(engine);
            }
        } catch (Throwable t) {
            PyTrialLog.error("Failed to load the Python engine SPI", t);
        }
        return engines;
    }

    public static Optional<PythonEngine> discover() {
        return all().stream().filter(PythonEngine::isAvailable).findFirst();
    }

    public static Optional<PythonEngine> byId(String id) {
        return all().stream()
                .filter(engine -> engine.id().equalsIgnoreCase(id))
                .findFirst();
    }

    public static PythonEngine missing() {
        return MissingPythonEngine.INSTANCE;
    }

    public static final class MissingPythonEngine implements PythonEngine {

        static final MissingPythonEngine INSTANCE = new MissingPythonEngine();

        static final String MESSAGE = """
                No Python engine is plugged in, pyTrial cannot run Python mods.
                Implement net.pytrial.engine.PythonEngine and register it through SPI, see docs/python-engine.md.""";

        private MissingPythonEngine() {
        }

        @Override
        public String id() {
            return "none";
        }

        @Override
        public String version() {
            return "0.0.0";
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void initialize(net.pytrial.PyTrialEnvironment environment) {
            throw new PythonEngineException(MESSAGE);
        }

        @Override
        public PythonRuntime createRuntime(net.pytrial.discovery.ModCandidate mod, java.nio.file.Path configDir) {
            throw new PythonEngineException(MESSAGE);
        }

        @Override
        public void close() {
        }
    }
}
