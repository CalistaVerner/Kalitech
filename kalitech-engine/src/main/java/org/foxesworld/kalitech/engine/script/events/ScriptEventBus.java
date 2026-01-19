// FILE: org/foxesworld/kalitech/engine/script/events/ScriptEventBus.java
package org.foxesworld.kalitech.engine.script.events;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ScriptEventBus (REDengine-style, AAA-contract) — BACKWARD COMPATIBLE.
 * <p>
 * Keeps legacy API + behavior:
 * <ul>
 *   <li>emit(name, payload) enqueues raw event</li>
 *   <li>on(name, fn) / once(name, fn) => fn(payload?) exactly as before</li>
 *   <li>off(name, id), clear(name), clearAll(), pump(...)</li>
 * </ul>
 *
 * Adds AAA-contract API (envelope):
 * <ul>
 *   <li>emitEvent(topic, payload, meta) => dispatches envelope listeners (and also legacy listeners for the same topic)</li>
 *   <li>onEvent/onceEvent(topic, fn, phase, priority) => fn(EventEnvelope)</li>
 *   <li>onAny/onceAny(fn, phase, priority) => fn(EventEnvelope)</li>
 *   <li>onPattern/oncePattern(pattern, fn, phase, priority) => fn(EventEnvelope)</li>
 *   <li>off(token) => token-based unsubscribe (topic not required)</li>
 *   <li>history ring buffer: setHistoryMax(), getHistory()</li>
 * </ul>
 *
 * Threading model:
 * <ul>
 *   <li>emit/emitEvent are thread-safe and only enqueue</li>
 *   <li>pump() must be called from main thread once per frame (or more) to dispatch</li>
 * </ul>
 */
public final class ScriptEventBus {

    public static final int DEFAULT_MAX_EVENTS_PER_FRAME = 4096;
    public static final long DEFAULT_TIME_BUDGET_NANOS = 2_000_000L; // 2ms
    private static final Logger log = LogManager.getLogger(ScriptEventBus.class);

    // -------------------- AAA envelope --------------------
    private final Queue<QEvent> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger nextSubId = new AtomicInteger(1);
    private final AtomicLong nextSeq = new AtomicLong(1);
    private final AtomicLong emittedCount = new AtomicLong();
    private final AtomicLong pumpedCount = new AtomicLong();
    private final AtomicLong windowCount = new AtomicLong();
    private volatile long lastRateNanos = System.nanoTime();
    private volatile double eventsPerSec = 0.0;

    /**
     * token -> location of subscription (for off(token))
     */
    private final ConcurrentHashMap<Integer, SubRef> byToken = new ConcurrentHashMap<>();

    /**
     * exact-topic legacy handlers
     */
    private final Map<String, LegacySubList> legacyHandlers = new ConcurrentHashMap<>();

    /**
     * exact-topic AAA listeners: topic -> [phase0, phase1, phase2] lists
     */
    private final Map<String, OrderedSubList[]> eventTopic = new ConcurrentHashMap<>();

    // any AAA listeners by phase
    private final OrderedSubList[] any = new OrderedSubList[]{
            new OrderedSubList(), new OrderedSubList(), new OrderedSubList()
    };

    // pattern AAA listeners by phase
    private final OrderedSubList[] patterns = new OrderedSubList[]{
            new OrderedSubList(), new OrderedSubList(), new OrderedSubList()
    };

    // -------------------- legacy payload normalization --------------------

    /**
     * Topic-specific legacy adapters. They transform arbitrary engine objects into JS-friendly maps
     * (stable property access, predictable field names).
     */
    private final ConcurrentHashMap<String, LegacyPayloadAdapter> legacyAdapters = new ConcurrentHashMap<>();

    private final Object histLock = new Object();
    private final ArrayDeque<EventEnvelope> history = new ArrayDeque<>();
    private volatile TimeProvider time = TimeProvider.SYSTEM;
    private volatile int historyMax = 0;

    public ScriptEventBus() {
        installDefaultLegacyAdapters();
    }

