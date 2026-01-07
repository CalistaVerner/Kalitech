// FILE: org/foxesworld/kalitech/engine/modules/hud/HudSizing.java
package org.foxesworld.kalitech.engine.modules.hud;

import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.core.GuiControl;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class HudSizing {

    private HudSizing() {
    }

    private static final ThreadLocal<Vector3f> TMP_SIZE = ThreadLocal.withInitial(Vector3f::new);
    private static final ThreadLocal<Vector3f> TMP_ORIG = ThreadLocal.withInitial(Vector3f::new);

    private static final ConcurrentHashMap<Class<?>, Hooks> HOOKS = new ConcurrentHashMap<>();

    public static boolean isBoxLike(Spatial s) {
        return (s instanceof Panel) || (s instanceof Container);
    }

    public static float preferredW(Spatial s) {
        if (s == null) return 0f;
        final GuiControl gc = s.getControl(GuiControl.class);
        if (gc == null) return 0f;
        final Vector3f ps = gc.getPreferredSize();
        if (ps == null) return 0f;
        final float w = ps.x;
        return (Float.isFinite(w) && w > 0f) ? w : 0f;
    }

    public static float preferredH(Spatial s) {
        if (s == null) return 0f;
        final GuiControl gc = s.getControl(GuiControl.class);
        if (gc == null) return 0f;
        final Vector3f ps = gc.getPreferredSize();
        if (ps == null) return 0f;
        final float h = ps.y;
        return (Float.isFinite(h) && h > 0f) ? h : 0f;
    }

    public static float widthOf(int id, Spatial s, HudSizeCache cache) {
        if (cache != null) {
            final float cw = cache.getW(id);
            if (cw > 0f) return cw;
        }
        return preferredW(s);
    }

    public static float heightOf(int id, Spatial s, HudSizeCache cache) {
        if (cache != null) {
            final float ch = cache.getH(id);
            if (ch > 0f) return ch;
        }
        return preferredH(s);
    }

    public static void forceSize(int id, Spatial s, float w, float h, HudSizeCache cache) {
        if (s == null) return;

        final float ww = sane(w);
        final float hh = sane(h);

        if (cache != null) cache.put(id, ww, hh);

        final Vector3f sz = TMP_SIZE.get();
        sz.set(ww, hh, 0f);

        if (s instanceof Panel p) {
            p.setPreferredSize(sz);
        } else if (s instanceof Container c) {
            c.setPreferredSize(sz);
        }

        final GuiControl gc = s.getControl(GuiControl.class);
        if (gc == null) return;

        gc.setPreferredSize(sz);

        final Hooks hooks = hooksFor(gc.getClass());
        hooks.apply(gc, sz);
    }

    private static float sane(float v) {
        if (!Float.isFinite(v) || v <= 0f) return 0f;
        return v;
    }

    private static Hooks hooksFor(Class<?> gcClass) {
        return HOOKS.computeIfAbsent(gcClass, Hooks::resolve);
    }

    private static final class Hooks {
        private final Method setSizeVec3;
        private final Method setSizeWH;
        private final Method reshapeVV;
        private final Method reshapeFFFF;
        private final Method setBoundsFFFF;

        private final Method invalidate;
        private final Method layout;
        private final Method refresh;
        private final Method updateLayout;
        private final Method rebuild;

        private Hooks(
                Method setSizeVec3,
                Method setSizeWH,
                Method reshapeVV,
                Method reshapeFFFF,
                Method setBoundsFFFF,
                Method invalidate,
                Method layout,
                Method refresh,
                Method updateLayout,
                Method rebuild
        ) {
            this.setSizeVec3 = setSizeVec3;
            this.setSizeWH = setSizeWH;
            this.reshapeVV = reshapeVV;
            this.reshapeFFFF = reshapeFFFF;
            this.setBoundsFFFF = setBoundsFFFF;
            this.invalidate = invalidate;
            this.layout = layout;
            this.refresh = refresh;
            this.updateLayout = updateLayout;
            this.rebuild = rebuild;
        }

        static Hooks resolve(Class<?> cls) {
            final Method setSizeVec3 = methodOrNull(cls, "setSize", Vector3f.class);
            final Method setSizeWH = methodOrNull(cls, "setSize", float.class, float.class);

            final Method reshapeVV = methodOrNull(cls, "reshape", Vector3f.class, Vector3f.class);
            final Method reshapeFFFF = methodOrNull(cls, "reshape", float.class, float.class, float.class, float.class);
            final Method setBoundsFFFF = methodOrNull(cls, "setBounds", float.class, float.class, float.class, float.class);

            final Method invalidate = methodOrNull(cls, "invalidate");
            final Method layout = methodOrNull(cls, "layout");
            final Method refresh = methodOrNull(cls, "refresh");
            final Method updateLayout = methodOrNull(cls, "updateLayout");
            final Method rebuild = methodOrNull(cls, "rebuild");

            return new Hooks(
                    setSizeVec3, setSizeWH,
                    reshapeVV, reshapeFFFF, setBoundsFFFF,
                    invalidate, layout, refresh, updateLayout, rebuild
            );
        }

        private static Method methodOrNull(Class<?> cls, String name, Class<?>... sig) {
            try {
                final Method m = cls.getMethod(name, sig);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static void invoke(Object target, Method m, Object... args) {
            try {
                m.invoke(target, args);
            } catch (Throwable ignored) {
            }
        }

        void apply(Object gc, Vector3f size) {
            final float w = size.x;
            final float h = size.y;

            if (setSizeVec3 != null) invoke(gc, setSizeVec3, size);
            if (setSizeWH != null) invoke(gc, setSizeWH, w, h);

            if (reshapeVV != null) {
                final Vector3f orig = TMP_ORIG.get();
                orig.set(0f, 0f, 0f);
                invoke(gc, reshapeVV, orig, size);
            }
            if (reshapeFFFF != null) invoke(gc, reshapeFFFF, 0f, 0f, w, h);
            if (setBoundsFFFF != null) invoke(gc, setBoundsFFFF, 0f, 0f, w, h);

            if (invalidate != null) invoke(gc, invalidate);
            if (layout != null) invoke(gc, layout);
            if (refresh != null) invoke(gc, refresh);
            if (updateLayout != null) invoke(gc, updateLayout);
            if (rebuild != null) invoke(gc, rebuild);
        }
    }
}