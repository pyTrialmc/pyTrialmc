package net.pytrial.lifecycle;

import net.pytrial.discovery.ModCandidate;
import net.pytrial.engine.PythonRuntime;
import net.pytrial.metadata.ModMetadata;
import net.pytrial.transform.HookRule;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class ModContainer {

    private final ModCandidate candidate;
    private ModState state = ModState.DISCOVERED;
    private PythonRuntime runtime;
    private String failureReason;

    public ModContainer(ModCandidate candidate) {
        this.candidate = candidate;
    }

    public ModCandidate candidate() {
        return candidate;
    }

    public String id() {
        return candidate.id();
    }

    public ModMetadata metadata() {
        return candidate.metadata();
    }

    public Path root() {
        return candidate.root();
    }

    public Path pythonEntry() {
        return candidate.pythonEntry();
    }

    public boolean isSingleFile() {
        return candidate.singleFile();
    }

    public List<HookRule> hooks() {
        return candidate.metadata().hooks();
    }

    public ModState state() {
        return state;
    }

    public void setState(ModState state) {
        this.state = state;
    }

    public Optional<PythonRuntime> runtime() {
        return Optional.ofNullable(runtime);
    }

    public void attachRuntime(PythonRuntime runtime) {
        this.runtime = runtime;
    }

    public String failureReason() {
        return failureReason;
    }

    public void markFailed(String reason) {
        this.state = ModState.FAILED;
        this.failureReason = reason;
    }

    @Override
    public String toString() {
        return id() + "@" + metadata().version() + " (" + state + ")";
    }
}
