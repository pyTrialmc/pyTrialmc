package net.pytrial.engine;

public interface PythonRuntime extends AutoCloseable {

    String modId();

    void loadModule() throws PythonEngineException;

    void onLoad();

    void onUnload();

    Object callHook(String hookId, Object[] args);

    void postEvent(String eventId, Object payload);

    boolean isAlive();

    @Override
    void close();
}
