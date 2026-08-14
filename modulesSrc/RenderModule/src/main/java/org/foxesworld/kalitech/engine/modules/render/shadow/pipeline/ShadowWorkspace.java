/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadow.pipeline.ShadowKey;

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
    private boolean strictWrites = false;
    private int currentWriter = 0;

    public ShadowWorkspace(int numSplits) {
        if (numSplits < 1) {
            throw new IllegalArgumentException("numSplits must be >= 1");
        }
        this.numSplits = numSplits;
        this.frameValues = new Object[64];
        this.frameStamps = new long[64];
        this.frameWriters = new int[64];
        this.frameEpoch = 1L;
        this.splitValues = new Object[numSplits][64];
        this.splitStamps = new long[numSplits][64];
        this.splitWriters = new int[numSplits][64];
        this.splitEpoch = new long[numSplits];
        for (int i = 0; i < numSplits; ++i) {
            this.splitEpoch[i] = 1L;
        }
    }

    public boolean isStrictWrites() {
        return this.strictWrites;
    }

    public void setStrictWrites(boolean strictWrites) {
        this.strictWrites = strictWrites;
    }

    public void setCurrentWriter(int writerId) {
        this.currentWriter = writerId;
    }

    public void clearCurrentWriter() {
        this.currentWriter = 0;
    }

    private static void requireScope(ShadowKey<?> key, ShadowKey.Scope scope) {
        if (key.scope() != scope) {
            throw new IllegalArgumentException("Key scope mismatch: expected=" + String.valueOf((Object)scope) + " actual=" + String.valueOf((Object)key.scope()) + " key=" + key.name());
        }
    }

    private static int grow(int current, int required) {
        int cap;
        for (cap = Math.max(current, 1); cap < required; cap <<= 1) {
        }
        return cap;
    }

    private static <T> T cast(ShadowKey<T> key, Object v) {
        if (!key.type().isInstance(v)) {
            throw new ClassCastException("Invalid value type for " + String.valueOf(key) + ": " + v.getClass().getName());
        }
        return (T)v;
    }

    public int numSplits() {
        return this.numSplits;
    }

    public void beginFrame(long frameId) {
        ++this.frameEpoch;
        if (this.frameEpoch == 0L) {
            this.frameEpoch = 1L;
        }
        for (int i = 0; i < this.numSplits; ++i) {
            int n = i;
            this.splitEpoch[n] = this.splitEpoch[n] + 1L;
            if (this.splitEpoch[i] != 0L) continue;
            this.splitEpoch[i] = 1L;
        }
        log.trace("[shadow][ws] beginFrame id={} epoch={}", (Object)frameId, (Object)this.frameEpoch);
    }

    public <T> void put(ShadowKey<T> key, T value) {
        int prevWriter;
        Objects.requireNonNull(key, "key");
        ShadowWorkspace.requireScope(key, ShadowKey.Scope.FRAME);
        int idx = key.index();
        this.ensureFrameCapacity(idx);
        if (this.strictWrites && this.frameStamps[idx] == this.frameEpoch && (prevWriter = this.frameWriters[idx]) != this.currentWriter) {
            String msg = "ShadowWorkspace strict write violation (frame): key=" + key.name() + " idx=" + idx + " epoch=" + this.frameEpoch + " prevWriter=" + prevWriter + " writer=" + this.currentWriter;
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        this.frameValues[idx] = value;
        this.frameStamps[idx] = this.frameEpoch;
        this.frameWriters[idx] = this.currentWriter;
    }

    public <T> T get(ShadowKey<T> key) {
        Objects.requireNonNull(key, "key");
        ShadowWorkspace.requireScope(key, ShadowKey.Scope.FRAME);
        int idx = key.index();
        if (idx >= this.frameStamps.length) {
            return null;
        }
        if (this.frameStamps[idx] != this.frameEpoch) {
            return null;
        }
        Object v = this.frameValues[idx];
        if (v == null) {
            return null;
        }
        return ShadowWorkspace.cast(key, v);
    }

    public <T> T getOrDefault(ShadowKey<T> key, T def) {
        T v = this.get(key);
        return v != null ? v : def;
    }

    public boolean has(ShadowKey<?> key) {
        Objects.requireNonNull(key, "key");
        ShadowWorkspace.requireScope(key, ShadowKey.Scope.FRAME);
        int idx = key.index();
        return idx < this.frameStamps.length && this.frameStamps[idx] == this.frameEpoch && this.frameValues[idx] != null;
    }

    public void remove(ShadowKey<?> key) {
        Objects.requireNonNull(key, "key");
        ShadowWorkspace.requireScope(key, ShadowKey.Scope.FRAME);
        int idx = key.index();
        if (idx >= this.frameStamps.length) {
            return;
        }
        this.frameValues[idx] = null;
        this.frameStamps[idx] = 0L;
    }

    public SplitView split(int splitIndex) {
        if (splitIndex < 0 || splitIndex >= this.numSplits) {
            throw new IllegalArgumentException("splitIndex out of range: " + splitIndex);
        }
        return new SplitView(splitIndex);
    }

    private void ensureFrameCapacity(int idx) {
        if (idx < this.frameValues.length) {
            return;
        }
        int newCap = ShadowWorkspace.grow(this.frameValues.length, idx + 1);
        Object[] nv = new Object[newCap];
        long[] ns = new long[newCap];
        int[] nw = new int[newCap];
        System.arraycopy(this.frameValues, 0, nv, 0, this.frameValues.length);
        System.arraycopy(this.frameStamps, 0, ns, 0, this.frameStamps.length);
        System.arraycopy(this.frameWriters, 0, nw, 0, this.frameWriters.length);
        this.frameValues = nv;
        this.frameStamps = ns;
        this.frameWriters = nw;
        log.debug("[shadow][ws] grow frame cap={}", (Object)newCap);
    }

    private void ensureSplitCapacity(int idx) {
        if (idx < this.splitValues[0].length) {
            return;
        }
        int oldCap = this.splitValues[0].length;
        int newCap = ShadowWorkspace.grow(oldCap, idx + 1);
        for (int s = 0; s < this.numSplits; ++s) {
            Object[] nv = new Object[newCap];
            long[] ns = new long[newCap];
            int[] nw = new int[newCap];
            System.arraycopy(this.splitValues[s], 0, nv, 0, oldCap);
            System.arraycopy(this.splitStamps[s], 0, ns, 0, oldCap);
            System.arraycopy(this.splitWriters[s], 0, nw, 0, oldCap);
            this.splitValues[s] = nv;
            this.splitStamps[s] = ns;
            this.splitWriters[s] = nw;
        }
        log.debug("[shadow][ws] grow split cap={} splits={}", (Object)newCap, (Object)this.numSplits);
    }

    public final class SplitView {
        private final int splitIndex;

        private SplitView(int splitIndex) {
            this.splitIndex = splitIndex;
        }

        public int index() {
            return this.splitIndex;
        }

        public <T> void put(ShadowKey<T> key, T value) {
            int prevWriter;
            Objects.requireNonNull(key, "key");
            ShadowWorkspace.requireScope(key, ShadowKey.Scope.SPLIT);
            int idx = key.index();
            ShadowWorkspace.this.ensureSplitCapacity(idx);
            if (ShadowWorkspace.this.strictWrites && ShadowWorkspace.this.splitStamps[this.splitIndex][idx] == ShadowWorkspace.this.splitEpoch[this.splitIndex] && (prevWriter = ShadowWorkspace.this.splitWriters[this.splitIndex][idx]) != ShadowWorkspace.this.currentWriter) {
                String msg = "ShadowWorkspace strict write violation (split): key=" + key.name() + " idx=" + idx + " split=" + this.splitIndex + " epoch=" + ShadowWorkspace.this.splitEpoch[this.splitIndex] + " prevWriter=" + prevWriter + " writer=" + ShadowWorkspace.this.currentWriter;
                log.error(msg);
                throw new IllegalStateException(msg);
            }
            ShadowWorkspace.this.splitValues[this.splitIndex][idx] = value;
            ShadowWorkspace.this.splitStamps[this.splitIndex][idx] = ShadowWorkspace.this.splitEpoch[this.splitIndex];
            ShadowWorkspace.this.splitWriters[this.splitIndex][idx] = ShadowWorkspace.this.currentWriter;
        }

        public <T> T get(ShadowKey<T> key) {
            Objects.requireNonNull(key, "key");
            ShadowWorkspace.requireScope(key, ShadowKey.Scope.SPLIT);
            int idx = key.index();
            if (idx >= ShadowWorkspace.this.splitStamps[this.splitIndex].length) {
                return null;
            }
            if (ShadowWorkspace.this.splitStamps[this.splitIndex][idx] != ShadowWorkspace.this.splitEpoch[this.splitIndex]) {
                return null;
            }
            Object v = ShadowWorkspace.this.splitValues[this.splitIndex][idx];
            if (v == null) {
                return null;
            }
            return ShadowWorkspace.cast(key, v);
        }

        public <T> T getOrDefault(ShadowKey<T> key, T def) {
            T v = this.get(key);
            return v != null ? v : def;
        }

        public boolean has(ShadowKey<?> key) {
            Objects.requireNonNull(key, "key");
            ShadowWorkspace.requireScope(key, ShadowKey.Scope.SPLIT);
            int idx = key.index();
            return idx < ShadowWorkspace.this.splitStamps[this.splitIndex].length && ShadowWorkspace.this.splitStamps[this.splitIndex][idx] == ShadowWorkspace.this.splitEpoch[this.splitIndex] && ShadowWorkspace.this.splitValues[this.splitIndex][idx] != null;
        }

        public void remove(ShadowKey<?> key) {
            Objects.requireNonNull(key, "key");
            ShadowWorkspace.requireScope(key, ShadowKey.Scope.SPLIT);
            int idx = key.index();
            if (idx >= ShadowWorkspace.this.splitStamps[this.splitIndex].length) {
                return;
            }
            ShadowWorkspace.this.splitValues[this.splitIndex][idx] = null;
            ShadowWorkspace.this.splitStamps[this.splitIndex][idx] = 0L;
        }
    }
}

