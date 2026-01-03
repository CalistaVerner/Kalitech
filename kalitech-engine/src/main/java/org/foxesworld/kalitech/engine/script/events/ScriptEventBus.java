// FILE: ScriptEventBus.java
package org.foxesworld.kalitech.engine.script.events;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

import java.util.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ScriptEventBus (REDengine-style, AAA-contract) — BACKWARD COMPATIBLE.
 *
 * ✅ Keeps legacy API + behavior:
 *   - emit(name, payload) enqueues raw event
 *   - on(name, fn) / once(name, fn) => fn(payload?) exactly as before
 *   - off(name, id), clear(name), clearAll(), pump(...)
 *
 * ✅ Adds AAA-contract API (envelope):
 *   - emitEvent(topic, payload, meta) => dispatches envelope listeners (and also legacy listeners for the same topic)
 *   - onEvent/onceEvent(topic, fn, phase, priority) => fn(EventEnvelope)
 *   - onAny/onceAny(fn, phase, priority) => fn(EventEnvelope)
 *   - onPattern/oncePattern(pattern, fn, phase, priority) => fn(EventEnvelope)
 *   - off(token) => token-based unsubscribe (topic not required)
 *   - history ring buffer: setHistoryMax(), getHistory()
 *
 * Threading model:
 *   - emit/emitEvent are thread-safe and only enqueue
 *   - pump() must be called from main thread once per frame (or more) to dispatch
 */
public final class ScriptEventBus {

    private static final Logger log = LogManager.getLogger(ScriptEventBus.class);

    public static final int DEFAULT_MAX_EVENTS_PER_FRAME = 4096;
    public static final long DEFAULT_TIME_BUDGET_NANOS = 2_000_000L; // 2ms

    // -------------------- AAA envelope --------------------

    public enum Phase { PRE, MAIN, POST }

    /** Stable meta for telemetry/debug. Fill what you have; bus fills missing ts/thread/seq. */
    public static final class Meta {
        public long ts;          // ms since epoch or custom clock
        public long frame;       // frame index (optional)
        public String thread;    // filled automatically if null
        public long seq;         // filled automatically if 0
        public String source;    // e.g. "physics", "world", "scripts"
        public String world;     // optional world id/name
        public int entityId;     // optional entity
    }

    /** Stable envelope passed to AAA listeners. */
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

    /** Optional: provide frame/time from engine. */
    public interface TimeProvider {
        long nowMs();
        long frame();
        TimeProvider SYSTEM = new TimeProvider() {
            @Override public long nowMs() { return System.currentTimeMillis(); }
            @Override public long frame() { return 0L; }
        };
    }

    private volatile TimeProvider time = TimeProvider.SYSTEM;
    public void setTimeProvider(TimeProvider provider) {
        this.time = (provider == null) ? TimeProvider.SYSTEM : provider;
    }

    // -------------------- internal event queue --------------------

    private record QEvent(String topic, Object payload, Meta metaOrNull, boolean isEnvelope) {}

    private final Queue<QEvent> queue = new ConcurrentLinkedQueue<>();

    // -------------------- ids/tokens --------------------

    private final AtomicInteger nextSubId = new AtomicInteger(1);
    private final AtomicLong nextSeq = new AtomicLong(1);

    private enum SubKind { LEGACY_TOPIC, EVENT_TOPIC, ANY, PATTERN }

    private static final class SubRef {
        final SubKind kind;
        final String key; // topic for topic kinds; null for any/pattern
        final int phaseIdx; // 0..2 for AAA lists; -1 for legacy
        final int subId;
        SubRef(SubKind kind, String key, int phaseIdx, int subId) {
            this.kind = kind;
            this.key = key;
            this.phaseIdx = phaseIdx;
            this.subId = subId;
        }
    }

    /** token -> location of subscription (for off(token)) */
    private final ConcurrentHashMap<Integer, SubRef> byToken = new ConcurrentHashMap<>();

    // -------------------- legacy subscriptions (exact topic, raw payload) --------------------

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

    /**
     * Small, allocation-light subscription list.
     * Not thread-safe by itself; ScriptEventBus controls access patterns (single-thread dispatch).
     * NOTE: legacy list removal uses swap-last (as before).
     */
    private static final class LegacySubList {
        private LegacySub[] arr = new LegacySub[8];
        private int size = 0;

        int add(LegacySub s) {
            if (s == null) return 0;
            if (size >= arr.length) arr = Arrays.copyOf(arr, arr.length << 1);
            arr[size++] = s;
            return s.id;
        }