    private static String normalizeTopic(String t) {
        String s = t.trim();
        if (s.isEmpty()) return "";
        while (s.startsWith(".")) s = s.substring(1);
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        while (s.contains("..")) s = s.replace("..", ".");
        return s;
    }

    /**
     * Register (or replace) a legacy payload adapter for exact topic.
     * Adapter must be deterministic and never throw.
     */
    public void setLegacyAdapter(String topic, LegacyPayloadAdapter adapter) {
        if (topic == null) return;
        String key = normalizeTopic(topic);
        if (key.isEmpty()) return;
        if (adapter == null) legacyAdapters.remove(key);
        else legacyAdapters.put(key, adapter);
    }

    private void installDefaultLegacyAdapters() {
        LegacyPayloadAdapter collision = new CollisionLegacyAdapter();
        legacyAdapters.put("engine.physics.collision.begin", collision);
        legacyAdapters.put("engine.physics.collision.end", collision);
        legacyAdapters.put("engine.physics.collision.persist", collision);
    }

    public void clearAll() {
        legacyHandlers.clear();
        eventTopic.clear();
        any[0].clear();
        any[1].clear();
        any[2].clear();
        patterns[0].clear();
        patterns[1].clear();
        patterns[2].clear();
        byToken.clear();
        queue.clear();
        legacyAdapters.clear();
        installDefaultLegacyAdapters();
        synchronized (histLock) {
            history.clear();
        }
    }

    // -------------------- legacy subscriptions (exact topic, raw payload) --------------------

    public void setTimeProvider(TimeProvider provider) {
        this.time = (provider == null) ? TimeProvider.SYSTEM : provider;
    }

    public void setHistoryMax(int max) {
        if (max < 0) max = 0;
        historyMax = max;
        synchronized (histLock) {
            if (max == 0) history.clear();
            else while (history.size() > max) history.removeFirst();
        }
    }

    public List<EventEnvelope> getHistory(int limit) {
        if (limit <= 0) return List.of();
        ArrayList<EventEnvelope> out = new ArrayList<>(Math.min(limit, 256));
        synchronized (histLock) {
            int n = 0;
            for (Iterator<EventEnvelope> it = history.descendingIterator(); it.hasNext() && n < limit; ) {
                out.add(it.next());
                n++;
            }
        }
        return out;
    }

    // -------------------- AAA subscriptions (envelope) --------------------

    private void record(EventEnvelope env) {
        int max = historyMax;
        if (max <= 0) return;
        synchronized (histLock) {
            history.addLast(env);
            while (history.size() > max) history.removeFirst();
        }
    }

    public void emit(String name, Object payload) {
        if (name == null) return;
        String key = normalizeTopic(name);
        if (key.isEmpty()) return;
        emittedCount.incrementAndGet();
        queue.add(new QEvent(key, payload, null, false));
    }

    public void emit(String name) {
        emit(name, null);
    }

    public void emitEvent(String topic, Object payload, Meta meta) {
        if (topic == null) return;
        String key = normalizeTopic(topic);
        if (key.isEmpty()) return;
        emittedCount.incrementAndGet();
        queue.add(new QEvent(key, payload, meta, true));
    }

    public int on(String name, Value fn) {
        return on(name, fn, false);
    }

    public int once(String name, Value fn) {
        return on(name, fn, true);
    }

    private int on(String name, Value fn, boolean once) {
        if (name == null) return 0;
        String key = normalizeTopic(name);
        if (key.isEmpty()) return 0;

        if (fn == null || fn.isNull() || !fn.canExecute()) return 0;

        LegacySubList list = legacyHandlers.computeIfAbsent(key, k -> new LegacySubList());
        int id = nextSubId.getAndIncrement();
        list.add(new LegacySub(id, fn, once));

        byToken.put(id, new SubRef(SubKind.LEGACY_TOPIC, key, -1, id));
        return id;
    }

