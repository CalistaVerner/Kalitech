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

public final class ScriptEventBus {

    private static final Logger log = LogManager.getLogger(ScriptEventBus.class);

    public static final int DEFAULT_MAX_EVENTS_PER_FRAME = 4096;
    public static final long DEFAULT_TIME_BUDGET_NANOS = 2_000_000L; // 2ms

    private record Event(String name, Object payload) {}

    // inside ScriptEventBus

    private static final class Sub {
        final int id;
        final Value fn;
        final boolean once;

        Sub(int id, Value fn, boolean once) {
            this.id = id;
            this.fn = fn;
            this.once = once;
        }
    }

    /**
     * Small, allocation-light subscription list.
     * Not thread-safe by itself; ScriptEventBus controls access patterns.
     */
    private static final class SubList {

        private Sub[] arr = new Sub[8];
        private int size = 0;

        int add(Sub s) {
            if (s == null) return 0;
            if (size >= arr.length) {
                arr = Arrays.copyOf(arr, arr.length << 1);
            }
            arr[size++] = s;
            return s.id;
        }

        boolean isEmpty() {
            return size == 0;
        }

        int size() {
            return size;
        }

        Sub get(int i) {
            // caller guarantees bounds
            return arr[i];
        }

        /** @return true if removed */
        boolean removeById(int id) {
            for (int i = 0; i < size; i++) {
                Sub s = arr[i];
                if (s != null && s.id == id) {
                    int last = size - 1;
                    arr[i] = arr[last];
                    arr[last] = null;
                    size = last;
                    return true;
                }
            }
            return false;
        }

        /** Optional: clear all subs (useful for reset). */
        void clear() {
            Arrays.fill(arr, 0, size, null);
            size = 0;
        }
    }

    private final AtomicInteger nextSubId = new AtomicInteger(1);
    private final Map<String, SubList> handlers = new ConcurrentHashMap<>();
    private final Queue<Event> queue = new ConcurrentLinkedQueue<>();

    public void emit(String name, Object payload) {
        if (name == null || name.isBlank()) return;
        queue.add(new Event(name.trim(), payload));
    }

    public void emit(String name) { emit(name, null); }

    public int on(String name, Value fn) { return on(name, fn, false); }
    public int once(String name, Value fn) { return on(name, fn, true); }

    private int on(String name, Value fn, boolean once) {
        if (name == null || name.isBlank()) return 0;
        if (fn == null || fn.isNull() || !fn.canExecute()) return 0;

        String key = name.trim();
        SubList list = handlers.computeIfAbsent(key, k -> new SubList());
        int id = nextSubId.getAndIncrement();
        list.add(new Sub(id, fn, once));
        return id;
    }

    public boolean off(String name, int subId) {
        if (subId <= 0) return false;
        if (name == null) return false;

        String key = name.trim();
        if (key.isEmpty()) return false;

        SubList list = handlers.get(key);
        if (list == null) return false;

        boolean removed = list.removeById(subId);
        if (removed && list.isEmpty()) {
            handlers.remove(key, list); // remove only if same instance still mapped
        }
        return removed;
    }



    /** Pump with defaults and return processed count. */
    public int pump() {
        return pump(DEFAULT_MAX_EVENTS_PER_FRAME, DEFAULT_TIME_BUDGET_NANOS);
    }

    public int pump(int maxEventsPerFrame, long timeBudgetNanos) {
        int limit = Math.max(0, maxEventsPerFrame);
        long deadline = (timeBudgetNanos > 0L) ? (System.nanoTime() + timeBudgetNanos) : Long.MAX_VALUE;

        int processed = 0;
        int checkMask = 0x3F;

        while (processed < limit) {
            Event e = queue.poll();
            if (e == null) break;

            processed++;
            dispatch(e);

            if ((processed & checkMask) == 0 && System.nanoTime() >= deadline) break;
        }
        return processed;
    }

    private void dispatch(Event e) {
        SubList list = handlers.get(e.name());
        if (list == null || list.isEmpty()) return;

        int n = list.size();
        for (int i = 0; i < n; i++) {
            Sub s = list.get(i);
            if (s == null) continue;

            try {
                if (e.payload() == null) s.fn.execute();
                else s.fn.execute(e.payload());
            } catch (Throwable t) {
                log.error("Event handler failed: {} (subId={})", e.name(), s.id, t);
            } finally {
                if (s.once) list.removeById(s.id);
            }
        }

        if (list.isEmpty()) handlers.remove(e.name(), list);
    }

    public int queuedEventsApprox() { return queue.size(); }

    public void clearAll() {
        handlers.clear();
        queue.clear();
    }
}
