// FILE: PerfMarks.java
package org.foxesworld.kalitech.engine.world.systems;

import org.graalvm.polyglot.HostAccess;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight per-frame marker system ("spike hints").
 *
 * Goal:
 *  - Let JS/Java tag what's happening during a frame: ctx.perf().mark("shoot:spawn")
 *  - When the frame goes over budget, print the latest mark(s) to quickly identify offenders.
 *
 * No allocations in steady-state (ring buffer of fixed size).
 */
public final class PerfMarks {

    public static final int MAX_MARKS = 8;

    private final String[] ring = new String[MAX_MARKS];
    private final long[] ringNanos = new long[MAX_MARKS];

    private int head = 0; // next write index
    private int count = 0;

    private volatile String lastMark = null;
    private final AtomicLong lastMarkNanos = new AtomicLong();

    private volatile int maxLen = 96;

    public PerfMarks() {}

    public void setMaxLen(int maxLen) {
        this.maxLen = clamp(maxLen, 16, 512);
    }

    public void beginFrame() {
        // keep history; only reset "lastMark"
        lastMark = null;
        lastMarkNanos.set(0L);
    }

    public void mark(String raw) {
        String s = normalize(raw, maxLen);
        if (s == null) return;

        final long now = System.nanoTime();
        lastMark = s;
        lastMarkNanos.set(now);

        ring[head] = s;
        ringNanos[head] = now;
        head = (head + 1) % MAX_MARKS;
        count = Math.min(MAX_MARKS, count + 1);
    }

    public String getLastMark() {
        return lastMark;
    }

    public long getLastMarkNanos() {
        return lastMarkNanos.get();
    }

    /**
     * Copy newest-first marks, limited to max items.
     */
    public MarkSnapshot[] snapshot(int max) {
        int n = Math.max(0, Math.min(max, count));
        MarkSnapshot[] out = new MarkSnapshot[n];
        if (n == 0) return out;

        int idx = head - 1;
        if (idx < 0) idx += MAX_MARKS;

        for (int i = 0; i < n; i++) {
            String m = ring[idx];
            long t = ringNanos[idx];
            out[i] = new MarkSnapshot(m, t);

            idx--;
            if (idx < 0) idx += MAX_MARKS;
        }
        return out;
    }

    public static final class MarkSnapshot {
        @HostAccess.Export public final String mark;
        @HostAccess.Export public final long atNanos;

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

    private static String normalize(String raw, int maxLen) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        // keep it simple, stable for logs
        // (optional) lowercase only if you want:
        // s = s.toLowerCase(Locale.ROOT);

        if (s.length() > maxLen) s = s.substring(0, maxLen);
        return s;
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}