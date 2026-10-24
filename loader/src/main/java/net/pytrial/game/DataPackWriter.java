package net.pytrial.game;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.pytrial.util.PyTrialLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * Data pack generated from the declared mod content.
 *
 * <p>Most of the Minecraft catalog is data driven: a recipe, a loot table, an advancement, a biome or
 * a dimension is a JSON file under {@code data/<namespace>/<folder>/<path>.json}, not a Java class.
 * Those kinds are therefore bridged by writing the pack instead of touching the registry.
 *
 * <p>The pack is written to {@code <gameDir>/pytrial/datapacks/pytrial_generated} and copied into every
 * world that already exists, because Minecraft loads data packs per world.
 */
public final class DataPackWriter {

    public static final String PACK_ID = "pytrial_generated";
    public static final String PACK_DESCRIPTION = "Recipes, loot tables, advancements and worldgen declared by pyTrial";

    /** Data pack format of the target range; a wrong value only produces a warning in game. */
    private static final int PACK_FORMAT = 61;
    private static final int PACK_FORMAT_MIN = 40;
    private static final int PACK_FORMAT_MAX = 99;

    /** Element kind to the folder it lives in inside {@code data/<namespace>/}. */
    private static final Map<String, String> FOLDERS = Map.ofEntries(
            Map.entry("advancement", "advancement"),
            Map.entry("recipe", "recipe"),
            Map.entry("loot_table", "loot_table"),
            Map.entry("function", "function"),
            Map.entry("structure", "structure"),
            Map.entry("biome", "worldgen/biome"),
            Map.entry("feature", "worldgen/configured_feature"),
            Map.entry("placed_feature", "worldgen/placed_feature"),
            Map.entry("dimension", "dimension"),
            Map.entry("dimension_type", "dimension_type"),
            Map.entry("enchantment", "enchantment"),
            Map.entry("damage_type", "damage_type"),
            Map.entry("banner_pattern", "banner_pattern"),
            Map.entry("armor_trim", "trim_pattern"),
            Map.entry("trim_material", "trim_material"),
            Map.entry("painting", "painting_variant"),
            Map.entry("villager_trade", "villager_trade"),
            Map.entry("villager_profession", "villager_profession"),
            Map.entry("item_modifier", "item_modifier"),
            Map.entry("predicate", "predicate")
    );

    private DataPackWriter() {
    }

    /** True when this kind is bridged by a JSON file rather than by the registry. */
    public static boolean handles(String kind) {
        return kind != null && (FOLDERS.containsKey(kind) || "tag".equals(kind));
    }

    /** The folder a kind is written to, or {@code null} when the kind is not data driven. */
    public static String folder(String kind) {
        return FOLDERS.get(kind);
    }

    public static void install(Path gameDir, List<ContentRegistry.DataEntry> entries) {
        if (gameDir == null || entries == null || entries.isEmpty()) {
            return;
        }
        try {
            Path root = gameDir.resolve("pytrial").resolve("datapacks").resolve(PACK_ID);
            Files.createDirectories(root);
            writeMeta(root);

            int written = 0;
            for (ContentRegistry.DataEntry entry : entries) {
                if (writeEntry(root, entry)) {
                    written++;
                }
            }
            int worlds = copyIntoWorlds(gameDir, root);
            PyTrialLog.info("Generated a data pack for " + written + " element(s): " + root);
            if (worlds > 0) {
                PyTrialLog.info("  copied into " + worlds + " existing world(s); new worlds pick it up on next launch");
            } else {
                PyTrialLog.info("  no world found yet, it will be copied in when a world is created");
            }
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot generate the data pack: " + t.getMessage());
        }
    }

