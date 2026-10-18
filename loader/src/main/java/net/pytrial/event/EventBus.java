package net.pytrial.event;

import net.pytrial.util.PyTrialLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EventBus {

    public static final int PRIORITY_HIGHEST = 100;
    public static final int PRIORITY_HIGH = 50;
    public static final int PRIORITY_NORMAL = 0;
    public static final int PRIORITY_LOW = -50;
    public static final int PRIORITY_LOWEST = -100;
    public static final int PRIORITY_OBSERVER = -1000;

    private final Map<Class<?>, CopyOnWriteArrayList<Registration<?>>> listeners = new ConcurrentHashMap<>();

    public <T extends Event> void register(Class<T> type, Consumer<T> listener) {
        register(type, PRIORITY_NORMAL, listener);
    }

    public <T extends Event> void register(Class<T> type, int priority, Consumer<T> listener) {
        CopyOnWriteArrayList<Registration<?>> list =
                listeners.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>());
        list.add(new Registration<>(priority, listener));
        List<Registration<?>> sorted = new ArrayList<>(list);
        sorted.sort((left, right) -> Integer.compare(right.priority(), left.priority()));
        list.clear();
        list.addAll(sorted);
    }

    public <T extends Event> boolean unregister(Class<T> type, Consumer<T> listener) {
        CopyOnWriteArrayList<Registration<?>> list = listeners.get(type);
        if (list == null) {
            return false;
        }
        return list.removeIf(registration -> registration.listener() == listener);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends Event> T post(T event) {
        CopyOnWriteArrayList<Registration<?>> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return event;
        }
        for (Registration registration : list) {
            try {
                ((Consumer) registration.listener()).accept(event);
            } catch (Throwable t) {
                PyTrialLog.error("Event listener threw an exception: " + event.getClass().getName(), t);
            }
        }
        return event;
    }

    public void clear() {
        listeners.clear();
    }

    private record Registration<T extends Event>(int priority, Consumer<T> listener) {
    }
}
