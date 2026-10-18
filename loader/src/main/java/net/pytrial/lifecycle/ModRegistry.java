package net.pytrial.lifecycle;

import net.pytrial.metadata.VersionConstraint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ModRegistry {
    private final Map<String, ModContainer> mods = new LinkedHashMap<>();
    private volatile LoaderPhase phase = LoaderPhase.CREATED;

    public void add(ModContainer container) {
        mods.put(container.id(), container);
    }

    public void addAll(List<ModContainer> containers) {
        containers.forEach(this::add);
    }

    public Optional<ModContainer> get(String modId) {
        return Optional.ofNullable(mods.get(modId));
    }

    public boolean contains(String modId) {
        return mods.containsKey(modId);
    }

    public String versionOf(String modId) {
        ModContainer container = mods.get(modId);
        return container != null ? container.metadata().version() : null;
    }

    public boolean satisfies(String modId, String requirement) {
        ModContainer container = mods.get(modId);
        if (container == null || container.state() == ModState.FAILED) {
            return false;
        }
        return VersionConstraint.matches(container.metadata().version(), requirement);
    }

    public List<ModContainer> mods() {
        return List.copyOf(mods.values());
    }

    public List<String> modIds() {
        return List.copyOf(mods.keySet());
    }

    public int size() {
        return mods.size();
    }

    public LoaderPhase phase() {
        return phase;
    }

    public void setPhase(LoaderPhase phase) {
        this.phase = phase;
    }

    public Map<String, ModState> snapshot() {
        Map<String, ModState> result = new LinkedHashMap<>();
        mods.forEach((id, container) -> result.put(id, container.state()));
        return Collections.unmodifiableMap(result);
    }

    public void clear() {
        mods.clear();
    }

    public List<ModContainer> problems() {
        List<ModContainer> result = new ArrayList<>();
        for (ModContainer container : mods.values()) {
            if (container.state() == ModState.FAILED || container.state() == ModState.DISABLED) {
                result.add(container);
            }
        }
        return result;
    }
}