    private static boolean writeEntry(Path root, ContentRegistry.DataEntry entry) {
        if ("function".equals(entry.kind())) {
            return writeText(root, entry, "content", ".mcfunction");
        }
        if ("structure".equals(entry.kind())) {
            return writeBinary(root, entry);
        }
        String folder = folder(entry.kind());
        if (folder == null && "tag".equals(entry.kind())) {
            String registry = entry.registry();
            if (registry == null || registry.isBlank()) {
                PyTrialLog.warn("Tag " + entry.id() + " has no registry, ignored");
                return false;
            }
            folder = "tags/" + registry.trim();
        }
        if (folder == null) {
            PyTrialLog.warn("No data folder for kind '" + entry.kind() + "', ignored");
            return false;
        }
        try {
            Path dir = root.resolve("data").resolve(entry.namespace()).resolve(folder);
            Files.createDirectories(dir);
            Path file = dir.resolve(entry.path() + ".json");
            String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
                    .create().toJson(entry.payload());
            Files.writeString(file, json + "\n", StandardCharsets.UTF_8);
            return true;
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot write " + entry.kind() + " " + entry.id() + ": " + t.getMessage());
            return false;
        }
    }

    /** Functions are plain text, not JSON. */
    private static boolean writeText(Path root, ContentRegistry.DataEntry entry, String field,
                                     String suffix) {
        String body = entry.payload().has(field) ? entry.payload().get(field).getAsString() : null;
        if (body == null) {
            PyTrialLog.warn(entry.kind() + " " + entry.id() + " has no '" + field + "', ignored");
            return false;
        }
        try {
            Path dir = root.resolve("data").resolve(entry.namespace()).resolve("function");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(entry.path() + suffix), body.endsWith("\n") ? body : body + "\n",
                    StandardCharsets.UTF_8);
            return true;
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot write " + entry.id() + ": " + t.getMessage());
            return false;
        }
    }

    /** Structures are compressed NBT: the declaration carries the bytes, base64 encoded. */
    private static boolean writeBinary(Path root, ContentRegistry.DataEntry entry) {
        String encoded = entry.payload().has("nbt") ? entry.payload().get("nbt").getAsString() : null;
        if (encoded == null || encoded.isBlank()) {
            PyTrialLog.warn("structure " + entry.id() + " has no 'nbt' payload (base64 of the .nbt file), "
                    + "ignored");
            return false;
        }
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(encoded.replaceAll("\\s", ""));
            Path dir = root.resolve("data").resolve(entry.namespace()).resolve("structure");
            Files.createDirectories(dir);
            Files.write(dir.resolve(entry.path() + ".nbt"), bytes);
            return true;
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot write structure " + entry.id() + ": " + t.getMessage());
            return false;
        }
    }

    private static void writeMeta(Path root) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description", PACK_DESCRIPTION);

        JsonObject range = new JsonObject();
        range.addProperty("min_inclusive", PACK_FORMAT_MIN);
        range.addProperty("max_inclusive", PACK_FORMAT_MAX);

        JsonObject meta = new JsonObject();
        meta.add("pack", pack);
        meta.add("supported_formats", range);
        Files.writeString(root.resolve("pack.mcmeta"),
                new GsonBuilder().setPrettyPrinting().create().toJson(meta) + "\n",
                StandardCharsets.UTF_8);
    }

    /** Minecraft loads data packs per world, so mirror the pack into every save that already exists. */
    private static int copyIntoWorlds(Path gameDir, Path packRoot) {
        Path saves = gameDir.resolve("saves");
        if (!Files.isDirectory(saves)) {
            return 0;
        }
        int copied = 0;
        try (DirectoryStream<Path> worlds = Files.newDirectoryStream(saves)) {
            for (Path world : worlds) {
                if (!Files.isDirectory(world) || !Files.exists(world.resolve("level.dat"))) {
                    continue;
                }
                try {
                    copyTree(packRoot, world.resolve("datapacks").resolve(PACK_ID));
                    copied++;
                } catch (Throwable t) {
                    PyTrialLog.warn("Cannot copy the data pack into " + world.getFileName() + ": " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot scan the saves folder: " + t.getMessage());
        }
        return copied;
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.createDirectories(to);
        try (var stream = Files.walk(from)) {
            for (Path source : (Iterable<Path>) stream::iterator) {
                Path target = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
