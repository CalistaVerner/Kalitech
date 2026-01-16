// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/pipeline/ShadowWorkspace.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * Shared data container for the entire shadow pipeline.
 * <p>
 * Designed for zero-copy, immediate synchronization between filters.
 * Uses epoch/stamp mechanics to clear values without allocations.
 */
public final class ShadowWorkspace {

    private static final Logger log = LogManager.getLogger(ShadowWorkspace.class);

    private static final int DEFAULT_CAPACITY = 64;

    private final int numSplits;

    private Object[] frameValues;
    private long[] frameStamps;
    private int[] frameWriters;
    private long frameEpoch;

    private Object[][] splitValues;
    private long[][] splitStamps;
    private int[][] splitWriters;
    private long[] splitEpoch;

    /**
     * When enabled, each key may be written only once per epoch.
     * Any second write (even with same value) is treated as a pipeline contract violation.
     */
    private boolean strictWrites = false;

    /**
     * Current writer identifier set by the orchestrator (typically the filter class).
     */
    private int currentWriter = 0;

    public ShadowWorkspace(int numSplits) {
        if (numSplits < 1) throw new IllegalArgumentException("numSplits must be >= 1");
        this.numSplits = numSplits;

        this.frameValues = new Object[DEFAULT_CAPACITY];
        this.frameStamps = new long[DEFAULT_CAPACITY];
        this.frameWriters = new int[DEFAULT_CAPACITY];
        this.frameEpoch = 1L;

        this.splitValues = new Object[numSplits][DEFAULT_CAPACITY];
        this.splitStamps = new long[numSplits][DEFAULT_CAPACITY];
        this.splitWriters = new int[numSplits][DEFAULT_CAPACITY];
        this.splitEpoch = new long[numSplits];
        for (int i = 0; i < numSplits; i++) splitEpoch[i] = 1L;
    }

    public boolean isStrictWrites() {
        return strictWrites;
    }

    /**
     * Enables strict single-writer policy.
     * <p>
     * When enabled, writing the same key more than once within the same epoch
     * is treated as a pipeline contract violation and will throw.
     */
    public void setStrictWrites(boolean strictWrites) {
        this.strictWrites = strictWrites;
    }

    /**
     * Sets current writer identifier for diagnostics.
     * Orchestrator must call this before invoking filter hooks.
     */
    public void setCurrentWriter(int writerId) {
        this.currentWriter = writerId;
    }

    public void clearCurrentWriter() {
        this.currentWriter = 0;
    }

    private static void requireScope(ShadowKey<?> key, ShadowKey.Scope scope) {
        if (key.scope() != scope) {
            throw new IllegalArgumentException("Key scope mismatch: expected=" + scope + " actual=" + key.scope()
                    + " key=" + key.name());
        }
    }

    private static int grow(int current, int required) {
        int cap = Math.max(current, 1);
        while (cap < required) cap <<= 1;
        return cap;
    }

    // ---------------- FRAME SCOPE ----------------

    @SuppressWarnings("unchecked")
    private static <T> T cast(ShadowKey<T> key, Object v) {
        if (!key.type().isInstance(v)) {
            throw new ClassCastException("Invalid value type for " + key + ": " + v.getClass().getName());
        }
        return (T) v;
    }

    public int numSplits() {
        return numSplits;
    }

    /**
     * Begins a new frame epoch. All previous frame/split values become logically cleared.
     */
    public void beginFrame(long frameId) {
        frameEpoch++;
        if (frameEpoch == 0L) frameEpoch = 1L;

        for (int i = 0; i < numSplits; i++) {
            splitEpoch[i]++;
            if (splitEpoch[i] == 0L) splitEpoch[i] = 1L;
        }

        log.trace("[shadow][ws] beginFrame id={} epoch={}", frameId, frameEpoch);
    }

    public <T> void put(ShadowKey<T> key, T value) {
        Objects.requireNonNull(key, "key");
        requireScope(key, ShadowKey.Scope.FRAME);

        int idx = key.index();
        ensureFrameCapacity(idx);

        if (strictWrites && frameStamps[idx] == frameEpoch) {
            int prevWriter = frameWriters[idx];
            if (prevWriter != currentWriter) {
                String msg = "ShadowWorkspace strict write violation (frame): key=" + key.name()
                        + " idx=" + idx + " epoch=" + frameEpoch
                        + " prevWriter=" + prevWriter + " writer=" + currentWriter;
                log.error(msg);
                throw new IllegalStateException(msg);
            }
        }

        frameValues[idx] = value;
        frameStamps[idx] = frameEpoch;
        frameWriters[idx] = currentWriter;
    }

    public <T> T get(ShadowKey<T> key) {
        Objects.requireNonNull(key, "key");
        requireScope(key, ShadowKey.Scope.FRAME);

        int idx = key.index();
        if (idx >= frameStamps.length) return null;

        if (frameStamps[idx] != frameEpoch) return null;

        Object v = frameValues[idx];
        if (v == null) return null;

        return cast(key, v);
    }

    // ---------------- SPLIT SCOPE ----------------

