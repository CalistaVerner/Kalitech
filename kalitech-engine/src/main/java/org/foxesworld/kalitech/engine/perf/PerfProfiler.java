// FILE: org/foxesworld/kalitech/engine/perf/PerfProfiler.java
package org.foxesworld.kalitech.engine.perf;

import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal per-frame profiler for the engine.
 *
 * Design goals:
 * - Near-zero overhead when disabled.
 * - No allocations per frame (after warmup).
 * - Rolling-window stats avg/p50/p95/max and spike counts.
 * - Optional JSONL output to file for offline analysis.
 *
 * Usage pattern:
 *   perf.beginFrame();
 *   long t = perf.begin("camera.flush");
 *   ... work ...
 *   perf.end("camera.flush", t);
 *   perf.endFrame(dtSeconds);
 *
 * Notes:
 * - File format is JSON Lines: one JSON object per line.
 * - When enabled+writeToFile, a header and footer meta records are written.
 */
public final class PerfProfiler implements Closeable {

    public static final class Config {
        public boolean enabled = true;

        /** Rolling window size per block. */
        public int windowFrames = 600;

        /** Emit summary every N frames. Set <=0 to disable periodic summaries. */
        public int summaryEveryFrames = 120;

        /** Spike threshold (nanos). Spike counter increments when block >= threshold. */
        public long spikeThresholdNanos = 1_000_000; // 1ms default

        /** Optional console prefix (if you also print to log). */
        public String prefix = "[perf]";

        /** Output to file in JSONL format. */
        public boolean writeToFile = true;

        /** Where to write JSONL; parent directories are created. */
        public String outputFile = "logs/perf-engine.jsonl";

        /** Flush file on each summary to keep data safe (slightly more IO). */
        public boolean flushEverySummary = true;

        /** Also print summary to log4j2 (in addition to file). */
        public boolean writeToLog = false;
    }

    private final Logger log;
    private final Config cfg;

    private int frameIndex = 0;

    // keep insertion order for stable output
    private final LinkedHashMap<String, StatWindow> windows = new LinkedHashMap<>();

    // optional file output
    private BufferedWriter file;
    private boolean fileOk = false;

    public PerfProfiler(Logger log, Config cfg) {
        this.log = log;
        this.cfg = (cfg != null) ? cfg : new Config();
        if (this.cfg.enabled && this.cfg.writeToFile) {
            openFileSafe();
        }
    }

    public boolean enabled() {
        return cfg.enabled;
    }

    public void setEnabled(boolean enabled) {
        boolean was = cfg.enabled;
        cfg.enabled = enabled;

        if (!was && enabled) {
            // enabling
            if (cfg.writeToFile) openFileSafe();
        } else if (was && !enabled) {
            // disabling
            close();
        }
    }

    /** Call once per frame. */
    public void beginFrame() {
        if (!cfg.enabled) return;
        // no-op
    }

    /** Call once per frame with dt. */
    public void endFrame(double dtSeconds) {
        if (!cfg.enabled) return;

        frameIndex++;
        if (cfg.summaryEveryFrames > 0 && (frameIndex % cfg.summaryEveryFrames) == 0) {
            dumpSummary(dtSeconds);
        }
    }

    /** Start measuring a block. Returns start time (nano). */
    public long begin(String name) {
        if (!cfg.enabled) return 0L;
        return System.nanoTime();
    }

    /** End measuring a block, given the start time from begin(). */
    public void end(String name, long startNanos) {
        if (!cfg.enabled) return;
        if (startNanos == 0L) return;
        if (name == null || name.isEmpty()) return;

        final long dt = System.nanoTime() - startNanos;

        StatWindow w = windows.get(name);
        if (w == null) {
            w = new StatWindow(name, cfg.windowFrames, cfg.spikeThresholdNanos);
            windows.put(name, w);
        }
        w.add(dt);
    }

