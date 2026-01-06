// FILE: org/foxesworld/kalitech/engine/modules/hud/HudSizeCache.java
package org.foxesworld.kalitech.engine.modules.hud;

import com.jme3.math.Vector3f;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores last known explicit sizes (w/h) by element id.
 * <p>
 * Why:
 * - Some Lemur/community builds don't immediately reflect preferredSize into GuiControl preferred size.
 * - We still need stable height for correct TOP-LEFT positioning of box-like elements.
 */
public final class HudSizeCache {

    private final ConcurrentHashMap<Integer, Vector3f> sizes = new ConcurrentHashMap<>();

    public void put(int id, float w, float h) {
        if (id <= 0) return;
        float ww = (Float.isFinite(w) && w >= 0f) ? w : 0f;
        float hh = (Float.isFinite(h) && h >= 0f) ? h : 0f;
        sizes.put(id, new Vector3f(ww, hh, 0f));
    }

    public Vector3f get(int id) {
        return id > 0 ? sizes.get(id) : null;
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