    public boolean off(String name, int subId) {
        if (subId <= 0) return false;
        if (name == null) return false;

        String key = normalizeTopic(name);
        if (key.isEmpty()) return false;

        LegacySubList list = legacyHandlers.get(key);
        if (list == null) return false;

        boolean removed = list.removeById(subId);
        if (removed && list.isEmpty()) legacyHandlers.remove(key, list);

        if (removed) byToken.remove(subId);
        return removed;
    }

    /**
     * Remove all legacy+aaa subscriptions for topic (does not touch queue).
     */
    public void clear(String name) {
        if (name == null) return;
        String key = normalizeTopic(name);
        if (key.isEmpty()) return;

        legacyHandlers.remove(key);
        OrderedSubList[] arr = eventTopic.remove(key);
        if (arr != null) {
            for (int pi = 0; pi < 3; pi++) {
                OrderedSubList l = arr[pi];
                for (int i = 0; i < l.size(); i++) {
                    AaaSub s = l.get(i);
                    if (s != null) byToken.remove(s.id);
                }
            }
        }
    }

    private void dispatchLegacy(String topic, Object payload) {
        LegacySubList list = legacyHandlers.get(topic);
        if (list == null || list.isEmpty()) return;

        Object normalized = normalizeLegacyPayload(topic, payload);

        for (int i = 0; i < list.size(); ) {
            LegacySub s = list.get(i);
            if (s == null) {
                i++;
                continue;
            }

            try {
                if (normalized == null) s.fn.execute();
                else s.fn.execute(normalized);
            } catch (Throwable t) {
                String pCls = (normalized == null) ? "null" : normalized.getClass().getName();
                log.error("Event handler failed (legacy): {} (subId={}, payloadClass={})", topic, s.id, pCls, t);
            }

            if (s.once) {
                list.removeById(s.id);
                byToken.remove(s.id);
                continue;
            }

            i++;
        }

        if (list.isEmpty()) legacyHandlers.remove(topic, list);
    }

    public int onEvent(String topic, Value fn, Phase phase, int priority) {
        return addAaaTopic(topic, fn, false, phase, priority);
    }

    public int onceEvent(String topic, Value fn, Phase phase, int priority) {
        return addAaaTopic(topic, fn, true, phase, priority);
    }

    public int onAny(Value fn, Phase phase, int priority) {
        return addAaaSpecial(SubKind.ANY, null, new AnyMatcher(), fn, false, phase, priority);
    }

    public int onceAny(Value fn, Phase phase, int priority) {
        return addAaaSpecial(SubKind.ANY, null, new AnyMatcher(), fn, true, phase, priority);
    }

    public int onPattern(String pattern, Value fn, Phase phase, int priority) {
        if (pattern == null) return 0;
        String p = normalizeTopic(pattern);
        if (p.isEmpty()) return 0;
        return addAaaSpecial(SubKind.PATTERN, null, new PatternMatcher(p), fn, false, phase, priority);
    }

    public int oncePattern(String pattern, Value fn, Phase phase, int priority) {
        if (pattern == null) return 0;
        String p = normalizeTopic(pattern);
        if (p.isEmpty()) return 0;
        return addAaaSpecial(SubKind.PATTERN, null, new PatternMatcher(p), fn, true, phase, priority);
    }

    /**
     * Token-based unsubscribe (works for legacy and AAA).
     */
    public boolean off(int token) {
        if (token <= 0) return false;
        SubRef ref = byToken.remove(token);
        if (ref == null) return false;

        switch (ref.kind) {
            case LEGACY_TOPIC -> {
                LegacySubList list = legacyHandlers.get(ref.key);
                if (list == null) return false;
                boolean removed = list.removeById(ref.subId);
                if (removed && list.isEmpty()) legacyHandlers.remove(ref.key, list);
                return removed;
            }
            case EVENT_TOPIC -> {
                OrderedSubList[] arr = eventTopic.get(ref.key);
                if (arr == null) return false;
                OrderedSubList list = arr[ref.phaseIdx];
                boolean removed = list.removeById(ref.subId);
                if (removed && arr[0].isEmpty() && arr[1].isEmpty() && arr[2].isEmpty()) {
                    eventTopic.remove(ref.key, arr);
                }
                return removed;
            }
            case ANY -> {
                return any[ref.phaseIdx].removeById(ref.subId);
            }
            case PATTERN -> {
                return patterns[ref.phaseIdx].removeById(ref.subId);
            }
        }
        return false;
    }

