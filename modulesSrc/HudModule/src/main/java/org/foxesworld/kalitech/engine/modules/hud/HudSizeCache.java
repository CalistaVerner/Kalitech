/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.hud;

import java.util.concurrent.ConcurrentHashMap;

public final class HudSizeCache {
    private static final long ZERO = HudSizeCache.pack(0.0f, 0.0f);
    private final ConcurrentHashMap<Integer, Long> sizes = new ConcurrentHashMap();

    private static long pack(float w, float h) {
        int wi = Float.floatToIntBits(HudSizeCache.sane(w));
        int hi = Float.floatToIntBits(HudSizeCache.sane(h));
        return (long)wi << 32 | (long)hi & 0xFFFFFFFFL;
    }

    private static float unpackW(long packed) {
        return Float.intBitsToFloat((int)(packed >>> 32));
    }

    private static float unpackH(long packed) {
        return Float.intBitsToFloat((int)packed);
    }

    private static float sane(float v) {
        if (!Float.isFinite(v) || v < 0.0f) {
            return 0.0f;
        }
        return v;
    }

    public void put(int id, float w, float h) {
        if (id <= 0) {
            return;
        }
        this.sizes.put(id, HudSizeCache.pack(w, h));
    }

    public void remove(int id) {
        if (id > 0) {
            this.sizes.remove(id);
        }
    }

    public void clear() {
        this.sizes.clear();
    }

    public void putW(int id, float w) {
        if (id <= 0) {
            return;
        }
        this.sizes.compute(id, (k, old) -> {
            long v = old != null ? old : ZERO;
            return HudSizeCache.pack(w, HudSizeCache.unpackH(v));
        });
    }

    public void putH(int id, float h) {
        if (id <= 0) {
            return;
        }
        this.sizes.compute(id, (k, old) -> {
            long v = old != null ? old : ZERO;
            return HudSizeCache.pack(HudSizeCache.unpackW(v), h);
        });
    }

    public float getW(int id) {
        if (id <= 0) {
            return 0.0f;
        }
        Long v = this.sizes.get(id);
        return v == null ? 0.0f : HudSizeCache.unpackW(v);
    }

    public float getH(int id) {
        if (id <= 0) {
            return 0.0f;
        }
        Long v = this.sizes.get(id);
        return v == null ? 0.0f : HudSizeCache.unpackH(v);
    }
}

