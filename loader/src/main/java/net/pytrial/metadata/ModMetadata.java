package net.pytrial.metadata;

import net.pytrial.transform.HookRule;

import java.util.List;
import java.util.Map;

public record ModMetadata(
        String id,
        String name,
        String version,
        String description,
        List<String> authors,
        String entry,
        String python,
        List<String> loadAfter,
        List<ModDependency> dependencies,
        List<HookRule> hooks,
        Map<String, Object> extra
) {
    public static final String DEFAULT_ENTRY = "main.py";
    public static final String DEFAULT_VERSION = "1.0.0";

    public ModMetadata {
        authors = List.copyOf(authors);
        loadAfter = List.copyOf(loadAfter);
        dependencies = List.copyOf(dependencies);
        hooks = List.copyOf(hooks);
        extra = Map.copyOf(extra);
    }
}