    private int addAaaTopic(String topic, Value fn, boolean once, Phase phase, int priority) {
        if (topic == null) return 0;
        String key = normalizeTopic(topic);
        if (key.isEmpty()) return 0;
        if (fn == null || fn.isNull() || !fn.canExecute()) return 0;

        int id = nextSubId.getAndIncrement();
        Phase ph = (phase == null) ? Phase.MAIN : phase;
        int phaseIdx = ph.ordinal();

        OrderedSubList[] lists = eventTopic.computeIfAbsent(key, k ->
                new OrderedSubList[]{new OrderedSubList(), new OrderedSubList(), new OrderedSubList()});

        AaaSub s = new AaaSub(id, fn, once, priority, ph, new ExactMatcher(key));
        lists[phaseIdx].addOrdered(s);

        byToken.put(id, new SubRef(SubKind.EVENT_TOPIC, key, phaseIdx, id));
        return id;
    }

    private int addAaaSpecial(SubKind kind, String key, Matcher matcher, Value fn, boolean once, Phase phase, int priority) {
        if (fn == null || fn.isNull() || !fn.canExecute()) return 0;

        int id = nextSubId.getAndIncrement();
        Phase ph = (phase == null) ? Phase.MAIN : phase;
        int phaseIdx = ph.ordinal();

        AaaSub s = new AaaSub(id, fn, once, priority, ph, matcher);

        if (kind == SubKind.ANY) any[phaseIdx].addOrdered(s);
        else patterns[phaseIdx].addOrdered(s);

        byToken.put(id, new SubRef(kind, key, phaseIdx, id));
        return id;
    }

    /**
     * Pump with defaults and return processed count.
     */
    public int pump() {
        return pump(DEFAULT_MAX_EVENTS_PER_FRAME, DEFAULT_TIME_BUDGET_NANOS);
    }

    public int pump(int maxEventsPerFrame, long timeBudgetNanos) {
        int limit = Math.max(0, maxEventsPerFrame);

        long now = System.nanoTime();
        long deadline;
        if (timeBudgetNanos <= 0L) {
            deadline = Long.MAX_VALUE;
        } else {
            long sum = now + timeBudgetNanos;
            deadline = (sum < now) ? Long.MAX_VALUE : sum;
        }

        int processed = 0;
        int checkMask = 0x3F; // check time every 64 events

        while (processed < limit) {
            QEvent e = queue.poll();
            if (e == null) break;

            processed++;
            dispatch(e);

            if ((processed & checkMask) == 0 && System.nanoTime() >= deadline) break;
        }
        if (processed > 0) {
            pumpedCount.addAndGet(processed);
            windowCount.addAndGet(processed);
            updateRate(now);
        }
        return processed;
    }

    private void updateRate(long nowNanos) {
        long elapsed = nowNanos - lastRateNanos;
        if (elapsed < 1_000_000_000L) return;
        long count = windowCount.getAndSet(0);
        double secs = elapsed / 1_000_000_000.0;
        if (secs > 0) eventsPerSec = count / secs;
        lastRateNanos = nowNanos;
    }

    private void dispatch(QEvent qe) {
        dispatchLegacy(qe.topic, qe.payload);

        EventEnvelope env = buildEnvelope(qe.topic, qe.payload, qe.metaOrNull);
        record(env);

        dispatchAaa(env, Phase.PRE);
        dispatchAaa(env, Phase.MAIN);
        dispatchAaa(env, Phase.POST);
    }

    private Object normalizeLegacyPayload(String topic, Object payload) {
        if (payload == null) return null;

        LegacyPayloadAdapter adapter = legacyAdapters.get(topic);
        if (adapter == null) return payload;

        try {
            Object out = adapter.adapt(topic, payload);
            return (out != null) ? out : null;
        } catch (Throwable t) {
            log.warn("Legacy payload adapter failed: topic={} adapter={} payloadClass={}",
                    topic, adapter.getClass().getName(), payload.getClass().getName(), t);
            return payload;
        }
    }

