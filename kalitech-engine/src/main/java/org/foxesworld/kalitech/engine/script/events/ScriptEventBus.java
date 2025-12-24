// FILE: ScriptEventBus.java
package org.foxesworld.kalitech.engine.script.events;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

import java.util.Arrays;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ScriptEventBus — engine-side event queue + JS handler registry.
 *
 * <p>Design goals:
 * <ul>
 *   <li>Hot path (pump) is allocation-free and lock-free: reads use snapshot arrays.</li>
 *   <li>Mutations (on/off) are synchronized per-event list and are relatively rare.</li>
 *   <li>Supports owner/module-based cleanup to prevent Value leaks on entity destroy/hot-reload.</li>
 * </ul>
 */
public final class ScriptEventBus {

    private static final Logger log = LogManager.getLogger(ScriptEventBus.class);

    public record Event(String name, Object payload) {}

    private final Map<String, HandlerList> handlers = new ConcurrentHashMap<>();
    private final Queue<Event> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    private static final class Subscription {
        final int id;
        final boolean once;
        final long owner;        // entityId or 0
        final String moduleId;   // optional
        final Value fn;

        Subscription(int id, boolean once, long owner, String moduleId, Value fn) {
            this.id = id;
            this.once = once;
            this.owner = owner;
            this.moduleId = moduleId;
            this.fn = fn;
        }
    }

    /**
     * Per-event list with snapshot array for lock-free reads in pump().
     */
    private static final class HandlerList {
        private volatile Subscription[] snapshot = new Subscription[0];

        int add(Subscription s) {
            synchronized (this) {
                Subscription[] cur = snapshot;
                Subscription[] next = Arrays.copyOf(cur, cur.length + 1);
                next[cur.length] = s;
                snapshot = next;
                return s.id;
            }
        }

        boolean removeById(int id) {
            synchronized (this) {
                Subscription[] cur = snapshot;
                for (int i = 0; i < cur.length; i++) {
                    if (cur[i].id == id) {
                        Subscription[] next = new Subscription[cur.length - 1];
                        System.arraycopy(cur, 0, next, 0, i);
                        System.arraycopy(cur, i + 1, next, i, cur.length - i - 1);
                        snapshot = next;
                        return true;
                    }
                }
                return false;
            }
        }

        int removeIf(java.util.function.Predicate<Subscription> pred) {
            synchronized (this) {
                Subscription[] cur = snapshot;
                if (cur.length == 0) return 0;

                int keep = 0;
                for (Subscription s : cur) if (!pred.test(s)) keep++;

                if (keep == cur.length) return 0;
                Subscription[] next = new Subscription[keep];
                int j = 0;
                for (Subscription s : cur) if (!pred.test(s)) next[j++] = s;
                snapshot = next;
                return cur.length - keep;
            }
        }

        Subscription[] snapshot() {
            return snapshot;
        }

        boolean isEmpty() {
            return snapshot.length == 0;
        }
    }

    // --------- API ---------

    /** Register a JS handler. Returns a token used for off(). */
    public int on(String eventName, Value handler) {
        return on(eventName, handler, false, 0L, null);
    }

    /** Register a one-shot JS handler. */
    public int once(String eventName, Value handler) {
        return on(eventName, handler, true, 0L, null);
    }

    /**
     * Register a handler bound to an owner (entityId) and optional moduleId.
     * Use offOwner/offByModule for cleanup on destroy/hot-reload.
     */
    public int onOwned(String eventName, Value handler, boolean once, long owner, String moduleId) {
        return on(eventName, handler, once, owner, moduleId);
    }

    private int on(String eventName, Value handler, boolean once, long owner, String moduleId) {
        if (eventName == null || eventName.isBlank()) throw new IllegalArgumentException("eventName is blank");
        if (handler == null || handler.isNull() || !handler.canExecute()) {
            throw new IllegalArgumentException("handler must be an executable JS function");
        }
        int id = nextId.getAndIncrement();
        HandlerList list = handlers.computeIfAbsent(eventName, k -> new HandlerList());
        list.add(new Subscription(id, once, owner, moduleId, handler));
        return id;
    }

    public boolean off(String eventName, int token) {
        HandlerList list = handlers.get(eventName);
        if (list == null) return false;
        boolean ok = list.removeById(token);
        if (ok && list.isEmpty()) handlers.remove(eventName, list);
        return ok;
    }

    /** Remove all handlers belonging to an owner (typically entityId). */
    public int offOwner(long owner) {
        if (owner == 0L) return 0;
        int removed = 0;
        for (var e : handlers.entrySet()) {
            HandlerList list = e.getValue();
            removed += list.removeIf(s -> s.owner == owner);
            if (list.isEmpty()) handlers.remove(e.getKey(), list);
        }
        return removed;
    }

    /** Remove all handlers registered from a specific module (hot-reload cleanup). */
    public int offByModule(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) return 0;
        int removed = 0;
        for (var e : handlers.entrySet()) {
            HandlerList list = e.getValue();
            removed += list.removeIf(s -> moduleId.equals(s.moduleId));
            if (list.isEmpty()) handlers.remove(e.getKey(), list);
        }
        return removed;
    }

    /** Enqueue an event (safe to call from any thread). */
    public void emit(String eventName, Object payload) {
        if (eventName == null || eventName.isBlank()) return;
        queue.add(new Event(eventName, payload));
    }

    /**
     * Pump queued events and execute handlers.
     *
     * <p>Call this strictly from the game/main thread (together with script execution).
     */
    public void pump() {
        Event e;
        while ((e = queue.poll()) != null) {
            HandlerList list = handlers.get(e.name());
            if (list == null) continue;

            Subscription[] snap = list.snapshot();
            if (snap.length == 0) continue;

            for (Subscription s : snap) {
                try {
                    if (s.fn != null && s.fn.canExecute()) {
                        if (e.payload() == null) s.fn.executeVoid();
                        else s.fn.executeVoid(e.payload());
                    }
                } catch (Throwable ex) {
                    log.error("EventBus handler error for '{}' (handler#{})", e.name(), s.id, ex);
                } finally {
                    if (s.once) list.removeById(s.id);
                }
            }

            if (list.isEmpty()) handlers.remove(e.name(), list);
        }
    }

    public void clearAll() {
        handlers.clear();
        queue.clear();
    }
}