        boolean isEmpty() { return size == 0; }
        int size() { return size; }
        LegacySub get(int i) { return arr[i]; }

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

    /** exact-topic legacy handlers */
    private final Map<String, LegacySubList> legacyHandlers = new ConcurrentHashMap<>();

    // -------------------- AAA subscriptions (envelope) --------------------

    private interface Matcher {
        boolean matches(String topic);
        String debug();
    }

    private static final class ExactMatcher implements Matcher {
        private final String topic;
        ExactMatcher(String topic) { this.topic = topic; }
        @Override public boolean matches(String t) { return topic.equals(t); }
        @Override public String debug() { return "EXACT(" + topic + ")"; }
    }

    private static final class AnyMatcher implements Matcher {
        @Override public boolean matches(String topic) { return true; }
        @Override public String debug() { return "ANY"; }
    }

    private static final class PatternMatcher implements Matcher {
        private final String pattern;
        private final String[] p;
        PatternMatcher(String pattern) {
            this.pattern = pattern;
            this.p = pattern.split("\\.");
        }
        @Override public boolean matches(String topic) {
            if ("**".equals(pattern)) return true;
            if (pattern.equals(topic)) return true;

            String[] t = topic.split("\\.");
            int i = 0;
            for (; i < p.length; i++) {
                String seg = p[i];
                if ("**".equals(seg)) return true;         // match the rest
                if (i >= t.length) return false;
                if ("*".equals(seg)) continue;             // one segment wildcard
                if (!seg.equals(t[i])) return false;
            }
            return i == t.length;
        }
        @Override public String debug() { return "PATTERN(" + pattern + ")"; }
    }

    private static final class AaaSub {
        final int id;
        final Value fn;          // called with (EventEnvelope)
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

    /**
     * Ordered list by priority DESC.
     * Removal keeps order (shift), because order matters for AAA.
     */
    private static final class OrderedSubList {
        private AaaSub[] arr = new AaaSub[8];
        private int size = 0;

        int size() { return size; }
        boolean isEmpty() { return size == 0; }
        AaaSub get(int i) { return arr[i]; }

        int addOrdered(AaaSub s) {
            if (s == null) return 0;
            if (size >= arr.length) arr = Arrays.copyOf(arr, arr.length << 1);

            int i = size;
            // insert so that higher priority comes first
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
                    // shift left from i
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

    // exact-topic AAA listeners: topic -> [phase0, phase1, phase2] lists
    private final Map<String, OrderedSubList[]> eventTopic = new ConcurrentHashMap<>();

    // any AAA listeners by phase
    private final OrderedSubList[] any = new OrderedSubList[] { new OrderedSubList(), new OrderedSubList(), new OrderedSubList() };

    // pattern AAA listeners by phase
    private final OrderedSubList[] patterns = new OrderedSubList[] { new OrderedSubList(), new OrderedSubList(), new OrderedSubList() };

    // -------------------- history ring buffer --------------------

    private final Object histLock = new Object();
    private final ArrayDeque<EventEnvelope> history = new ArrayDeque<>();
    private volatile int historyMax = 0;

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

    private void record(EventEnvelope env) {
        int max = historyMax;
        if (max <= 0) return;
        synchronized (histLock) {
            history.addLast(env);
            while (history.size() > max) history.removeFirst();
        }
    }

    // -------------------- legacy emit API --------------------

    public void emit(String name, Object payload) {
        if (name == null) return;
        String key = normalizeTopic(name);
        if (key.isEmpty()) return;
        queue.add(new QEvent(key, payload, null, false));
    }

    public void emit(String name) {
        emit(name, null);
    }

    // -------------------- AAA emit API --------------------

    public void emitEvent(String topic, Object payload, Meta meta) {
        if (topic == null) return;
        String key = normalizeTopic(topic);
        if (key.isEmpty()) return;
        queue.add(new QEvent(key, payload, meta, true));
    }

    // -------------------- legacy subscribe API --------------------

    public int on(String name, Value fn) { return on(name, fn, false); }
    public int once(String name, Value fn) { return on(name, fn, true); }

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

    /** Remove all legacy+aaa subscriptions for topic (does not touch queue). */
    public void clear(String name) {
        if (name == null) return;
        String key = normalizeTopic(name);
        if (key.isEmpty()) return;

        legacyHandlers.remove(key);
        OrderedSubList[] arr = eventTopic.remove(key);
        if (arr != null) {
            // also remove from token map
            for (int pi = 0; pi < 3; pi++) {
                OrderedSubList l = arr[pi];
                for (int i = 0; i < l.size(); i++) {
                    AaaSub s = l.get(i);
                    if (s != null) byToken.remove(s.id);
                }
            }
        }
    }

    public void clearAll() {
        legacyHandlers.clear();
        eventTopic.clear();
        any[0].clear(); any[1].clear(); any[2].clear();
        patterns[0].clear(); patterns[1].clear(); patterns[2].clear();
        byToken.clear();
        queue.clear();
        synchronized (histLock) { history.clear(); }
    }

    // -------------------- AAA subscribe API --------------------

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

    /** Token-based unsubscribe (works for legacy and AAA). */
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
                new OrderedSubList[] { new OrderedSubList(), new OrderedSubList(), new OrderedSubList() });

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