    /**
     * Adapter contract: convert raw payload into a JS-friendly object.
     * Must never throw; return original payload if unsure.
     */
    @FunctionalInterface
    public interface LegacyPayloadAdapter {
        Object adapt(String topic, Object payload);
    }

    private void dispatchAaa(EventEnvelope env, Phase phase) {
        int pi = phase.ordinal();

        OrderedSubList[] tLists = eventTopic.get(env.topic);
        if (tLists != null) runAaaList(tLists[pi], env, SubKind.EVENT_TOPIC);

        runAaaList(any[pi], env, SubKind.ANY);
        runAaaList(patterns[pi], env, SubKind.PATTERN);
    }

    private void runAaaList(OrderedSubList list, EventEnvelope env, SubKind kind) {
        if (list == null || list.isEmpty()) return;

        for (int i = 0; i < list.size(); ) {
            AaaSub s = list.get(i);
            if (s == null) {
                i++;
                continue;
            }
            if (!s.matcher.matches(env.topic)) {
                i++;
                continue;
            }

            try {
                s.fn.execute(env);
            } catch (Throwable t) {
                log.error("Event handler failed (aaa): topic={} phase={} token={} matcher={}",
                        env.topic, s.phase, s.id, s.matcher.debug(), t);
            }

            if (s.once) {
                off(s.id);
                continue;
            }

            i++;
        }
    }

    private EventEnvelope buildEnvelope(String topic, Object payload, Meta metaIn) {
        Meta m = (metaIn != null) ? metaIn : new Meta();

        if (m.ts == 0L) m.ts = time.nowMs();
        if (m.frame == 0L) m.frame = time.frame();
        if (m.thread == null) m.thread = Thread.currentThread().getName();
        if (m.seq == 0L) m.seq = nextSeq.getAndIncrement();
        if (m.source == null) m.source = "engine";

        return new EventEnvelope(topic, payload, m);
    }

    public int queuedEventsApprox() {
        return queue.size();
    }

    public EventStats stats() {
        return new EventStats(eventsPerSec, emittedCount.get(), pumpedCount.get(), queue.size());
    }

    public enum Phase {PRE, MAIN, POST}

    private enum SubKind {LEGACY_TOPIC, EVENT_TOPIC, ANY, PATTERN}

    /**
     * Optional: provide frame/time from engine.
     */
    public interface TimeProvider {
        TimeProvider SYSTEM = new TimeProvider() {
            @Override
            public long nowMs() {
                return System.currentTimeMillis();
            }

            @Override
            public long frame() {
                return 0L;
            }
        };

        long nowMs();

        long frame();
    }

    private interface Matcher {
        boolean matches(String topic);

        String debug();
    }

    /**
     * Stable meta for telemetry/debug. Fill what you have; bus fills missing ts/thread/seq.
     */
    public static final class Meta {
        public long ts;
        public long frame;
        public String thread;
        public long seq;
        public String source;
        public String world;
        public String entityUuid;
    }

    public static final class EventStats {
        @HostAccess.Export public final double eventsPerSec;
        @HostAccess.Export public final long emitted;
        @HostAccess.Export public final long pumped;
        @HostAccess.Export public final int queued;

        public EventStats(double eventsPerSec, long emitted, long pumped, int queued) {
            this.eventsPerSec = eventsPerSec;
            this.emitted = emitted;
            this.pumped = pumped;
            this.queued = queued;
        }
    }

    /**
     * Stable envelope passed to AAA listeners.
     */
    public static final class EventEnvelope {
        public final String topic;
        public final Object payload;
        public final Meta meta;

        public EventEnvelope(String topic, Object payload, Meta meta) {
            this.topic = topic;
            this.payload = payload;
            this.meta = meta;
        }
    }

    private record QEvent(String topic, Object payload, Meta metaOrNull, boolean isEnvelope) {
    }

    private static final class SubRef {
        final SubKind kind;
        final String key;
        final int phaseIdx;
        final int subId;

