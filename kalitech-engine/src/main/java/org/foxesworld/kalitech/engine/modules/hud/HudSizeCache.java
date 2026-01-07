package org.foxesworld.kalitech.engine.modules.hud;

import java.util.concurrent.ConcurrentHashMap;

public final class HudSizeCache {

    private static final long ZERO = pack(0f, 0f);

    private final ConcurrentHashMap<Integer, Long> sizes = new ConcurrentHashMap<>();

    private static long pack(float w, float h) {
        final int wi = Float.floatToIntBits(sane(w));
        final int hi = Float.floatToIntBits(sane(h));
        return ((long) wi << 32) | (hi & 0xFFFFFFFFL);
    }

    private static float unpackW(long packed) {
        return Float.intBitsToFloat((int) (packed >>> 32));
    }

    private static float unpackH(long packed) {
        return Float.intBitsToFloat((int) packed);
    }

    private static float sane(float v) {
        if (!Float.isFinite(v) || v < 0f) return 0f;
        return v;
    }

    public void put(int id, float w, float h) {
        if (id <= 0) return;
        sizes.put(id, pack(w, h));
    }

    public void remove(int id) {
        if (id > 0) sizes.remove(id);
    }

    public void clear() {
        sizes.clear();
    }

    public void putW(int id, float w) {
        if (id <= 0) return;
        sizes.compute(id, (k, old) -> {
            final long v = (old != null) ? old : ZERO;
            return pack(w, unpackH(v));
        });
    }

    public void putH(int id, float h) {
        if (id <= 0) return;
        sizes.compute(id, (k, old) -> {
            final long v = (old != null) ? old : ZERO;
            return pack(unpackW(v), h);
        });
    }

    public float getW(int id) {
        if (id <= 0) return 0f;
        final Long v = sizes.get(id);
        return (v == null) ? 0f : unpackW(v);
    }

    public float getH(int id) {
        if (id <= 0) return 0f;
        final Long v = sizes.get(id);
        return (v == null) ? 0f : unpackH(v);
    }
}