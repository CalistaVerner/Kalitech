// FILE: org/foxesworld/kalitech/engine/script/events/ScriptEventBus.java
package org.foxesworld.kalitech.engine.script.events;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.script.ScriptFailureBoundary;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe event bus for Lua scripts.
 *
 * <p>Direct subscriptions receive the payload. Envelope subscriptions receive an
 * {@link EventEnvelope} with topic, payload, metadata, phase and priority ordering.
 * Producers only enqueue; {@link #pump()} performs deterministic dispatch on the main thread.</p>
 */
public final class ScriptEventBus {

    public static final int DEFAULT_MAX_EVENTS_PER_FRAME = 4096;
    public static final long DEFAULT_TIME_BUDGET_NANOS = 2_000_000L; // 2ms
    private static final Logger log = LogManager.getLogger(ScriptEventBus.class);

    // -------------------- envelope envelope --------------------
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
     * exact-topic direct handlers
     */
    private final Map<String, TopicSubList> topicHandlers = new ConcurrentHashMap<>();

    /**
     * exact-topic envelope listeners: topic -> [phase0, phase1, phase2] lists
     */
    private final Map<String, OrderedSubList[]> eventTopic = new ConcurrentHashMap<>();

    // any envelope listeners by phase
    private final OrderedSubList[] any = new OrderedSubList[]{
            new OrderedSubList(), new OrderedSubList(), new OrderedSubList()
    };

    // pattern envelope listeners by phase
    private final OrderedSubList[] patterns = new OrderedSubList[]{
            new OrderedSubList(), new OrderedSubList(), new OrderedSubList()
    };

    // -------------------- direct payload normalization --------------------

    /**
     * Topic-specific adapters transform engine objects into stable Lua-facing maps
     * (stable property access, predictable field names).
     */
    private final ConcurrentHashMap<String, PayloadAdapter> payloadAdapters = new ConcurrentHashMap<>();

    private final Object histLock = new Object();
    private final ArrayDeque<EventEnvelope> history = new ArrayDeque<>();
    private volatile TimeProvider time = TimeProvider.SYSTEM;
    private volatile int historyMax = 0;

    public ScriptEventBus() {
        installDefaultPayloadAdapters();
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
     * Register or replace a payload adapter for an exact topic.
     * Adapter must be deterministic and never throw.
     */
    public void setPayloadAdapter(String topic, PayloadAdapter adapter) {
        if (topic == null) return;
        String key = normalizeTopic(topic);
        if (key.isEmpty()) return;
        if (adapter == null) payloadAdapters.remove(key);
        else payloadAdapters.put(key, adapter);
    }

    private void installDefaultPayloadAdapters() {
        PayloadAdapter collision = new CollisionPayloadAdapter();
        payloadAdapters.put("engine.physics.collision.begin", collision);
        payloadAdapters.put("engine.physics.collision.end", collision);
        payloadAdapters.put("engine.physics.collision.persist", collision);
    }

    public void clearAll() {
        topicHandlers.clear();
        eventTopic.clear();
        any[0].clear();
        any[1].clear();
        any[2].clear();
        patterns[0].clear();
        patterns[1].clear();
        patterns[2].clear();
        byToken.clear();
        queue.clear();
        payloadAdapters.clear();
        installDefaultPayloadAdapters();
        synchronized (histLock) {
            history.clear();
        }
    }

    // -------------------- direct subscriptions (exact topic, raw payload) --------------------

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

    // -------------------- envelope subscriptions (envelope) --------------------

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
        queue.add(new QEvent(key, payload, null));
    }

    public void emit(String name) {
        emit(name, null);
    }

    public void emitEvent(String topic, Object payload, Meta meta) {
        if (topic == null) return;
        String key = normalizeTopic(topic);
        if (key.isEmpty()) return;
        emittedCount.incrementAndGet();
        queue.add(new QEvent(key, payload, meta));
    }

    public int on(String name, LuaValueRef fn) {
        return on(name, fn, false);
    }

    public int once(String name, LuaValueRef fn) {
        return on(name, fn, true);
    }

    private int on(String name, LuaValueRef fn, boolean once) {
        if (name == null) return 0;
        String key = normalizeTopic(name);
        if (key.isEmpty()) return 0;

        if (fn == null || fn.isNull() || !fn.canExecute()) return 0;

        TopicSubList list = topicHandlers.computeIfAbsent(key, k -> new TopicSubList());
        int id = nextSubId.getAndIncrement();
        list.add(new TopicSub(id, fn, once));

        byToken.put(id, new SubRef(SubKind.TOPIC, key, -1, id));
        return id;
    }

    public boolean off(String name, int subId) {
        if (subId <= 0) return false;
        if (name == null) return false;

        String key = normalizeTopic(name);
        if (key.isEmpty()) return false;

        TopicSubList list = topicHandlers.get(key);
        if (list == null) return false;

        boolean removed = list.removeById(subId);
        if (removed && list.isEmpty()) topicHandlers.remove(key, list);

        if (removed) byToken.remove(subId);
        return removed;
    }

    /**
     * Remove all direct and envelope subscriptions for a topic without touching the queue.
     */
    public void clear(String name) {
        if (name == null) return;
        String key = normalizeTopic(name);
        if (key.isEmpty()) return;

        topicHandlers.remove(key);
        OrderedSubList[] arr = eventTopic.remove(key);
        if (arr != null) {
            for (int pi = 0; pi < 3; pi++) {
                OrderedSubList l = arr[pi];
                for (int i = 0; i < l.size(); i++) {
                    EnvelopeSub s = l.get(i);
                    if (s != null) byToken.remove(s.id);
                }
            }
        }
    }

    private void dispatchTopic(String topic, Object payload) {
        TopicSubList list = topicHandlers.get(topic);
        if (list == null || list.isEmpty()) return;

        Object normalized = normalizePayload(topic, payload);

        for (int i = 0; i < list.size(); ) {
            TopicSub s = list.get(i);
            if (s == null) {
                i++;
                continue;
            }

            try {
                if (normalized == null) s.fn.execute();
                else s.fn.execute(normalized);
            } catch (Throwable failure) {
                ScriptFailureBoundary.rethrowIfFatal(failure);
                String payloadClass = (normalized == null) ? "null" : normalized.getClass().getName();
                log.error("Lua event handler quarantined topic={} token={} payloadClass={}; "
                                + "event bus and engine remain active",
                        topic, s.id, payloadClass, failure);
                list.removeById(s.id);
                byToken.remove(s.id);
                continue;
            }

            if (s.once) {
                list.removeById(s.id);
                byToken.remove(s.id);
                continue;
            }

            i++;
        }

        if (list.isEmpty()) topicHandlers.remove(topic, list);
    }

    public int onEvent(String topic, LuaValueRef fn, Phase phase, int priority) {
        return addEnvelopeTopic(topic, fn, false, phase, priority);
    }

    public int onceEvent(String topic, LuaValueRef fn, Phase phase, int priority) {
        return addEnvelopeTopic(topic, fn, true, phase, priority);
    }

    public int onAny(LuaValueRef fn, Phase phase, int priority) {
        return addEnvelopeSpecial(SubKind.ANY, null, new AnyMatcher(), fn, false, phase, priority);
    }

    public int onceAny(LuaValueRef fn, Phase phase, int priority) {
        return addEnvelopeSpecial(SubKind.ANY, null, new AnyMatcher(), fn, true, phase, priority);
    }

    public int onPattern(String pattern, LuaValueRef fn, Phase phase, int priority) {
        if (pattern == null) return 0;
        String p = normalizeTopic(pattern);
        if (p.isEmpty()) return 0;
        return addEnvelopeSpecial(SubKind.PATTERN, null, new PatternMatcher(p), fn, false, phase, priority);
    }

    public int oncePattern(String pattern, LuaValueRef fn, Phase phase, int priority) {
        if (pattern == null) return 0;
        String p = normalizeTopic(pattern);
        if (p.isEmpty()) return 0;
        return addEnvelopeSpecial(SubKind.PATTERN, null, new PatternMatcher(p), fn, true, phase, priority);
    }

    /**
     * Token-based unsubscribe (works for direct and envelope subscriptions).
     */
    public boolean off(int token) {
        if (token <= 0) return false;
        SubRef ref = byToken.remove(token);
        if (ref == null) return false;

        switch (ref.kind) {
            case TOPIC -> {
                TopicSubList list = topicHandlers.get(ref.key);
                if (list == null) return false;
                boolean removed = list.removeById(ref.subId);
                if (removed && list.isEmpty()) topicHandlers.remove(ref.key, list);
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

    private int addEnvelopeTopic(String topic, LuaValueRef fn, boolean once, Phase phase, int priority) {
        if (topic == null) return 0;
        String key = normalizeTopic(topic);
        if (key.isEmpty()) return 0;
        if (fn == null || fn.isNull() || !fn.canExecute()) return 0;

        int id = nextSubId.getAndIncrement();
        Phase ph = (phase == null) ? Phase.MAIN : phase;
        int phaseIdx = ph.ordinal();

        OrderedSubList[] lists = eventTopic.computeIfAbsent(key, k ->
                new OrderedSubList[]{new OrderedSubList(), new OrderedSubList(), new OrderedSubList()});

        EnvelopeSub s = new EnvelopeSub(id, fn, once, priority, ph, new ExactMatcher(key));
        lists[phaseIdx].addOrdered(s);

        byToken.put(id, new SubRef(SubKind.EVENT_TOPIC, key, phaseIdx, id));
        return id;
    }

    private int addEnvelopeSpecial(SubKind kind, String key, Matcher matcher, LuaValueRef fn, boolean once, Phase phase, int priority) {
        if (fn == null || fn.isNull() || !fn.canExecute()) return 0;

        int id = nextSubId.getAndIncrement();
        Phase ph = (phase == null) ? Phase.MAIN : phase;
        int phaseIdx = ph.ordinal();

        EnvelopeSub s = new EnvelopeSub(id, fn, once, priority, ph, matcher);

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
        dispatchTopic(qe.topic, qe.payload);

        EventEnvelope env = buildEnvelope(qe.topic, qe.payload, qe.metaOrNull);
        record(env);

        dispatchEnvelope(env, Phase.PRE);
        dispatchEnvelope(env, Phase.MAIN);
        dispatchEnvelope(env, Phase.POST);
    }

    private Object normalizePayload(String topic, Object payload) {
        if (payload == null) return null;

        PayloadAdapter adapter = payloadAdapters.get(topic);
        if (adapter == null) return payload;

        try {
            Object out = adapter.adapt(topic, payload);
            return (out != null) ? out : null;
        } catch (Throwable t) {
            ScriptFailureBoundary.rethrowIfFatal(t);
            log.warn("direct payload adapter failed: topic={} adapter={} payloadClass={}",
                    topic, adapter.getClass().getName(), payload.getClass().getName(), t);
            return payload;
        }
    }

    /**
     * Adapter contract: convert a raw payload into a Lua-facing object.
     * Must never throw; return original payload if unsure.
     */
    @FunctionalInterface
    public interface PayloadAdapter {
        Object adapt(String topic, Object payload);
    }

    private void dispatchEnvelope(EventEnvelope env, Phase phase) {
        int pi = phase.ordinal();

        OrderedSubList[] tLists = eventTopic.get(env.topic);
        if (tLists != null) runEnvelopeList(tLists[pi], env, SubKind.EVENT_TOPIC);

        runEnvelopeList(any[pi], env, SubKind.ANY);
        runEnvelopeList(patterns[pi], env, SubKind.PATTERN);
    }

    private void runEnvelopeList(OrderedSubList list, EventEnvelope env, SubKind kind) {
        if (list == null || list.isEmpty()) return;

        for (int i = 0; i < list.size(); ) {
            EnvelopeSub s = list.get(i);
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
            } catch (Throwable failure) {
                ScriptFailureBoundary.rethrowIfFatal(failure);
                log.error("Lua event handler quarantined topic={} phase={} token={} matcher={}; "
                                + "event bus and engine remain active",
                        env.topic, s.phase, s.id, s.matcher.debug(), failure);
                off(s.id);
                continue;
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

        if (m.timestampMs == 0L) m.timestampMs = time.nowMs();
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

    private enum SubKind {TOPIC, EVENT_TOPIC, ANY, PATTERN}

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
     * Stable metadata for telemetry and debugging; the bus fills missing timestamp, thread and sequence values.
     */
    public static final class Meta {
        public long timestampMs;
        public long frame;
        public String thread;
        public long seq;
        public String source;
        public String world;
        public String entityUuid;
    }

    public static final class EventStats {
        @LuaExport public final double eventsPerSec;
        @LuaExport public final long emitted;
        @LuaExport public final long pumped;
        @LuaExport public final int queued;

        public EventStats(double eventsPerSec, long emitted, long pumped, int queued) {
            this.eventsPerSec = eventsPerSec;
            this.emitted = emitted;
            this.pumped = pumped;
            this.queued = queued;
        }
    }

    /**
     * Stable envelope passed to envelope listeners.
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

    private record QEvent(String topic, Object payload, Meta metaOrNull) {
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

    private static final class TopicSub {
        final int id;
        final LuaValueRef fn;
        final boolean once;

        TopicSub(int id, LuaValueRef fn, boolean once) {
            this.id = id;
            this.fn = fn;
            this.once = once;
        }
    }

    private static final class TopicSubList {
        private TopicSub[] arr = new TopicSub[8];
        private int size = 0;

        int add(TopicSub s) {
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

        TopicSub get(int i) {
            return arr[i];
        }

        boolean removeById(int id) {
            for (int i = 0; i < size; i++) {
                TopicSub s = arr[i];
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

    private static final class EnvelopeSub {
        final int id;
        final LuaValueRef fn;
        final boolean once;
        final int priority;
        final Phase phase;
        final Matcher matcher;

        EnvelopeSub(int id, LuaValueRef fn, boolean once, int priority, Phase phase, Matcher matcher) {
            this.id = id;
            this.fn = fn;
            this.once = once;
            this.priority = priority;
            this.phase = phase;
            this.matcher = matcher;
        }
    }

    private static final class OrderedSubList {
        private EnvelopeSub[] arr = new EnvelopeSub[8];
        private int size = 0;

        int size() {
            return size;
        }

        boolean isEmpty() {
            return size == 0;
        }

        EnvelopeSub get(int i) {
            return arr[i];
        }

        int addOrdered(EnvelopeSub s) {
            if (s == null) return 0;
            if (size >= arr.length) arr = Arrays.copyOf(arr, arr.length << 1);

            int i = size;
            while (i > 0) {
                EnvelopeSub prev = arr[i - 1];
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
                EnvelopeSub s = arr[i];
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

    // -------------------- built-in direct adapters --------------------

    /**
     * Normalizes collision payload into a stable map:
     * { a: <obj>, b: <obj>, step: int, dt: double, contact: <obj> }
     */
    private static final class CollisionPayloadAdapter implements PayloadAdapter {

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
            ScriptFailureBoundary.rethrowIfFatal(ignored);
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
            ScriptFailureBoundary.rethrowIfFatal(ignored);
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