        SubRef(SubKind kind, String key, int phaseIdx, int subId) {
            this.kind = kind;
            this.key = key;
            this.phaseIdx = phaseIdx;
            this.subId = subId;
        }
    }

    private static final class LegacySub {
        final int id;
        final Value fn;
        final boolean once;

        LegacySub(int id, Value fn, boolean once) {
            this.id = id;
            this.fn = fn;
            this.once = once;
        }
    }

    private static final class LegacySubList {
        private LegacySub[] arr = new LegacySub[8];
        private int size = 0;

        int add(LegacySub s) {
            if (s == null) return 0;
            if (size >= arr.length) arr = Arrays.copyOf(arr, arr.length << 1);
            arr[size++] = s;
            return s.id;
        }

        boolean isEmpty() {
            return size == 0;
        }

        int size() {
            return size;
        }

        LegacySub get(int i) {
            return arr[i];
        }

        boolean removeById(int id) {
            for (int i = 0; i < size; i++) {
                LegacySub s = arr[i];
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

        void clear() {
            Arrays.fill(arr, 0, size, null);
            size = 0;
        }
    }

    private static final class ExactMatcher implements Matcher {
        private final String topic;

        ExactMatcher(String topic) {
            this.topic = topic;
        }

        @Override
        public boolean matches(String t) {
            return topic.equals(t);
        }

        @Override
        public String debug() {
            return "EXACT(" + topic + ")";
        }
    }

    private static final class AnyMatcher implements Matcher {
        @Override
        public boolean matches(String topic) {
            return true;
        }

        @Override
        public String debug() {
            return "ANY";
        }
    }

    private static final class PatternMatcher implements Matcher {
        private final String pattern;
        private final String[] p;

        PatternMatcher(String pattern) {
            this.pattern = pattern;
            this.p = pattern.split("\\.");
        }

        @Override
        public boolean matches(String topic) {
            if ("**".equals(pattern)) return true;
            if (pattern.equals(topic)) return true;

            String[] t = topic.split("\\.");
            int i = 0;
            for (; i < p.length; i++) {
                String seg = p[i];
                if ("**".equals(seg)) return true;
                if (i >= t.length) return false;
                if ("*".equals(seg)) continue;
                if (!seg.equals(t[i])) return false;
            }
            return i == t.length;
        }

        @Override
        public String debug() {
            return "PATTERN(" + pattern + ")";
        }
    }

    private static final class AaaSub {
        final int id;
        final Value fn;
        final boolean once;
        final int priority;
        final Phase phase;
        final Matcher matcher;

        AaaSub(int id, Value fn, boolean once, int priority, Phase phase, Matcher matcher) {
            this.id = id;
            this.fn = fn;
            this.once = once;
            this.priority = priority;
            this.phase = phase;
            this.matcher = matcher;
        }
    }

    private static final class OrderedSubList {
        private AaaSub[] arr = new AaaSub[8];
        private int size = 0;

        int size() {
            return size;
        }

        boolean isEmpty() {
            return size == 0;
        }

        AaaSub get(int i) {
            return arr[i];
        }

        int addOrdered(AaaSub s) {
            if (s == null) return 0;
            if (size >= arr.length) arr = Arrays.copyOf(arr, arr.length << 1);

            int i = size;
            while (i > 0) {
                AaaSub prev = arr[i - 1];
                if (prev == null || prev.priority >= s.priority) break;
                arr[i] = prev;
                i--;
            }
            arr[i] = s;
            size++;
            return s.id;
        }

        boolean removeById(int id) {
            for (int i = 0; i < size; i++) {
                AaaSub s = arr[i];
                if (s != null && s.id == id) {
                    int last = size - 1;
                    if (i < last) System.arraycopy(arr, i + 1, arr, i, last - i);
                    arr[last] = null;
                    size = last;
                    return true;
                }
            }
            return false;
        }

        void clear() {
            Arrays.fill(arr, 0, size, null);
            size = 0;
        }
    }

    // -------------------- built-in legacy adapters --------------------

    /**
     * Normalizes collision payload into a stable map:
     * { a: <obj>, b: <obj>, step: int, dt: double, contact: <obj> }
     */
    private static final class CollisionLegacyAdapter implements LegacyPayloadAdapter {

        private static Object firstPresent(Map<?, ?> m, String... keys) {
            for (String k : keys) {
                if (m.containsKey(k)) return m.get(k);
            }
            return null;
        }

        private static Object reflectFirst(Object obj, String... names) {
            for (String n : names) {
                Object v = getFieldValue(obj, n);
                if (v != null) return v;
                v = callGetter(obj, n);
                if (v != null) return v;
            }
            return null;
        }

        private static Object getFieldValue(Object obj, String fieldName) {
            try {
                Field f = findField(obj.getClass(), fieldName);
                if (f == null) return null;
                f.setAccessible(true);
                return f.get(obj);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Field findField(Class<?> c, String name) {
            Class<?> cur = c;
            while (cur != null && cur != Object.class) {
                try {
                    return cur.getDeclaredField(name);
                } catch (NoSuchFieldException e) {
                    cur = cur.getSuperclass();
                }
            }
            return null;
        }

        private static Object callGetter(Object obj, String nameOrMethod) {
            try {
                String methodName = nameOrMethod;
                if (!nameOrMethod.startsWith("get") && !nameOrMethod.startsWith("is")) {
                    methodName = "get" + Character.toUpperCase(nameOrMethod.charAt(0)) + nameOrMethod.substring(1);
                }
                Method m = findNoArgMethod(obj.getClass(), methodName);
                if (m == null) return null;
                m.setAccessible(true);
                return m.invoke(obj);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Method findNoArgMethod(Class<?> c, String name) {
            Class<?> cur = c;
            while (cur != null && cur != Object.class) {
                for (Method m : cur.getDeclaredMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
                }
                cur = cur.getSuperclass();
            }
            return null;
        }

        @Override
        public Object adapt(String topic, Object payload) {
            if (payload == null) return null;

            if (payload instanceof Map<?, ?> m) {
                Object a = firstPresent(m, "a", "objA", "A", "first", "0");
                Object b = firstPresent(m, "b", "objB", "B", "second", "1");
                Object step = firstPresent(m, "step", "frame", "tick");
                Object dt = firstPresent(m, "dt", "delta", "timeStep");
                Object contact = firstPresent(m, "contact", "manifold", "point", "info");

                HashMap<String, Object> out = new HashMap<>(8);
                out.put("a", a);
                out.put("b", b);
                if (step != null) out.put("step", step);
                if (dt != null) out.put("dt", dt);
                if (contact != null) out.put("contact", contact);
                return out;
            }

            if (payload instanceof Object[] arr) {
                HashMap<String, Object> out = new HashMap<>(8);
                if (arr.length > 0) out.put("a", arr[0]);
                if (arr.length > 1) out.put("b", arr[1]);
                if (arr.length > 2) out.put("contact", arr[2]);
                return out;
            }

            Object a = reflectFirst(payload, "a", "objA", "A", "first", "getA", "getObjA", "getObjectA", "getNodeA", "getFirst");
            Object b = reflectFirst(payload, "b", "objB", "B", "second", "getB", "getObjB", "getObjectB", "getNodeB", "getSecond");
            Object step = reflectFirst(payload, "step", "frame", "tick", "getStep", "getFrame", "getTick");
            Object dt = reflectFirst(payload, "dt", "delta", "timeStep", "getDt", "getDelta", "getTimeStep");
            Object contact = reflectFirst(payload, "contact", "manifold", "point", "info", "getContact", "getManifold", "getPoint", "getInfo");

            if (a == null && b == null && step == null && dt == null && contact == null) {
                return payload;
            }

            HashMap<String, Object> out = new HashMap<>(8);
            out.put("a", a);
            out.put("b", b);
            if (step != null) out.put("step", step);
            if (dt != null) out.put("dt", dt);
            if (contact != null) out.put("contact", contact);
            return out;
        }
    }
}
