package net.pytrial.engine.py;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.pytrial.PyTrialEnvironment;
import net.pytrial.discovery.ModCandidate;
import net.pytrial.engine.PythonEngine;
import net.pytrial.engine.PythonEngineException;
import net.pytrial.engine.PythonRuntime;
import net.pytrial.game.CommandBridge;
import net.pytrial.game.ContentRegistry;
import net.pytrial.game.DataPackWriter;
import net.pytrial.game.RegistryBridge;
import net.pytrial.util.Json;
import net.pytrial.util.PyTrialLog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class SubprocessPythonEngine implements PythonEngine {

    private static final Gson GSON = new Gson();
    private static final String[] CANDIDATES = {"python", "python3", "py"};
    private static final long LOAD_TIMEOUT_SECONDS = 30L;

    private static String resolvedPython;
    private static boolean pythonSearched;
    private static RegistryBridge registryBridge;

    private final Map<String, CompletableFuture<Boolean>> pendingLoads = new ConcurrentHashMap<>();

    private PyTrialEnvironment environment;
    private Process process;
    private BufferedWriter writer;
    private Thread readerThread;
    private volatile boolean alive;

    @Override
    public String id() {
        return "cpython-subprocess";
    }

    @Override
    public String version() {
        return resolvedPython != null ? resolvedPython : "unknown";
    }

    @Override
    public boolean isAvailable() {
        return findPython() != null;
    }

    @Override
    public void initialize(PyTrialEnvironment environment) {
        this.environment = environment;
        String python = findPython();
        if (python == null) {
            throw new PythonEngineException("No usable python interpreter found (tried python / python3 / py)");
        }

        Path libraryPath = environment.pythonLibraryDir();
        PythonLibrary.deploy(libraryPath);
        if (!Files.isDirectory(libraryPath.resolve("pyTrialmc"))) {
            throw new PythonEngineException("Python library directory missing: " + libraryPath.resolve("pyTrialmc"));
        }

        String script = "import sys, json\n"
                + "sys.path.insert(0, '" + libraryPath.toString().replace('\\', '/') + "')\n"
                + "from pyTrialmc import runtime\n"
                + "def emit(payload):\n"
                + "    sys.stdout.write(json.dumps(payload, ensure_ascii=False) + '\\n')\n"
                + "    sys.stdout.flush()\n"
                + "runtime.bind(emit)\n"
                + "runtime.serve()\n";

        try {
            process = new ProcessBuilder(python, "-u", "-c", script).start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));

            alive = true;
            readerThread = new Thread(() -> readLoop(stdout), "pyTrial-python-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            Thread errorThread = new Thread(() -> errorLoop(stderr), "pyTrial-python-stderr");
            errorThread.setDaemon(true);
            errorThread.start();

            PyTrialLog.info("Python engine started: " + python + " (library dir " + libraryPath + ")");
        } catch (IOException e) {
            throw new PythonEngineException("Failed to start Python: " + e.getMessage(), e);
        }
    }

    @Override
    public PythonRuntime createRuntime(ModCandidate mod, Path configDir) {
        return new Runtime(mod, configDir);
    }

    @Override
    public void close() {
        alive = false;
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
        if (process != null) {
            process.destroy();
        }
    }

    private static synchronized String findPython() {
        if (pythonSearched) {
            return resolvedPython;
        }
        pythonSearched = true;
        for (String candidate : CANDIDATES) {
            try {
                Process probe = new ProcessBuilder(candidate, "--version")
                        .redirectErrorStream(true)
                        .start();
                if (probe.waitFor() == 0) {
                    resolvedPython = candidate;
                    return candidate;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private synchronized void send(JsonObject message) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(GSON.toJson(message));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            PyTrialLog.error("Failed to send data to Python: " + e.getMessage());
        }
    }

    private void readLoop(BufferedReader reader) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                handleEvent(line);
            }
        } catch (Throwable t) {
            PyTrialLog.debug("Python output channel ended: " + t.getMessage());
        }
        alive = false;
        pendingLoads.values().forEach(future -> future.complete(false));
    }

    private void errorLoop(BufferedReader reader) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    PyTrialLog.warn("[python] " + line);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void handleEvent(String line) {
        if (line.isBlank()) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
            String event = json.has("event") ? json.get("event").getAsString() : "";
            switch (event) {
                case "log" -> PyTrialLog.info("[python] " + text(json, "message"));
                case "register_block" -> queueBlock(text(json, "namespace"), text(json, "path"),
                        display(json), texture(json));
                case "register_item" -> queueItem(text(json, "namespace"), text(json, "path"), display(json));
                case "register_command" -> {
                    CommandBridge.add(text(json, "name"), text(json, "description"));
                    PyTrialLog.info("[python] command requested: /" + text(json, "name"));
                }
                case "register" -> registerContent(json);
                case "register_gui" -> PyTrialLog.warn("[python] gui registration is not available at this "
                        + "stage, ignored: " + text(json, "id"));
                case "open_gui", "close_gui" -> PyTrialLog.warn("[python] gui request is not available at this "
                        + "stage, ignored");
                case "declared", "loaded" -> complete(text(json, "id"), true);
                case "error" -> {
                    PyTrialLog.error("[python] " + text(json, "message"));
                    if (json.has("id")) {
                        complete(text(json, "id"), false);
                    }
                }
                default -> {
                    if (event.startsWith("register_")) {
                        // register_<kind> helpers send their own event name; normalise and route them
                        String kind = event.substring("register_".length());
                        json.addProperty("kind", kind);
                        routeExtra(kind, json);
                    } else {
                        PyTrialLog.debug("Unhandled Python event: " + event);
                    }
                }
            }
        } catch (Throwable t) {
            PyTrialLog.warn("Cannot parse Python output: " + line);
        }
    }

    private void complete(String id, boolean success) {
        if (id == null) {
            return;
        }
        CompletableFuture<Boolean> future = pendingLoads.get(id);
        if (future != null) {
            future.complete(success);
        }
    }

    private static void queueBlock(String namespace, String path, String display, String texture) {
        if (ContentRegistry.isApplied()) {
            PyTrialLog.warn("[python] block registration arrived after the registry was frozen, ignored: "
                    + namespace + ":" + path + " (declare it in on_declare)");
            return;
        }
        ContentRegistry.addBlock(namespace, path, display, texture);
    }

    private static void queueItem(String namespace, String path, String display) {
        if (ContentRegistry.isApplied()) {
            PyTrialLog.warn("[python] item registration arrived after the registry was frozen, ignored: "
                    + namespace + ":" + path + " (declare it in on_declare)");
            return;
        }
        ContentRegistry.addItem(namespace, path, display);
    }

    /** Optional texture source of a block: a resource id, a URL or an absolute file path. */
    private static String texture(JsonObject json) {
        String texture = text(json, "texture");
        return texture == null || texture.isBlank() ? null : texture.trim();
    }

    /** Optional display name of an element: "name", or "title" for creative tabs. */
    private static String display(JsonObject json) {
        String name = text(json, "name");
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        String title = text(json, "title");
        return title == null || title.isBlank() ? null : title.trim();
    }

    private static void registerContent(JsonObject json) {
        String kind = text(json, "kind");
        if (kind == null) {
            PyTrialLog.warn("[python] registration without a kind, ignored");
            return;
        }
        switch (kind) {
            case "block" -> queueBlock(text(json, "namespace"), text(json, "path"), display(json),
                    texture(json));
            case "item" -> queueItem(text(json, "namespace"), text(json, "path"), display(json));
            case "creative_tab" -> {
                if (ContentRegistry.isApplied()) {
                    PyTrialLog.warn("[python] creative tab registration arrived after the registry was frozen, "
                            + "ignored: " + text(json, "namespace") + ":" + text(json, "path")
                            + " (declare it in on_declare)");
                    break;
                }
                ContentRegistry.addCreativeTab(text(json, "namespace"), text(json, "path"),
                        text(json, "title"), text(json, "icon"), stringList(json, "items"),
                        text(json, "row"));
            }
            default -> routeExtra(kind, json);
        }
    }

    /**
     * Kinds outside the block/item/tab trio: data driven content becomes a data pack file, single
     * registry objects go through the registry bridge, and anything else reports why.
     */
    private static void routeExtra(String kind, JsonObject json) {
        String namespace = text(json, "namespace");
        String path = text(json, "path");
        if (namespace == null || path == null) {
            PyTrialLog.warn("[python] " + kind + " registration without a namespace/path, ignored");
            return;
        }
        if (ContentRegistry.isApplied()) {
            PyTrialLog.warn("[python] " + kind + " " + namespace + ":" + path
                    + " arrived after the registry was frozen, ignored (declare it in on_declare)");
            return;
        }
        if (DataPackWriter.handles(kind)) {
            ContentRegistry.addData(kind, namespace, path, text(json, "registry"), payloadOf(json));
            return;
        }
        RegistryBridge bridge = registryBridge();
        if (bridge != null && bridge.handles(kind)) {
            Object registered = bridge.register(kind, namespace, path, json);
            if (registered != null) {
                PyTrialLog.info("Registered " + kind + " " + namespace + ":" + path);
            }
            return;
        }
        PyTrialLog.warn("[python] registration of '" + kind + "' is not supported yet, ignored");
    }

    /** The declaration without the transport fields, which is the body of the generated JSON file. */
    private static JsonObject payloadOf(JsonObject json) {
        JsonObject payload = new JsonObject();
        json.entrySet().forEach(entry -> {
            switch (entry.getKey()) {
                case "event", "kind", "namespace", "path", "registry", "id" -> {
                    // transport only
                }
                default -> payload.add(entry.getKey(), entry.getValue());
            }
        });
        return payload;
    }

    private static synchronized RegistryBridge registryBridge() {
        if (registryBridge == null) {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) {
                loader = SubprocessPythonEngine.class.getClassLoader();
            }
            registryBridge = new RegistryBridge(loader);
        }
        return registryBridge;
    }

    private static List<String> stringList(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        json.getAsJsonArray(key).forEach(element -> {
            if (!element.isJsonNull()) {
                values.add(element.getAsString());
            }
        });
        return values;
    }

    private static String text(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }

    private final class Runtime implements PythonRuntime {

        private final ModCandidate mod;
        private final Path configDir;
        private volatile boolean loaded;

        Runtime(ModCandidate mod, Path configDir) {
            this.mod = mod;
            this.configDir = configDir;
        }

        @Override
        public String modId() {
            return mod.id();
        }

        @Override
        public void loadModule() {
            if (!alive) {
                throw new PythonEngineException("Python process has exited");
            }
            JsonObject message = new JsonObject();
            message.addProperty("kind", "load");
            message.addProperty("phase", "declare");
            message.addProperty("id", mod.id());
            message.addProperty("version", mod.metadata().version());
            message.addProperty("entry", mod.pythonEntry().toString());
            message.addProperty("root", mod.root().toString());
            message.addProperty("config", configDir.toString());
            message.addProperty("minecraftVersion", environment.minecraftVersion());

            CompletableFuture<Boolean> future = new CompletableFuture<>();
            pendingLoads.put(mod.id(), future);
            send(message);
            try {
                Boolean result = future.get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (result == null || !result) {
                    throw new PythonEngineException("Failed to load the Python module: " + mod.id());
                }
                loaded = true;
            } catch (PythonEngineException e) {
                throw e;
            } catch (Throwable t) {
                throw new PythonEngineException("Timed out waiting for the Python module to load: " + mod.id(), t);
            } finally {
                pendingLoads.remove(mod.id());
            }
        }

        @Override
        public void onLoad() {
            if (!alive || !loaded) {
                throw new PythonEngineException("Python module is not loaded: " + mod.id());
            }
            JsonObject message = new JsonObject();
            message.addProperty("kind", "activate");
            message.addProperty("id", mod.id());
            message.addProperty("minecraftVersion", environment.minecraftVersion());

            CompletableFuture<Boolean> future = new CompletableFuture<>();
            pendingLoads.put(mod.id(), future);
            send(message);
            try {
                Boolean result = future.get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (result == null || !result) {
                    throw new PythonEngineException("Failed to activate the Python module: " + mod.id());
                }
            } catch (PythonEngineException e) {
                throw e;
            } catch (Throwable t) {
                throw new PythonEngineException("Timed out waiting for the Python module to activate: "
                        + mod.id(), t);
            } finally {
                pendingLoads.remove(mod.id());
            }
        }

        @Override
        public void onUnload() {
            JsonObject message = new JsonObject();
            message.addProperty("kind", "unload");
            send(message);
        }

        @Override
        public Object callHook(String hookId, Object[] args) {
            JsonObject message = new JsonObject();
            message.addProperty("kind", "hook");
            message.addProperty("id", hookId);
            send(message);
            return null;
        }

        @Override
        public void postEvent(String eventId, Object payload) {
            JsonObject message = new JsonObject();
            message.addProperty("kind", "event");
            message.addProperty("id", eventId);
            message.add("payload", payload == null ? JsonNull.INSTANCE : Json.PRETTY.toJsonTree(payload));
            send(message);
        }

        @Override
        public boolean isAlive() {
            return alive && loaded;
        }

        @Override
        public void close() {
            loaded = false;
        }
    }
}
