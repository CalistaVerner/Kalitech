// FILE: org/foxesworld/kalitech/engine/modules/hud/HudSizeCache.java
package org.foxesworld.kalitech.engine.modules.hud;

import com.jme3.math.Vector3f;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores last known explicit sizes (w/h) by element id.
 *
 * Why:
 * - Some Lemur/community builds don't immediately reflect preferredSize into GuiControl preferred size.
 * - We still need stable width/height for correct TOP-LEFT positioning and hit-test bounds.
 *
 * Notes:
 * - Cache is best-effort: only explicit sizes set via API should be stored here.
 * - Never stores negative or NaN sizes.
 */
public final class HudSizeCache {

    private final ConcurrentHashMap<Integer, Vector3f> sizes = new ConcurrentHashMap<>();

    private static float sane(float v) {
        if (!Float.isFinite(v)) return 0f;
        return (v < 0f) ? 0f : v;
    }

    public void put(int id, float w, float h) {
        if (id <= 0) return;
        float ww = sane(w);
        float hh = sane(h);
        sizes.put(id, new Vector3f(ww, hh, 0f));
    }

    /**
     * Convenience: update width only (keeps cached height).
     */
    public void putW(int id, float w) {
        if (id <= 0) return;
        float ww = sane(w);
        sizes.compute(id, (k, old) -> {
            float hh = (old != null && Float.isFinite(old.y)) ? old.y : 0f;
            return new Vector3f(ww, sane(hh), 0f);
        });
    }

    /**
     * Convenience: update height only (keeps cached width).
     */
    public void putH(int id, float h) {
        if (id <= 0) return;
        float hh = sane(h);
        sizes.compute(id, (k, old) -> {
            float ww = (old != null && Float.isFinite(old.x)) ? old.x : 0f;
            return new Vector3f(sane(ww), hh, 0f);
        });
    }

    public Vector3f get(int id) {
        return id > 0 ? sizes.get(id) : null;
    }

    public float getW(int id) {
        Vector3f v = get(id);
        return (v != null && Float.isFinite(v.x) && v.x > 0f) ? v.x : 0f;
    }

    public float getH(int id) {
        Vector3f v = get(id);
        return (v != null && Float.isFinite(v.y) && v.y > 0f) ? v.y : 0f;
    }

    public void remove(int id) {
        if (id > 0) sizes.remove(id);
    }

    public void clear() {
        sizes.clear();
    }
}