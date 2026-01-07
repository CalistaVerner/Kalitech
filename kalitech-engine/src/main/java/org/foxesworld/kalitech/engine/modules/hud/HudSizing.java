// FILE: org/foxesworld/kalitech/engine/modules/hud/HudSizing.java
package org.foxesworld.kalitech.engine.modules.hud;

import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.core.GuiControl;

/**
 * Fork-proof sizing utilities for Lemur elements.
 *
 * Goals:
 * - set preferred size AND try to set real size
 * - call invalidation/layout hooks where available
 * - keep size cache updated for reliable width/height math
 *
 * NOTE:
 * - In Lemur forks, GuiControl may expose different sizing APIs.
 *   We call them best-effort via reflection.
 */
public final class HudSizing {
    private HudSizing() {
    }

    public static boolean isBoxLike(Spatial s) {
        return (s instanceof Panel) || (s instanceof Container);
    }

    public static float preferredW(Spatial s) {
        if (s == null) return 0f;
        try {
            GuiControl gc = s.getControl(GuiControl.class);
            if (gc != null) {
                Vector3f ps = gc.getPreferredSize();
                if (ps != null && Float.isFinite(ps.x) && ps.x > 0f) return ps.x;
            }
        } catch (Throwable ignore) {
        }
        return 0f;
    }

    public static float preferredH(Spatial s) {
        if (s == null) return 0f;
        try {
            GuiControl gc = s.getControl(GuiControl.class);
            if (gc != null) {
                Vector3f ps = gc.getPreferredSize();
                if (ps != null && Float.isFinite(ps.y) && ps.y > 0f) return ps.y;
            }
        } catch (Throwable ignore) {
        }
        return 0f;
    }

    /**
     * Best-effort element width:
     * 1) cached explicit size
     * 2) guiControl preferred size
     */
    public static float widthOf(int id, Spatial s, HudSizeCache cache) {
        if (cache != null) {
            float cw = cache.getW(id);
            if (cw > 0f) return cw;
        }
        return preferredW(s);
    }

    /**
     * Best-effort element height:
     * 1) cached explicit size
     * 2) guiControl preferred size
     */
    public static float heightOf(int id, Spatial s, HudSizeCache cache) {
        if (cache != null) {
            float ch = cache.getH(id);
            if (ch > 0f) return ch;
        }
        return preferredH(s);
    }

    private static float sane(float v) {
        if (!Float.isFinite(v)) return 0f;
        return (v < 0f) ? 0f : v;
    }

    private static void reflectCall(Object target, String method, Class<?>[] sig, Object[] args) {
        if (target == null) return;
        try {
            var m = target.getClass().getMethod(method, sig);
            m.setAccessible(true);
            m.invoke(target, args);
        } catch (Throwable ignore) {
        }
    }

    private static void tryInvalidateAndLayout(GuiControl gc) {
        if (gc == null) return;

        // Most common fork variants
        reflectCall(gc, "invalidate", new Class<?>[]{}, new Object[]{});
        reflectCall(gc, "layout", new Class<?>[]{}, new Object[]{});
        reflectCall(gc, "refresh", new Class<?>[]{}, new Object[]{});
        reflectCall(gc, "updateLogicalState", new Class<?>[]{float.class}, new Object[]{0f});
        reflectCall(gc, "updateGeometricState", new Class<?>[]{}, new Object[]{});

        // Some forks add "updateLayout"/"rebuild"
        reflectCall(gc, "updateLayout", new Class<?>[]{}, new Object[]{});
        reflectCall(gc, "rebuild", new Class<?>[]{}, new Object[]{});
    }

    /**
     * Force size for Lemur spatials:
     * - setPreferredSize on Panel/Container
     * - setPreferredSize on GuiControl
     * - try setSize()/reshape() via reflection for forks
     * - call invalidation/layout hooks where available
     * - update size cache
     */
    public static void forceSize(int id, Spatial s, float w, float h, HudSizeCache cache) {
        if (s == null) return;

        final float ww = sane(w);
        final float hh = sane(h);
        final Vector3f sz = new Vector3f(ww, hh, 0f);

        if (cache != null) cache.put(id, ww, hh);

        // preferred size on known types (works on vanilla Lemur and many forks)
        try {
            if (s instanceof Panel p) p.setPreferredSize(sz);
            else if (s instanceof Container c) c.setPreferredSize(sz);
        } catch (Throwable ignore) {
        }

        // GuiControl path
        GuiControl gc = null;
        try {
            gc = s.getControl(GuiControl.class);
        } catch (Throwable ignore) {
        }

        if (gc == null) return;

        // preferred size on GuiControl
        try {
            gc.setPreferredSize(sz);
        } catch (Throwable ignore) {
        }

        // Real size: forks expose different signatures
        reflectCall(gc, "setSize", new Class<?>[]{Vector3f.class}, new Object[]{sz});
        reflectCall(gc, "setSize", new Class<?>[]{float.class, float.class}, new Object[]{ww, hh});

        // reshape variants (origin + size)
        reflectCall(gc, "reshape", new Class<?>[]{Vector3f.class, Vector3f.class}, new Object[]{new Vector3f(0, 0, 0), sz});
        reflectCall(gc, "reshape", new Class<?>[]{float.class, float.class, float.class, float.class}, new Object[]{0f, 0f, ww, hh});

        // Some forks expose getSize()/setSize via different names
        reflectCall(gc, "setBounds", new Class<?>[]{float.class, float.class, float.class, float.class}, new Object[]{0f, 0f, ww, hh});

        tryInvalidateAndLayout(gc);
    }
}