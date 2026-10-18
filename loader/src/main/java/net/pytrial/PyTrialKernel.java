package net.pytrial;

import net.pytrial.bridge.HookDispatcher;
import net.pytrial.bridge.HookSink;
import net.pytrial.discovery.ModCandidate;
import net.pytrial.discovery.ModDiscoverer;
import net.pytrial.engine.PythonEngine;
import net.pytrial.engine.PythonEngineFactory;
import net.pytrial.engine.PythonRuntime;
import net.pytrial.event.EventBus;
import net.pytrial.event.Events;
import net.pytrial.game.CommandBridge;
import net.pytrial.game.ContentRegistry;
import net.pytrial.game.ModResourcePack;
import net.pytrial.game.GameBridge;
import net.pytrial.lifecycle.LoaderPhase;
import net.pytrial.lifecycle.ModContainer;
import net.pytrial.lifecycle.ModRegistry;
import net.pytrial.lifecycle.ModState;
import net.pytrial.mapping.MappingLoader;
import net.pytrial.mapping.MappingSet;
import net.pytrial.resolve.DependencyResolver;
import net.pytrial.transform.AsmCommandsTransformer;
import net.pytrial.transform.AsmCreativeTabsTransformer;
import net.pytrial.transform.AsmItemNameTransformer;
import net.pytrial.transform.AsmTitleScreenTransformer;
import net.pytrial.ui.PyTrialSettings;
import net.pytrial.transform.AsmLanguageTransformer;
import net.pytrial.transform.AsmHookTransformer;
import net.pytrial.transform.AsmOverlayTransformer;
import net.pytrial.transform.AsmRegistryTransformer;
import net.pytrial.transform.HookRule;
import net.pytrial.transform.TransformPipeline;
import net.pytrial.ui.LoadingWindow;
import net.pytrial.ui.FailureScreen;
import net.pytrial.ui.SplashRenderer;
import net.pytrial.util.PyTrialLog;