    /** Convenience scoped helper (manual try/finally). */
    public Scope scope(String name) {
        if (!cfg.enabled) return Scope.NOOP;
        return new Scope(this, name);
    }

    public static class Scope implements AutoCloseable {
        static final Scope NOOP = new Scope(null, null) {
            @Override public void close() {}
        };

        private final PerfProfiler p;
        private final String name;
        private final long t0;

        Scope(PerfProfiler p, String name) {
            this.p = p;
            this.name = name;
            this.t0 = (p != null) ? System.nanoTime() : 0L;
        }

        @Override
        public void close() {
            if (p != null) p.end(name, t0);
        }
    }

    private void dumpSummary(double dtSeconds) {
        // 1) Write to file (JSONL)
        if (fileOk) {
            writeFileLine("{\"type\":\"frame\",\"frame\":" + frameIndex + ",\"dt_ms\":" + fmtMsNum(dtSeconds * 1000.0) + "}");
            for (Map.Entry<String, StatWindow> e : windows.entrySet()) {
                StatWindow w = e.getValue();
                if (w.count == 0) continue;

                StatWindow.Summary s = w.summarize();
                writeFileLine(
                        "{\"type\":\"block\",\"name\":\"" + escapeJson(e.getKey()) + "\"" +
                                ",\"avg_ms\":" + nanosToMsNum(s.avg) +
                                ",\"p50_ms\":" + nanosToMsNum(s.p50) +
                                ",\"p95_ms\":" + nanosToMsNum(s.p95) +
                                ",\"max_ms\":" + nanosToMsNum(s.max) +
                                ",\"spikes\":" + s.spikes +
                                "}"
                );
            }
            if (cfg.flushEverySummary) flushFileSafe();
        }

        // 2) Optional: also log to console
        if (cfg.writeToLog) {
            StringBuilder sb = new StringBuilder(2048);
            sb.append(cfg.prefix)
                    .append(" frame=").append(frameIndex)
                    .append(" dt=").append(String.format("%.3fms", dtSeconds * 1000.0))
                    .append(" window=").append(cfg.windowFrames)
                    .append('\n');

            for (Map.Entry<String, StatWindow> e : windows.entrySet()) {
                StatWindow w = e.getValue();
                if (w.count == 0) continue;

                StatWindow.Summary s = w.summarize();
                sb.append("  ")
                        .append(padRight(e.getKey(), 20))
                        .append(" avg=").append(formatMs(s.avg))
                        .append(" p50=").append(formatMs(s.p50))
                        .append(" p95=").append(formatMs(s.p95))
                        .append(" max=").append(formatMs(s.max))
                        .append(" spikes=").append(s.spikes)
                        .append('\n');
            }

            log.info(sb.toString());
        }
    }

    private void openFileSafe() {
        closeFileOnly();

        if (cfg.outputFile == null || cfg.outputFile.trim().isEmpty()) {
            fileOk = false;
            return;
        }

        try {
            Path path = Paths.get(cfg.outputFile);
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);

            file = Files.newBufferedWriter(
                    path,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            fileOk = true;

            writeFileLine("{\"type\":\"meta\",\"started\":\"" + Instant.now() + "\",\"windowFrames\":" + cfg.windowFrames +
                    ",\"summaryEveryFrames\":" + cfg.summaryEveryFrames +
                    ",\"spikeThresholdMs\":" + nanosToMsNum(cfg.spikeThresholdNanos) +
                    "}");

            flushFileSafe();
        } catch (Throwable t) {
            fileOk = false;
            closeFileOnly();
            // Do not spam; one warning is enough.
            try { log.warn("PerfProfiler: file output disabled: {}", t.toString()); } catch (Throwable ignored) {}
        }
    }

    private void writeFileLine(String line) {
        if (!fileOk || file == null) return;
        try {
            file.write(line);
            file.newLine();
        } catch (IOException e) {
            fileOk = false;
            closeFileOnly();
            try { log.warn("PerfProfiler: file write failed, disabling: {}", e.toString()); } catch (Throwable ignored) {}
        }
    }

