package net.pytrial.bridge;

import net.pytrial.util.PyTrialLog;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class HookDispatcher {

    private static final Object[] NO_ARGS = new Object[0];
    private static final AtomicLong INVOCATIONS = new AtomicLong();
    private static final AtomicBoolean OVERLAY_PROBE = new AtomicBoolean();

    private static volatile HookSink sink;
    private static volatile boolean installed;

    private HookDispatcher() {
    }

    public static void install(HookSink hookSink) {
        sink = hookSink;
        installed = hookSink != null;
        PyTrialLog.debug("HookDispatcher sink installed: " + (sink != null));
    }

    public static boolean isInstalled() {
        return installed;
    }

    public static long invocations() {
        return INVOCATIONS.get();
    }

    public static void onHead(String hookId) {
        dispatch(hookId, NO_ARGS);
    }

    public static void onReturn(String hookId) {
        dispatch(hookId, NO_ARGS);
    }

    public static void onBeforeInvoke(String hookId) {
        dispatch(hookId, NO_ARGS);
    }

    public static void onAfterInvoke(String hookId) {
        dispatch(hookId, NO_ARGS);
    }

    public static void onCommandsCreated(Object commands) {
        HookSink current = sink;
        if (current == null) {
            return;
        }
        try {
            current.onCommandsCreated(commands);
        } catch (Throwable t) {
            PyTrialLog.error("Error while registering commands (ignored)", t);
        }
    }

    public static void onRegistryBootStrap() {
        HookSink current = sink;
        if (current == null) {
            return;
        }
        try {
            current.onRegistryBootStrap();
        } catch (Throwable t) {
            PyTrialLog.error("Error while registering game content (ignored)", t);
        }
    }

    public static void onOverlayRender(Object overlay, Object graphics, int width, int height) {
        if (OVERLAY_PROBE.compareAndSet(false, true)) {
            PyTrialLog.info("Loading screen callback reached the dispatcher (sink installed: "
                    + (sink != null) + ")");
        }
        HookSink current = sink;
        if (current == null) {
            return;
        }
        try {
            current.onOverlayRender(overlay, graphics, width, height);
        } catch (Throwable t) {
            PyTrialLog.error("Error while updating the loading screen (ignored)", t);
        }
    }

    public static boolean adjustFadeOut(boolean ready) {
        HookSink current = sink;
        if (current == null) {
            return ready;
        }
        try {
            return current.adjustFadeOut(ready);
        } catch (Throwable t) {
            PyTrialLog.error("Error while checking the loading screen fade state (ignored)", t);
            return ready;
        }
    }

    public static void dispatch(String hookId, Object[] args) {
        HookSink current = sink;
        if (current == null) {
            return;
        }
        INVOCATIONS.incrementAndGet();
        try {
            current.onHook(hookId, args != null ? args : NO_ARGS);
        } catch (Throwable t) {
            PyTrialLog.error("Error while handling hook " + hookId + " (ignored)", t);
        }
    }
}
