package net.pytrial.api;

import net.pytrial.PyTrial;
import net.pytrial.PyTrialEnvironment;
import net.pytrial.PyTrialKernel;
import net.pytrial.event.EventBus;
import net.pytrial.lifecycle.ModRegistry;

import java.util.List;
import java.util.Optional;

public final class PyTrialApi {

    private PyTrialApi() {
    }

    public static String loaderVersion() {
        return PyTrial.version();
    }

    public static Optional<PyTrialEnvironment> environment() {
        return Optional.ofNullable(PyTrialKernel.get().environment());
    }

    public static ModRegistry registry() {
        return PyTrialKernel.get().registry();
    }

    public static EventBus events() {
        return PyTrialKernel.get().eventBus();
    }

    public static boolean isModLoaded(String modId) {
        return registry().contains(modId);
    }

    public static String modVersion(String modId) {
        return registry().versionOf(modId);
    }

    public static List<String> loadedMods() {
        return registry().modIds();
    }
}
