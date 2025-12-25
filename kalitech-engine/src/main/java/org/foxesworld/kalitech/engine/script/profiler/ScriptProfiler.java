// FILE: ScriptProfiler.java
package org.foxesworld.kalitech.engine.script.profiler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight per-frame / rolling profiler for the scripting subsystem.
 *
 * <p>Автор: Calista Verner</p>
 *
 * Tracks:
 * - module init/update total time + calls + errors
 * - jobs drained count + time
 * - events pumped count + time
 *
 * Logs top N periodically (default every 2s).
 */
public final class ScriptProfiler {

    private static final Logger log = LogManager.getLogger(ScriptProfiler.class);

    public enum Phase { INIT, UPDATE, DESTROY, SERIALIZE, DESERIALIZE }

    public static final class ModuleStats {
        public final String moduleId;

        // totals
        public final AtomicLong initCalls = new AtomicLong();
        public final AtomicLong initTimeNanos = new AtomicLong();
        public final AtomicLong updateCalls = new AtomicLong();
        public final AtomicLong updateTimeNanos = new AtomicLong();
        public final AtomicLong errors = new AtomicLong();

        // last window (since last report)
        final AtomicLong wInitCalls = new AtomicLong();
        final AtomicLong wInitTimeNanos = new AtomicLong();
        final AtomicLong wUpdateCalls = new AtomicLong();
        final AtomicLong wUpdateTimeNanos = new AtomicLong();
        final AtomicLong wErrors = new AtomicLong();

        ModuleStats(String moduleId) {
            this.moduleId = moduleId;
        }
    }

    private final Map<String, ModuleStats> modules = new ConcurrentHashMap<>();

    // subsystem counters
    private final AtomicLong jobsCount = new AtomicLong();
    private final AtomicLong jobsTimeNanos = new AtomicLong();
    private final AtomicLong eventsCount = new AtomicLong();
    private final AtomicLong eventsTimeNanos = new AtomicLong();

    private final AtomicLong wJobsCount = new AtomicLong();
    private final AtomicLong wJobsTimeNanos = new AtomicLong();
    private final AtomicLong wEventsCount = new AtomicLong();
    private final AtomicLong wEventsTimeNanos = new AtomicLong();

    // reporting
    private volatile long reportEveryNanos = 2_000_000_000L; // 2s
    private volatile int topN = 8;
    private volatile boolean enabled = true;

    private long lastReportNanos = System.nanoTime();

    public ScriptProfiler setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public ScriptProfiler setReportEveryNanos(long nanos) {
        this.reportEveryNanos = Math.max(250_000_000L, nanos); // min 250ms
        return this;
    }

    public ScriptProfiler setTopN(int topN) {
        this.topN = Math.max(1, Math.min(32, topN));
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ---- record API ----

    public long begin() {
        return enabled ? System.nanoTime() : 0L;
    }

    public void endModule(String moduleId, Phase phase, long t0Nanos, boolean ok) {
        if (!enabled || moduleId == null) return;
        long dt = Math.max(0L, System.nanoTime() - t0Nanos);

        ModuleStats s = modules.computeIfAbsent(moduleId, ModuleStats::new);

        if (!ok) {
            s.errors.incrementAndGet();
            s.wErrors.incrementAndGet();
        }

        switch (phase) {
            case INIT -> {
                s.initCalls.incrementAndGet();
                s.initTimeNanos.addAndGet(dt);
                s.wInitCalls.incrementAndGet();
                s.wInitTimeNanos.addAndGet(dt);
            }
            case UPDATE -> {
                s.updateCalls.incrementAndGet();
                s.updateTimeNanos.addAndGet(dt);
                s.wUpdateCalls.incrementAndGet();
                s.wUpdateTimeNanos.addAndGet(dt);
            }
            default -> {
                // we can extend later
            }
        }
    }

    public void recordJobs(int drainedCount, long dtNanos) {
        if (!enabled) return;
        jobsCount.addAndGet(Math.max(0, drainedCount));
        jobsTimeNanos.addAndGet(Math.max(0L, dtNanos));
        wJobsCount.addAndGet(Math.max(0, drainedCount));
        wJobsTimeNanos.addAndGet(Math.max(0L, dtNanos));
    }

    public void recordEvents(int pumpedCount, long dtNanos) {
        if (!enabled) return;
        eventsCount.addAndGet(Math.max(0, pumpedCount));
        eventsTimeNanos.addAndGet(Math.max(0L, dtNanos));
        wEventsCount.addAndGet(Math.max(0, pumpedCount));
        wEventsTimeNanos.addAndGet(Math.max(0L, dtNanos));
    }

    /** Call once per frame (end of ScriptSubsystem.update()). */
    public void tick() {
        if (!enabled) return;
        long now = System.nanoTime();
        if (now - lastReportNanos < reportEveryNanos) return;
        lastReportNanos = now;

        // Snapshot window + reset window counters
        long wj = wJobsCount.getAndSet(0);
        long wjt = wJobsTimeNanos.getAndSet(0);
        long we = wEventsCount.getAndSet(0);
        long wet = wEventsTimeNanos.getAndSet(0);

        List<ModuleRow> rows = new ArrayList<>(modules.size());
        for (ModuleStats s : modules.values()) {
            long uCalls = s.wUpdateCalls.getAndSet(0);
            long uTime = s.wUpdateTimeNanos.getAndSet(0);
            long iCalls = s.wInitCalls.getAndSet(0);
            long iTime = s.wInitTimeNanos.getAndSet(0);
            long errs = s.wErrors.getAndSet(0);

            if (uCalls == 0 && iCalls == 0 && errs == 0) continue;

            rows.add(new ModuleRow(s.moduleId, uCalls, uTime, iCalls, iTime, errs));
        }

        rows.sort(Comparator.comparingLong(ModuleRow::sortKey).reversed());

        StringBuilder sb = new StringBuilder(512);
        sb.append("[ScriptProfiler] window=")
                .append(reportEveryNanos / 1_000_000_000.0).append("s")
                .append(" jobs=").append(wj).append(" (").append(ms(wjt)).append("ms)")
                .append(" events=").append(we).append(" (").append(ms(wet)).append("ms)")
                .append(" modules=").append(rows.size());

        int n = Math.min(topN, rows.size());
        for (int i = 0; i < n; i++) {
            ModuleRow r = rows.get(i);
            sb.append("\n  #").append(i + 1).append(" ").append(r.moduleId)
                    .append(" update=").append(r.updateCalls).append(" (").append(ms(r.updateTimeNanos)).append("ms)")
                    .append(" init=").append(r.initCalls).append(" (").append(ms(r.initTimeNanos)).append("ms)")
                    .append(" err=").append(r.errors);
        }

        log.info(sb.toString());
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record ModuleRow(String moduleId, long updateCalls, long updateTimeNanos,
                             long initCalls, long initTimeNanos, long errors) {
        long sortKey() {
            // prioritize update time, then init time, then errors
            return updateTimeNanos * 2 + initTimeNanos + errors * 1_000_000L;
        }
    }
}