import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PyTrialKernel implements HookSink {

    public static final String HOOK_GAME_START = "pytrial:game_start";

    private static final long MIN_WINDOW_MILLIS = 1500L;

    private static final PyTrialKernel INSTANCE = new PyTrialKernel();

    private final EventBus eventBus = new EventBus();
    private final ModRegistry registry = new ModRegistry();
    private final TransformPipeline pipeline = new TransformPipeline();
    private final Map<String, ModContainer> hookIndex = new ConcurrentHashMap<>();
    private final AtomicBoolean prepared = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean loadingStarted = new AtomicBoolean();
    private final AtomicBoolean loadingDone = new AtomicBoolean();
    private volatile boolean loadingFailed;
    private final AtomicBoolean contentRegistered = new AtomicBoolean();

    private volatile PyTrialEnvironment environment;
    private volatile PythonEngine engine;
    private volatile List<ModContainer> loadOrder = List.of();
    private volatile AsmHookTransformer asmTransformer;
    private volatile LoadingWindow loadingWindow;
    private volatile SplashRenderer splashRenderer;
    private volatile MappingSet mappings = MappingSet.EMPTY;
    private volatile String status = "";
    private volatile float progress;
    private volatile long windowOpenedAt;

    private PyTrialKernel() {
    }

    public static PyTrialKernel get() {
        return INSTANCE;
    }

    public void prepare(PyTrialEnvironment env, Instrumentation instrumentation) {
        if (!prepared.compareAndSet(false, true)) {
            PyTrialLog.warn("Kernel already initialised, ignoring duplicate call");
            return;
        }

        PyTrialLog.info("pyTrial " + PyTrial.version() + " starting (game dir: " + env.gameDir() + ")");
        this.environment = env;
        env.prepare();
        PyTrialLog.attachFile(env.installDir().resolve("logs").resolve("loader.log"));
        PyTrialSettings.attach(env.installDir());
        ModResourcePack.ensureBeforeGameStart(env.installDir().getParent());

        windowOpenedAt = System.currentTimeMillis();
        setStatus("Preparing pyTrial...");
        setProgress(0.15f);

        this.mappings = MappingLoader.load(env);

        registry.setPhase(LoaderPhase.DISCOVERY);
        eventBus.post(new Events.LoaderInitialized(env.gameDir(), env.modsDir(), env.minecraftVersion()));

        List<ModCandidate> accepted = new ArrayList<>();
        Path unpackDir = env.cacheDir().resolve("mods");
        for (ModCandidate candidate : ModDiscoverer.discover(env.modsDir(), unpackDir)) {
            Events.ModDiscovered event = eventBus.post(new Events.ModDiscovered(candidate.id(), candidate.root()));
            if (event.isCancelled()) {
                PyTrialLog.info("Mod " + candidate.id() + " cancelled by a listener");
                continue;
            }
            accepted.add(candidate);
        }

        setStatus("Found " + accepted.size() + " mod(s), resolving order...");
        setProgress(0.35f);
        registry.setPhase(LoaderPhase.RESOLUTION);
        List<ModContainer> ordered = List.of();
        try {
            ordered = DependencyResolver.resolve(accepted);
        } catch (RuntimeException e) {
            PyTrialLog.error("Dependency resolution failed, no Python mod will be loaded: " + e.getMessage());
        }

        registry.addAll(ordered);
        loadOrder = ordered;
        setStatus("Resolved " + ordered.size() + " mod(s), registering hooks...");
        setProgress(0.5f);
        ordered.forEach(container ->
                container.hooks().forEach(rule -> hookIndex.put(rule.id(), container)));
        eventBus.post(new Events.ModsResolved(registry.modIds()));

        registry.setPhase(LoaderPhase.TRANSFORM);
        List<HookRule> rules = new ArrayList<>();
        ordered.forEach(container -> rules.addAll(container.hooks()));

        if (!rules.isEmpty()) {
            asmTransformer = new AsmHookTransformer(rules);
            pipeline.add(asmTransformer);
            PyTrialLog.info("Registered the hook transformer with " + rules.size() + " injection rule(s)");
        }

        pipeline.add(new AsmRegistryTransformer());
        pipeline.add(new AsmCommandsTransformer());

        AsmCreativeTabsTransformer creativeTabsTransformer = new AsmCreativeTabsTransformer();
        pipeline.add(creativeTabsTransformer);
        PyTrialLog.info("Creative tab injection target: " + creativeTabsTransformer.resolvedTarget());

        AsmLanguageTransformer languageTransformer = new AsmLanguageTransformer();
        pipeline.add(languageTransformer);
        PyTrialLog.info("Language injection target: " + languageTransformer.resolvedTarget());

        AsmItemNameTransformer itemNameTransformer = new AsmItemNameTransformer();
        pipeline.add(itemNameTransformer);
        PyTrialLog.info("Item name injection target: " + itemNameTransformer.resolvedTarget());

        AsmTitleScreenTransformer titleScreenTransformer = new AsmTitleScreenTransformer();
        pipeline.add(titleScreenTransformer);
        PyTrialLog.info("Title screen injection target: " + titleScreenTransformer.resolvedTarget());

        AsmOverlayTransformer overlayTransformer = new AsmOverlayTransformer(mappings);
        if (overlayTransformer.isResolved()) {
            splashRenderer = new SplashRenderer(mappings);
            pipeline.add(overlayTransformer);
            PyTrialLog.info("Loading screen injection target: " + overlayTransformer.resolvedTarget());
        }

        if (instrumentation != null && !pipeline.isEmpty()) {
            try {
                instrumentation.addTransformer(pipeline, true);
            } catch (Throwable t) {
                PyTrialLog.error("Failed to register the bytecode transformer", t);
            }
        }

        HookDispatcher.install(this);
        ContentRegistry.addBlock("pytrial", "python_block", "Python Block", null);
        CommandBridge.add("pytrial", "pyTrial status");
        setStatus("Ready: " + registry.size() + " mod(s) resolved");
        setProgress(0.65f);
        PyTrialLog.info("Preparation done: " + registry.size() + " mod(s) resolved, waiting for the game to start");
        declareMods();
    }

    public void onGameStart() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        PyTrialLog.info("Loading Python mods");
        registry.setPhase(LoaderPhase.INITIALIZATION);
        eventBus.post(new Events.GameStarting());

        setStatus(loadOrder.isEmpty()
                ? "No python mod found, starting game..."
                : "Loading " + loadOrder.size() + " mod(s)...");
        setProgress(0.75f);

        if (engine == null) {
            if (!loadOrder.isEmpty()) {
                reportMissingEngine();
            }
        } else {
            for (int i = 0; i < loadOrder.size(); i++) {
                ModContainer container = loadOrder.get(i);
                if (container.state() == ModState.LOADING) {
                    activateMod(container, i + 1, loadOrder.size());
                }
            }
        }

        setStatus(summary());

        registry.setPhase(LoaderPhase.STARTED);
        logSummary();
        installShutdownHook();
        reportLoadingResult();
        if (!loadingFailed) {
            broadcast("mods_loaded", Map.<String, Object>of("count", loadOrder.size()));
        }
    }

    private void reportLoadingResult() {
        List<String> failures = new ArrayList<>();
        for (ModContainer container : loadOrder) {
            if (container.state() != ModState.LOADED) {
                String reason = container.failureReason();
                failures.add(container.id() + (reason != null ? " (" + reason + ")" : ""));
            }
        }
        if (failures.isEmpty()) {
            loadingDone.set(true);
            return;
        }
        loadingFailed = true;
        PyTrialLog.error("Mod loading failed, the game will not start: " + String.join(", ", failures));
        if (environment != null) {
            FailureScreen.showLater(failures, environment.installDir().resolve("logs").resolve("loader.log"));
        }
    }

    private void broadcast(String eventId, Map<String, Object> payload) {
        for (ModContainer container : loadOrder) {
            container.runtime().ifPresent(runtime -> {
                try {
                    runtime.postEvent(eventId, payload);
                } catch (Throwable t) {
                    PyTrialLog.warn("Could not deliver event " + eventId + " to " + container.id() + ": "
                            + t.getMessage());
                }
            });
        }
    }

    private String summary() {
        StringBuilder sb = new StringBuilder("pyTrial ").append(PyTrial.version());
        if (loadOrder.isEmpty()) {
            return sb.append(" - no python mod").toString();
        }
        sb.append(" - ").append(registry.size()).append(" mod(s): ");
        int shown = 0;
        for (ModContainer container : registry.mods()) {
            if (shown > 0) {
                sb.append(", ");
            }
            sb.append(container.id());
            shown++;
            if (shown == 3 && registry.size() > 3) {
                sb.append(", ...");
                break;
            }
        }
        return sb.toString();
    }

    private void declareMods() {
        if (loadOrder.isEmpty()) {
            return;
        }
        Optional<PythonEngine> discovered = PythonEngineFactory.discover();
        if (discovered.isEmpty()) {
            reportMissingEngine();
            return;
        }
        engine = discovered.get();
        try {
            engine.initialize(environment);
            PyTrialLog.info("Python engine ready: " + engine.id() + " " + engine.version());
        } catch (Throwable t) {
            PyTrialLog.error("Python engine initialisation failed, no mod will be loaded", t);
            loadOrder.forEach(container -> container.setState(ModState.DISABLED));
            engine = null;
            return;
        }
        for (int i = 0; i < loadOrder.size(); i++) {
            declareMod(loadOrder.get(i), i + 1, loadOrder.size());
        }
    }

    private void declareMod(ModContainer container, int index, int total) {
        setStatus("Declaring mod " + index + "/" + total + ": " + container.id());
        try {
            Path configDir = environment.configDirOf(container.id());
            Files.createDirectories(configDir);
            PythonRuntime runtime = engine.createRuntime(container.candidate(), configDir);
            runtime.loadModule();
            container.attachRuntime(runtime);
            container.setState(ModState.LOADING);
            PyTrialLog.info("Declared mod " + container.id() + " " + container.metadata().version());
        } catch (Throwable t) {
            container.markFailed(t.getMessage() != null ? t.getMessage() : t.toString());
            PyTrialLog.error("Failed to declare mod " + container.id() + ": " + container.failureReason(), t);
        }
    }

    private void activateMod(ModContainer container, int index, int total) {
        setStatus("Loading mod " + index + "/" + total + ": "
                + container.id() + " " + container.metadata().version());
        setProgress(0.75f + 0.2f * index / Math.max(1, total));

        eventBus.post(new Events.ModLoading(container.id()));
        try {
            PythonRuntime runtime = container.runtime().orElse(null);
            if (runtime == null) {
                throw new IllegalStateException("mod was not declared: " + container.id());
            }
            runtime.onLoad();

            container.attachRuntime(runtime);
            container.setState(ModState.LOADED);
            eventBus.post(new Events.ModLoaded(container.id()));
            PyTrialLog.info("Loaded mod " + container.id() + " " + container.metadata().version());
            broadcast("mod_loaded", Map.<String, Object>of("id", container.id()));
        } catch (Throwable t) {
            container.markFailed(t.getMessage() != null ? t.getMessage() : t.toString());
            PyTrialLog.error("Failed to load mod " + container.id() + ": " + container.failureReason(), t);
        }
    }

    @Override
    public Object onHook(String hookId, Object[] args) {
        if (HOOK_GAME_START.equals(hookId)) {
            onGameStart();
            return null;
        }

        PythonEngine current = engine;
        if (current == null) {
            return null;
        }
        ModContainer owner = hookIndex.get(hookId);
        if (owner == null) {
            return null;
        }
        return owner.runtime().map(runtime -> runtime.callHook(hookId, args)).orElse(null);
    }

    @Override
    public void onOverlayRender(Object overlay, Object graphics, int mouseX, int mouseY) {
        if (!loadingStarted.get()) {
            return;
        }
        SplashRenderer renderer = splashRenderer;
        if (renderer != null) {
            renderer.render(overlay, graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean adjustFadeOut(boolean ready) {
        if (ready && !loadingStarted.get()) {
            startModLoading();
        }
        if (loadingFailed) {
            return true;
        }
        return ready && loadingDone.get();
    }

    private void startModLoading() {
        if (!loadingStarted.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                onGameStart();
            } catch (Throwable t) {
                PyTrialLog.error("Mod loading failed", t);
            }
        }, "pyTrial-modloader");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void onRegistryBootStrap() {
        if (!contentRegistered.compareAndSet(false, true)) {
            return;
        }
        setStatus("Registering content...");
        setProgress(0.9f);
        List<ContentRegistry.BlockEntry> blocks = ContentRegistry.apply(
                new GameBridge(PyTrialKernel.class.getClassLoader()));
        PyTrialLog.info("Content registration done: " + blocks.size() + " block(s)");
        ModResourcePack.install(environment.installDir().getParent(), blocks);

        java.util.List<PyTrialSettings.ModEntry> entries = new java.util.ArrayList<>();
        for (ModContainer container : registry.mods()) {
            entries.add(new PyTrialSettings.ModEntry(container.id(), String.valueOf(container.state()),
                    container.runtime().isPresent()));
        }
        PyTrialSettings.setModEntries(entries);

        setProgress(1f);
        setStatus(summary());
    }

    @Override
    public void onCommandsCreated(Object commands) {
        int registered = new CommandBridge(this::currentStatus).apply(commands);
        if (registered > 0) {
            PyTrialLog.info("Command registration done: " + registered + " command(s)");
        }
    }

    public String currentStatus() {
        return status;
    }

    private void setStatus(String text) {
        this.status = text;
        PyTrialLog.status(text);
        LoadingWindow window = loadingWindow;
        if (window != null) {
            window.setStatus(text);
        }
    }

    private void setProgress(float value) {
        this.progress = Math.max(this.progress, Math.min(1f, value));
    }

    private void waitForMinimumWindowTime() {
        long elapsed = System.currentTimeMillis() - windowOpenedAt;
        if (elapsed >= MIN_WINDOW_MILLIS) {
            return;
        }
        try {
            Thread.sleep(MIN_WINDOW_MILLIS - elapsed);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeWindow() {
        LoadingWindow window = loadingWindow;
        loadingWindow = null;
        if (window != null) {
            window.close();
        }
    }

    public synchronized void shutdown() {
        if (registry.phase() == LoaderPhase.SHUTDOWN) {
            return;
        }
        LoadingWindow window = loadingWindow;
        if (window != null) {
            window.close();
            loadingWindow = null;
        }
        registry.setPhase(LoaderPhase.SHUTDOWN);
        eventBus.post(new Events.LoaderShutdown());
        broadcast("loader_shutdown", Map.<String, Object>of());

        for (ModContainer container : loadOrder) {
            container.runtime().ifPresent(runtime -> {
                try {
                    runtime.onUnload();
                    runtime.close();
                } catch (Throwable t) {
                    PyTrialLog.warn("Error while shutting down mod " + container.id() + ": " + t.getMessage());
                }
            });
        }
        if (engine != null) {
            try {
                engine.close();
            } catch (Throwable t) {
                PyTrialLog.warn("Error while shutting down the Python engine: " + t.getMessage());
            }
            engine = null;
        }
        HookDispatcher.install(null);
        PyTrialLog.info("pyTrial shut down (hook invocations: " + HookDispatcher.invocations() + ")");
    }

    private void installShutdownHook() {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "pyTrial-shutdown"));
        } catch (IllegalStateException ignored) {
        }
    }

    private void reportMissingEngine() {
        PyTrialLog.warn("No usable Python engine implementation found (the Python runtime is intentionally left unimplemented)");
        if (!loadOrder.isEmpty()) {
            PyTrialLog.warn("Resolved " + loadOrder.size() + " mod(s); they will be marked DISABLED: "
                    + DependencyResolver.idsOf(loadOrder));
            loadOrder.forEach(container -> container.setState(ModState.DISABLED));
        }
        PyTrialLog.warn("See docs/python-engine.md for how to plug an engine in");
    }

    private void logSummary() {
        PyTrialLog.info("Load result: " + registry.size() + " mod(s), "
                + registry.mods().stream().filter(container -> container.state() == ModState.LOADED).count()
                + " loaded");
        for (ModContainer container : registry.mods()) {
            PyTrialLog.info("  - " + container.id() + " " + container.metadata().version()
                    + " [" + container.state() + "]"
                    + (container.failureReason() != null ? " " + container.failureReason() : ""));
        }
    }

    public PyTrialEnvironment environment() {
        return environment;
    }

    public ModRegistry registry() {
        return registry;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public TransformPipeline pipeline() {
        return pipeline;
    }

    public Optional<PythonEngine> engine() {
        return Optional.ofNullable(engine);
    }

    public List<String> appliedHooks() {
        AsmHookTransformer transformer = asmTransformer;
        return transformer != null ? transformer.appliedHooks() : List.of();
    }

    public static void main(String[] args) {
        System.out.println("pyTrial Loader " + PyTrial.version());
        System.out.println("This jar is mounted into the game process via -javaagent; start the game with the bootstrap:");
        System.out.println("  java -jar pytrial-bootstrap.jar install");
        System.out.println("See docs/python-engine.md to plug in your own Python engine");
    }
}
