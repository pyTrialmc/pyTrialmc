package net.pytrial.metadata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pytrial.transform.HookRule;
import net.pytrial.transform.HookRuleParser;
import net.pytrial.util.Json;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MetadataParser {

    private static final List<String> KNOWN_KEYS = List.of(
            "id", "name", "version", "description", "authors", "author",
            "entry", "python", "loadAfter", "load_after", "loadBefore", "load_before",
            "depends", "dependencies", "hooks", "schemaVersion");

    private MetadataParser() {
    }

    public static ModMetadata parse(Path modRoot, JsonObject json, String fallbackId) {
        String id = Json.optString(json, "id", fallbackId);
        if (id == null || id.isBlank()) {
            throw new MetadataException("Mod metadata has no id and it cannot be derived from the directory name: " + modRoot);
        }
        if (!id.matches("[a-z0-9_\\-.]+")) {
            throw new MetadataException("Mod id may only contain lowercase letters, digits, underscore, dot and hyphen: " + id);
        }

        String name = Json.optString(json, "name", id);
        String version = Json.optString(json, "version", ModMetadata.DEFAULT_VERSION);
        String description = Json.optString(json, "description", "");
        String entry = Json.optString(json, "entry", ModMetadata.DEFAULT_ENTRY);
        String python = Json.optString(json, "python", "*");

        List<String> authors = stringList(json, "authors");
        if (authors.isEmpty()) {
            authors = stringList(json, "author");
        }
        List<String> loadAfter = stringList(json, "loadAfter");
        if (loadAfter.isEmpty()) {
            loadAfter = stringList(json, "load_after");
        }

        List<ModDependency> dependencies = parseDependencies(json);
        List<HookRule> hooks = HookRuleParser.parse(
                json.has("hooks") && json.get("hooks").isJsonArray() ? json.getAsJsonArray("hooks") : null,
                id);

        Map<String, Object> extra = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entrySet : json.entrySet()) {
            if (!KNOWN_KEYS.contains(entrySet.getKey())) {
                extra.put(entrySet.getKey(), Json.PRETTY.fromJson(entrySet.getValue(), Object.class));
            }
        }

        return new ModMetadata(id, name, version, description, authors, entry, python,
                loadAfter, dependencies, hooks, extra);
    }

    public static ModMetadata forSingleFile(Path pythonFile) {
        String fileName = pythonFile.getFileName().toString();
        String id = fileName.substring(0, fileName.length() - 3)
                .toLowerCase()
                .replaceAll("[^a-z0-9_\\-.]", "_");

        return new ModMetadata(
                id,
                id,
                ModMetadata.DEFAULT_VERSION,
                "Single file Python mod",
                List.of(),
                fileName,
                "*",
                List.of(),
                List.of(),
                List.of(),
                Map.of("singleFile", true));
    }

    private static List<ModDependency> parseDependencies(JsonObject json) {
        JsonArray array = null;
        if (json.has("depends") && json.get("depends").isJsonArray()) {
            array = json.getAsJsonArray("depends");
        } else if (json.has("dependencies") && json.get("dependencies").isJsonArray()) {
            array = json.getAsJsonArray("dependencies");
        }
        if (array == null) {
            return List.of();
        }

        List<ModDependency> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                result.add(new ModDependency(element.getAsString()));
                continue;
            }
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String id = Json.optString(object, "id", null);
            if (id == null || id.isBlank()) {
                continue;
            }
            result.add(new ModDependency(
                    id,
                    Json.optString(object, "version", "*"),
                    Json.optBoolean(object, "optional", false)));
        }
        return result;
    }

    private static List<String> stringList(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return List.of();
        }
        JsonElement element = json.get(key);
        List<String> result = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                result.add(item.getAsString());
            }
        } else {
            result.add(element.getAsString());
        }
        return result;
    }
}
