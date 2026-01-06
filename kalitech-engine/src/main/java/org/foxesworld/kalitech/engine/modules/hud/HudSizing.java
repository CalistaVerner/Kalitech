// FILE: org/foxesworld/kalitech/engine/modules/hud/HudSizing.java
package org.foxesworld.kalitech.engine.modules.hud;

import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.core.GuiControl;

/**
 * Fork-proof sizing utilities for Lemur elements.
 * <p>
 * Goals:
 * - set preferred size AND try to set real size
 * - call invalidation/layout hooks where available
 * - keep size cache updated for reliable height math
 */
public final class HudSizing {
    private HudSizing() {
    }

    public static boolean isBoxLike(Spatial s) {
        return (s instanceof Panel) || (s instanceof Container);
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

    private static void reflectCall(Object target, String method, Class<?>[] sig, Object[] args) {
        try {
            var m = target.getClass().getMethod(method, sig);
            m.invoke(target, args);
        } catch (Throwable ignore) {
        }
    }

    /**
     * Force size for Lemur spatials:
     * - setPreferredSize on Panel/Container
     * - setPreferredSize on GuiControl
     * - try setSize()/reshape()/invalidate()/layout() via reflection for forks
     */
    public static void forceSize(int id, Spatial s, float w, float h, HudSizeCache cache) {
        if (s == null) return;

        final float ww = (Float.isFinite(w) && w >= 0f) ? w : 0f;
        final float hh = (Float.isFinite(h) && h >= 0f) ? h : 0f;
        final Vector3f sz = new Vector3f(ww, hh, 0f);

        if (cache != null) cache.put(id, ww, hh);

        // preferred size on known types
        try {
            if (s instanceof Panel p) p.setPreferredSize(sz);
            else if (s instanceof Container c) c.setPreferredSize(sz);
        } catch (Throwable ignore) {
        }

        GuiControl gc = s.getControl(GuiControl.class);
        if (gc == null) return;

        try {
            gc.setPreferredSize(sz);
        } catch (Throwable ignore) {
        }

        // real size: different forks expose different signatures
        reflectCall(gc, "setSize", new Class<?>[]{Vector3f.class}, new Object[]{sz});
        reflectCall(gc, "setSize", new Class<?>[]{float.class, float.class}, new Object[]{ww, hh});

        // reshape variants
        reflectCall(gc, "reshape", new Class<?>[]{Vector3f.class, Vector3f.class}, new Object[]{new Vector3f(0, 0, 0), sz});
        reflectCall(gc, "reshape", new Class<?>[]{float.class, float.class, float.class, float.class}, new Object[]{0f, 0f, ww, hh});

        // invalidation/layout hooks
        reflectCall(gc, "invalidate", new Class<?>[]{}, new Object[]{});
        reflectCall(gc, "layout", new Class<?>[]{}, new Object[]{});
        reflectCall(gc, "refresh", new Class<?>[]{}, new Object[]{});
    }
}