    public <T> T getOrDefault(ShadowKey<T> key, T def) {
        T v = get(key);
        return v != null ? v : def;
    }

    public boolean has(ShadowKey<?> key) {
        Objects.requireNonNull(key, "key");
        requireScope(key, ShadowKey.Scope.FRAME);

        int idx = key.index();
        return idx < frameStamps.length && frameStamps[idx] == frameEpoch && frameValues[idx] != null;
    }

    // ---------------- INTERNAL ----------------

    public void remove(ShadowKey<?> key) {
        Objects.requireNonNull(key, "key");
        requireScope(key, ShadowKey.Scope.FRAME);

        int idx = key.index();
        if (idx >= frameStamps.length) return;

        frameValues[idx] = null;
        frameStamps[idx] = 0L;
    }

    public SplitView split(int splitIndex) {
        if (splitIndex < 0 || splitIndex >= numSplits) {
            throw new IllegalArgumentException("splitIndex out of range: " + splitIndex);
        }
        return new SplitView(splitIndex);
    }

    private void ensureFrameCapacity(int idx) {
        if (idx < frameValues.length) return;

        int newCap = grow(frameValues.length, idx + 1);
        Object[] nv = new Object[newCap];
        long[] ns = new long[newCap];
        int[] nw = new int[newCap];

        System.arraycopy(frameValues, 0, nv, 0, frameValues.length);
        System.arraycopy(frameStamps, 0, ns, 0, frameStamps.length);
        System.arraycopy(frameWriters, 0, nw, 0, frameWriters.length);

        frameValues = nv;
        frameStamps = ns;
        frameWriters = nw;

        log.debug("[shadow][ws] grow frame cap={}", newCap);
    }

    private void ensureSplitCapacity(int idx) {
        if (idx < splitValues[0].length) return;

        int oldCap = splitValues[0].length;
        int newCap = grow(oldCap, idx + 1);

        for (int s = 0; s < numSplits; s++) {
            Object[] nv = new Object[newCap];
            long[] ns = new long[newCap];
            int[] nw = new int[newCap];

            System.arraycopy(splitValues[s], 0, nv, 0, oldCap);
            System.arraycopy(splitStamps[s], 0, ns, 0, oldCap);
            System.arraycopy(splitWriters[s], 0, nw, 0, oldCap);

            splitValues[s] = nv;
            splitStamps[s] = ns;
            splitWriters[s] = nw;
        }

        log.debug("[shadow][ws] grow split cap={} splits={}", newCap, numSplits);
    }

    public final class SplitView {

        private final int splitIndex;

        private SplitView(int splitIndex) {
            this.splitIndex = splitIndex;
        }

        public int index() {
            return splitIndex;
        }

        public <T> void put(ShadowKey<T> key, T value) {
            Objects.requireNonNull(key, "key");
            requireScope(key, ShadowKey.Scope.SPLIT);

            int idx = key.index();
            ensureSplitCapacity(idx);

            if (strictWrites && splitStamps[splitIndex][idx] == splitEpoch[splitIndex]) {
                int prevWriter = splitWriters[splitIndex][idx];
                if (prevWriter != currentWriter) {
                    String msg = "ShadowWorkspace strict write violation (split): key=" + key.name()
                            + " idx=" + idx + " split=" + splitIndex
                            + " epoch=" + splitEpoch[splitIndex]
                            + " prevWriter=" + prevWriter + " writer=" + currentWriter;
                    log.error(msg);
                    throw new IllegalStateException(msg);
                }
            }

            splitValues[splitIndex][idx] = value;
            splitStamps[splitIndex][idx] = splitEpoch[splitIndex];
            splitWriters[splitIndex][idx] = currentWriter;
        }

        public <T> T get(ShadowKey<T> key) {
            Objects.requireNonNull(key, "key");
            requireScope(key, ShadowKey.Scope.SPLIT);

            int idx = key.index();
            if (idx >= splitStamps[splitIndex].length) return null;

            if (splitStamps[splitIndex][idx] != splitEpoch[splitIndex]) return null;

            Object v = splitValues[splitIndex][idx];
            if (v == null) return null;

            return cast(key, v);
        }

        public <T> T getOrDefault(ShadowKey<T> key, T def) {
            T v = get(key);
            return v != null ? v : def;
        }

        public boolean has(ShadowKey<?> key) {
            Objects.requireNonNull(key, "key");
            requireScope(key, ShadowKey.Scope.SPLIT);

            int idx = key.index();
            return idx < splitStamps[splitIndex].length
                    && splitStamps[splitIndex][idx] == splitEpoch[splitIndex]
                    && splitValues[splitIndex][idx] != null;
        }

        public void remove(ShadowKey<?> key) {
            Objects.requireNonNull(key, "key");
            requireScope(key, ShadowKey.Scope.SPLIT);

            int idx = key.index();
            if (idx >= splitStamps[splitIndex].length) return;

            splitValues[splitIndex][idx] = null;
            splitStamps[splitIndex][idx] = 0L;
        }
    }
}