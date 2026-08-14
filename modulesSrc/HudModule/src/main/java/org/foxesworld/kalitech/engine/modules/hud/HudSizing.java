/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector3f
 *  com.jme3.scene.Spatial
 *  com.simsilica.lemur.Container
 *  com.simsilica.lemur.Panel
 *  com.simsilica.lemur.core.GuiControl
 */
package org.foxesworld.kalitech.engine.modules.hud;

import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.core.GuiControl;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import org.foxesworld.kalitech.engine.modules.hud.HudSizeCache;

public final class HudSizing {
    private static final ThreadLocal<Vector3f> TMP_SIZE = ThreadLocal.withInitial(Vector3f::new);
    private static final ThreadLocal<Vector3f> TMP_ORIG = ThreadLocal.withInitial(Vector3f::new);
    private static final ConcurrentHashMap<Class<?>, Hooks> HOOKS = new ConcurrentHashMap();

    private HudSizing() {
    }

    public static boolean isBoxLike(Spatial s) {
        return s instanceof Panel || s instanceof Container;
    }

    public static float preferredW(Spatial s) {
        if (s == null) {
            return 0.0f;
        }
        GuiControl gc = (GuiControl)s.getControl(GuiControl.class);
        if (gc == null) {
            return 0.0f;
        }
        Vector3f ps = gc.getPreferredSize();
        if (ps == null) {
            return 0.0f;
        }
        float w = ps.x;
        return Float.isFinite(w) && w > 0.0f ? w : 0.0f;
    }

    public static float preferredH(Spatial s) {
        if (s == null) {
            return 0.0f;
        }
        GuiControl gc = (GuiControl)s.getControl(GuiControl.class);
        if (gc == null) {
            return 0.0f;
        }
        Vector3f ps = gc.getPreferredSize();
        if (ps == null) {
            return 0.0f;
        }
        float h = ps.y;
        return Float.isFinite(h) && h > 0.0f ? h : 0.0f;
    }

    public static float widthOf(int id, Spatial s, HudSizeCache cache) {
        float cw;
        if (cache != null && (cw = cache.getW(id)) > 0.0f) {
            return cw;
        }
        return HudSizing.preferredW(s);
    }

    public static float heightOf(int id, Spatial s, HudSizeCache cache) {
        float ch;
        if (cache != null && (ch = cache.getH(id)) > 0.0f) {
            return ch;
        }
        return HudSizing.preferredH(s);
    }

    public static void forceSize(int id, Spatial s, float w, float h, HudSizeCache cache) {
        if (s == null) {
            return;
        }
        float ww = HudSizing.sane(w);
        float hh = HudSizing.sane(h);
        if (cache != null) {
            cache.put(id, ww, hh);
        }
        Vector3f sz = TMP_SIZE.get();
        sz.set(ww, hh, 0.0f);
        if (s instanceof Panel) {
            Panel p = (Panel)s;
            p.setPreferredSize(sz);
        } else if (s instanceof Container) {
            Container c = (Container)s;
            c.setPreferredSize(sz);
        }
        GuiControl gc = (GuiControl)s.getControl(GuiControl.class);
        if (gc == null) {
            return;
        }
        gc.setPreferredSize(sz);
        Hooks hooks = HudSizing.hooksFor(gc.getClass());
        hooks.apply(gc, sz);
    }

    private static float sane(float v) {
        if (!Float.isFinite(v) || v <= 0.0f) {
            return 0.0f;
        }
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

        private Hooks(Method setSizeVec3, Method setSizeWH, Method reshapeVV, Method reshapeFFFF, Method setBoundsFFFF, Method invalidate, Method layout, Method refresh, Method updateLayout, Method rebuild) {
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
            Method setSizeVec3 = Hooks.methodOrNull(cls, "setSize", Vector3f.class);
            Method setSizeWH = Hooks.methodOrNull(cls, "setSize", Float.TYPE, Float.TYPE);
            Method reshapeVV = Hooks.methodOrNull(cls, "reshape", Vector3f.class, Vector3f.class);
            Method reshapeFFFF = Hooks.methodOrNull(cls, "reshape", Float.TYPE, Float.TYPE, Float.TYPE, Float.TYPE);
            Method setBoundsFFFF = Hooks.methodOrNull(cls, "setBounds", Float.TYPE, Float.TYPE, Float.TYPE, Float.TYPE);
            Method invalidate = Hooks.methodOrNull(cls, "invalidate", new Class[0]);
            Method layout = Hooks.methodOrNull(cls, "layout", new Class[0]);
            Method refresh = Hooks.methodOrNull(cls, "refresh", new Class[0]);
            Method updateLayout = Hooks.methodOrNull(cls, "updateLayout", new Class[0]);
            Method rebuild = Hooks.methodOrNull(cls, "rebuild", new Class[0]);
            return new Hooks(setSizeVec3, setSizeWH, reshapeVV, reshapeFFFF, setBoundsFFFF, invalidate, layout, refresh, updateLayout, rebuild);
        }

        private static Method methodOrNull(Class<?> cls, String name, Class<?> ... sig) {
            try {
                Method m = cls.getMethod(name, sig);
                m.setAccessible(true);
                return m;
            }
            catch (Throwable ignored) {
                return null;
            }
        }

        private static void invoke(Object target, Method m, Object ... args) {
            try {
                m.invoke(target, args);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }

        void apply(Object gc, Vector3f size) {
            float w = size.x;
            float h = size.y;
            if (this.setSizeVec3 != null) {
                Hooks.invoke(gc, this.setSizeVec3, size);
            }
            if (this.setSizeWH != null) {
                Hooks.invoke(gc, this.setSizeWH, Float.valueOf(w), Float.valueOf(h));
            }
            if (this.reshapeVV != null) {
                Vector3f orig = TMP_ORIG.get();
                orig.set(0.0f, 0.0f, 0.0f);
                Hooks.invoke(gc, this.reshapeVV, orig, size);
            }
            if (this.reshapeFFFF != null) {
                Hooks.invoke(gc, this.reshapeFFFF, Float.valueOf(0.0f), Float.valueOf(0.0f), Float.valueOf(w), Float.valueOf(h));
            }
            if (this.setBoundsFFFF != null) {
                Hooks.invoke(gc, this.setBoundsFFFF, Float.valueOf(0.0f), Float.valueOf(0.0f), Float.valueOf(w), Float.valueOf(h));
            }
            if (this.invalidate != null) {
                Hooks.invoke(gc, this.invalidate, new Object[0]);
            }
            if (this.layout != null) {
                Hooks.invoke(gc, this.layout, new Object[0]);
            }
            if (this.refresh != null) {
                Hooks.invoke(gc, this.refresh, new Object[0]);
            }
            if (this.updateLayout != null) {
                Hooks.invoke(gc, this.updateLayout, new Object[0]);
            }
            if (this.rebuild != null) {
                Hooks.invoke(gc, this.rebuild, new Object[0]);
            }
        }
    }
}

