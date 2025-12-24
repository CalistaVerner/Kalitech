package org.foxesworld.kalitech.engine.script.events;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class ScriptEventBus {

    private static final Logger log = LogManager.getLogger(ScriptEventBus.class);

    public record Event(String name, Object payload) {}

    private final Map<String, CopyOnWriteArrayList<Subscription>> handlers = new ConcurrentHashMap<>();
    private final Queue<Event> queue = new ConcurrentLinkedQueue<>();

    private final AtomicInteger nextId = new AtomicInteger(1);

    private record Subscription(int id, boolean once, Value fn) {}

    /**
     * Register a JS handler. Returns a token used for off().
     *
     * <p>We intentionally accept Value instead of a Java functional interface,
     * because HostAccess is restricted (safer) and JS functions map naturally to Value.
     */
    public int on(String eventName, Value handler, boolean once) {
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("eventName is blank");
        }
        if (handler == null || handler.isNull() || !handler.canExecute()) {
            throw new IllegalArgumentException("handler must be an executable JS function");
        }
        int id = nextId.getAndIncrement();
        handlers.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>())
                .add(new Subscription(id, once, handler));
        log.debug("EventBus: handler#{} registered for '{}' (once={})", id, eventName, once);
        return id;
    }

    public boolean off(String eventName, int token) {
        CopyOnWriteArrayList<Subscription> list = handlers.get(eventName);
        if (list == null || list.isEmpty()) return false;
        boolean removed = list.removeIf(s -> s.id == token);
        if (removed) log.debug("EventBus: handler#{} removed for '{}'", token, eventName);
        return removed;
    }

    public void clear(String eventName) {
        if (eventName == null) return;
        handlers.remove(eventName);
        log.debug("EventBus: cleared '{}'", eventName);
    }

    public void emit(String eventName, Object payload) {
        queue.add(new Event(eventName, payload));
    }

    /** Вызывать строго в игровом треде (update). */
    public void pump() {
        Event e;
        while ((e = queue.poll()) != null) {
            CopyOnWriteArrayList<Subscription> list = handlers.get(e.name());
            if (list == null || list.isEmpty()) continue;

            for (Subscription s : list) {
                try {
                    if (s.fn != null && s.fn.canExecute()) {
                        // If payload is null - pass no args (nicer for JS)
                        if (e.payload() == null) s.fn.executeVoid();
                        else s.fn.executeVoid(e.payload());
                    }
                } catch (Throwable ex) {
                    log.error("EventBus handler error for '{}' (handler#{})", e.name(), s.id, ex);
                } finally {
                    if (s.once) {
                        // CopyOnWriteArrayList allows safe removal during iteration
                        list.remove(s);
                    }
                }
            }
        }
    }

    public void clearAll() {
        handlers.clear();
        queue.clear();
    }
}