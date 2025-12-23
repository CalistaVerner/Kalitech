package org.foxesworld.kalitech.engine.script.events;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ScriptEventBus {

    private static final Logger log = LogManager.getLogger(ScriptEventBus.class);

    public record Event(String name, Object payload) {}

    private final Map<String, List<Handler>> handlers = new HashMap<>();
    private final Queue<Event> queue = new ConcurrentLinkedQueue<>();

    @FunctionalInterface
    public interface Handler {
        void handle(Object payload);
    }

    public void on(String eventName, Handler handler) {
        handlers.computeIfAbsent(eventName, k -> new ArrayList<>()).add(handler);
        log.debug("EventBus: handler registered for '{}'", eventName);
    }

    public void emit(String eventName, Object payload) {
        queue.add(new Event(eventName, payload));
    }

    /** Вызывать строго в игровом треде (update). */
    public void pump() {
        Event e;
        while ((e = queue.poll()) != null) {
            List<Handler> list = handlers.get(e.name());
            if (list == null || list.isEmpty()) continue;

            for (Handler h : List.copyOf(list)) {
                try {
                    h.handle(e.payload());
                } catch (Exception ex) {
                    log.error("EventBus handler error for '{}'", e.name(), ex);
                }
            }
        }
    }

    public void clearAll() {
        handlers.clear();
        queue.clear();
    }
}