    private void flushFileSafe() {
        if (!fileOk || file == null) return;
        try {
            file.flush();
        } catch (IOException e) {
            fileOk = false;
            closeFileOnly();
            try { log.warn("PerfProfiler: file flush failed, disabling: {}", e.toString()); } catch (Throwable ignored) {}
        }
    }

    private void closeFileOnly() {
        if (file != null) {
            try { file.close(); } catch (Throwable ignored) {}
        }
        file = null;
        fileOk = false;
    }

    @Override
    public void close() {
        // close file writer if open
        if (file != null) {
            try {
                // footer
                writeFileLine("{\"type\":\"meta\",\"ended\":\"" + Instant.now() + "\"}");
                file.flush();
            } catch (Throwable ignored) {
            }
            closeFileOnly();
        }
    }

    private static String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s;
        StringBuilder sb = new StringBuilder(n);
        sb.append(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }

    private static String formatMs(long nanos) {
        return String.format("%.3fms", nanos / 1_000_000.0);
    }

    private static String nanosToMsNum(long nanos) {
        return String.format("%.3f", nanos / 1_000_000.0);
    }

    private static String fmtMsNum(double ms) {
        return String.format("%.3f", ms);
    }

    private static String escapeJson(String s) {
        if (s == null || s.isEmpty()) return "";
        // minimal escaping for JSON string
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    // -----------------------------
    // Rolling window stats
    // -----------------------------
    static final class StatWindow {
        final String name;
        final long[] ring;
        final long[] scratch; // for sorting percentiles
        final long spikeThreshold;

        int idx = 0;
        int count = 0;

        long sum = 0;
        long spikes = 0;

        StatWindow(String name, int windowSize, long spikeThreshold) {
            this.name = name;
            int sz = Math.max(60, windowSize);
            this.ring = new long[sz];
            this.scratch = new long[sz];
            this.spikeThreshold = Math.max(0, spikeThreshold);
        }

        void add(long nanos) {
            // remove overwritten element from sum if ring is full
            if (count == ring.length) {
                long old = ring[idx];
                sum -= old;

                // spikes is monotonic in this minimal version (cheap).
                // If you need exact rolling spike count, keep a ring of spike flags too.
            } else {
                count++;
            }

            ring[idx] = nanos;
            sum += nanos;

            if (spikeThreshold > 0 && nanos >= spikeThreshold) spikes++;

            idx++;
            if (idx >= ring.length) idx = 0;
        }

        Summary summarize() {
            int n = count;
            if (n <= 0) return new Summary(0, 0, 0, 0, 0);

            // copy window values for percentile computation
            long localMax = 0;
            for (int i = 0; i < n; i++) {
                long v = ring[i];
                scratch[i] = v;
                if (v > localMax) localMax = v;
            }

            Arrays.sort(scratch, 0, n);

            long avg = sum / n;
            long p50 = percentileSorted(scratch, n, 0.50);
            long p95 = percentileSorted(scratch, n, 0.95);

            return new Summary(avg, p50, p95, localMax, spikes);
        }

        static long percentileSorted(long[] sorted, int n, double q) {
            if (n <= 0) return 0;
            if (q <= 0) return sorted[0];
            if (q >= 1) return sorted[n - 1];
            // nearest-rank
            int rank = (int) Math.ceil(q * n) - 1;
            if (rank < 0) rank = 0;
            if (rank >= n) rank = n - 1;
            return sorted[rank];
        }

        static final class Summary {
            final long avg, p50, p95, max, spikes;
            Summary(long avg, long p50, long p95, long max, long spikes) {
                this.avg = avg;
                this.p50 = p50;
                this.p95 = p95;
                this.max = max;
                this.spikes = spikes;
            }
        }
    }
}