package net.pytrial.bridge;

@FunctionalInterface
public interface HookSink {

    default void onOverlayRender(Object overlay, Object graphics, int width, int height) {
    }

    default boolean adjustFadeOut(boolean ready) {
        return ready;
    }

    default void onRegistryBootStrap() {
    }

    default void onCommandsCreated(Object commands) {
    }

    Object onHook(String hookId, Object[] args);
}