    // -------------------- pump/dispatch (budget-aware) --------------------

    /** Pump with defaults and return processed count. */
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
        return processed;
    }

    private void dispatch(QEvent qe) {
        // 1) legacy exact-topic listeners (raw payload or no args) — ALWAYS for both emit() and emitEvent()
        dispatchLegacy(qe.topic, qe.payload);

        // 2) AAA envelope listeners:
        //    - if emitEvent() -> always
        //    - if legacy emit() -> still create envelope for any/pattern/event-topic listeners (telemetry-friendly)
        EventEnvelope env = buildEnvelope(qe.topic, qe.payload, qe.metaOrNull);
        record(env);

        dispatchAaa(env, Phase.PRE);
        dispatchAaa(env, Phase.MAIN);
        dispatchAaa(env, Phase.POST);
    }

    private void dispatchLegacy(String topic, Object payload) {
        LegacySubList list = legacyHandlers.get(topic);
        if (list == null || list.isEmpty()) return;

        for (int i = 0; i < list.size(); ) {
            LegacySub s = list.get(i);
            if (s == null) { i++; continue; }

            try {
                if (payload == null) s.fn.execute();
                else s.fn.execute(payload);
            } catch (Throwable t) {
                log.error("Event handler failed (legacy): {} (subId={})", topic, s.id, t);
            }

            if (s.once) {
                list.removeById(s.id);
                byToken.remove(s.id);
                continue; // swap-last semantics -> keep i
            }

            i++;
        }

        if (list.isEmpty()) legacyHandlers.remove(topic, list);
    }

    private void dispatchAaa(EventEnvelope env, Phase phase) {
        int pi = phase.ordinal();

        // exact topic AAA
        OrderedSubList[] tLists = eventTopic.get(env.topic);
        if (tLists != null) runAaaList(tLists[pi], env, SubKind.EVENT_TOPIC);

        // any
        runAaaList(any[pi], env, SubKind.ANY);

        // patterns
        runAaaList(patterns[pi], env, SubKind.PATTERN);
    }

    private void runAaaList(OrderedSubList list, EventEnvelope env, SubKind kind) {
        if (list == null || list.isEmpty()) return;

        // NOTE: OrderedSubList removal shifts. We'll iterate with index and adjust.
        for (int i = 0; i < list.size(); ) {
            AaaSub s = list.get(i);
            if (s == null) { i++; continue; }
            if (!s.matcher.matches(env.topic)) { i++; continue; }

            try {
                s.fn.execute(env);
            } catch (Throwable t) {
                log.error("Event handler failed (aaa): topic={} phase={} token={} matcher={}",
                        env.topic, s.phase, s.id, s.matcher.debug(), t);
            }

            if (s.once) {
                // token-based off ensures correct removal no matter where it lives
                off(s.id);
                // list has shifted; stay at same i
                continue;
            }

            i++;
        }
    }

    private EventEnvelope buildEnvelope(String topic, Object payload, Meta metaIn) {
        Meta m = (metaIn != null) ? metaIn : new Meta();

        // Fill guaranteed fields
        if (m.ts == 0L) m.ts = time.nowMs();
        if (m.frame == 0L) m.frame = time.frame();
        if (m.thread == null) m.thread = Thread.currentThread().getName();
        if (m.seq == 0L) m.seq = nextSeq.getAndIncrement();
        if (m.source == null) m.source = "engine";

        return new EventEnvelope(topic, payload, m);
    }

    // -------------------- misc --------------------

    public int queuedEventsApprox() {
        return queue.size();
    }

    private static String normalizeTopic(String t) {
        String s = t.trim();
        if (s.isEmpty()) return "";
        while (s.startsWith(".")) s = s.substring(1);
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        while (s.contains("..")) s = s.replace("..", ".");
        return s;
    }
}