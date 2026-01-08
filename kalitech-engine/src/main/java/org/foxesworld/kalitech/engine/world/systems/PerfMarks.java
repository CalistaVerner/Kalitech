// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world.systems;

import org.graalvm.polyglot.HostAccess;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PerfMarks
 * <p>
 * Lightweight per-frame marker ring ("spike hints") for fast offender identification.
 * JS/Java can tag work using {@link #mark(String)}; when a frame goes over budget, the last marks
 * are printed to quickly find the hot path without a heavyweight profiler.
 */
public final class PerfMarks {

    public static final int MAX_MARKS = 8;

    private final String[] ring = new String[MAX_MARKS];
    private final long[] ringNanos = new long[MAX_MARKS];
    private final AtomicLong lastMarkNanos = new AtomicLong();
    private int head = 0; // next write index
    private int count = 0;
    private volatile String lastMark = null;
    private volatile int maxLen = 96;

    public PerfMarks() {
    }

    private static String normalize(String raw, int maxLen) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.length() > maxLen) s = s.substring(0, maxLen);
        return s;
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    /**
     * Max stored mark string length (to avoid log spam).
     */
    public void setMaxLen(int n) {
        this.maxLen = clamp(n, 16, 512);
    }

    /**
     * Called once per frame to advance the ring.
     */
    public void beginFrame() {
        // No heavy work; ring is updated on mark()
    }

    /**
     * Mark an event (safe to call often, but keep the string short).
     */
    @HostAccess.Export
    public void mark(String raw) {
        String s = normalize(raw, maxLen);
        if (s == null) return;

        long now = System.nanoTime();

        lastMark = s;
        lastMarkNanos.set(now);

        ring[head] = s;
        ringNanos[head] = now;

        head = (head + 1) % MAX_MARKS;
        if (count < MAX_MARKS) count++;
    }

    /**
     * Convenience: mark a budget-related event (used by scheduler/budget queue).
     */
    @HostAccess.Export
    public void markBudget(String system, long spentNanos, long softBudgetNanos, boolean overSoft) {
        String s = (system == null ? "sys" : system);
        long spentMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, spentNanos));
        long softMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, softBudgetNanos));
        mark("budget:" + s + " spent=" + spentMs + "ms soft=" + softMs + "ms over=" + overSoft);
    }

    /**
     * Convenience: mark a backpressure event (apply queue overloaded).
     */
    @HostAccess.Export
    public void markBackpressure(String system, int pending, int threshold) {
        String s = (system == null ? "sys" : system);
        mark("backpressure:" + s + " pending=" + pending + " thr=" + threshold);
    }

    /**
     * Latest mark string.
     */
    public String getLastMark() {
        return lastMark;
    }

    public long getLastMarkNanos() {
        return lastMarkNanos.get();
    }

    /**
     * Ring snapshot (newest first).
     */
    @HostAccess.Export
    public MarkSnapshot[] snapshot() {
        int n = this.count;
        MarkSnapshot[] out = new MarkSnapshot[n];
        for (int i = 0; i < n; i++) {
            int idx = (head - 1 - i);
            if (idx < 0) idx += MAX_MARKS;
            out[i] = new MarkSnapshot(ring[idx], ringNanos[idx]);
        }
        return out;
    }

    @Override
    public String toString() {
        return "PerfMarks" + Arrays.toString(snapshot());
    }

    public static final class MarkSnapshot {
        @HostAccess.Export
        public final String mark;
        @HostAccess.Export
        public final long atNanos;

        public MarkSnapshot(String mark, long atNanos) {
            this.mark = mark;
            this.atNanos = atNanos;
        }

        @HostAccess.Export
        public long ageMs(long nowNanos) {
            long age = Math.max(0L, nowNanos - atNanos);
            return TimeUnit.NANOSECONDS.toMillis(age);
        }
    }
}