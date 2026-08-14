/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.SimpleApplication
 *  com.jme3.light.AmbientLight
 *  com.jme3.light.DirectionalLight
 *  com.jme3.light.Light
 *  com.jme3.light.PointLight
 *  com.jme3.light.SpotLight
 *  com.jme3.math.ColorRGBA
 *  com.jme3.math.Vector3f
 *  org.foxesworld.kalitech.engine.api.contract.ApiCostHint
 *  org.foxesworld.kalitech.engine.api.contract.ApiFlag
 *  org.foxesworld.kalitech.engine.api.contract.ApiMethod
 *  org.foxesworld.kalitech.engine.api.contract.ApiThreadRule
 *  org.foxesworld.kalitech.engine.api.module.AbstractApiModule
 *  org.foxesworld.kalitech.engine.api.module.ApiContext
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *   *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 *  org.foxesworld.kalitech.engine.script.lua.LuaArray
 *  org.foxesworld.kalitech.engine.script.lua.LuaObject
 */
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.LightApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.foxesworld.kalitech.engine.script.lua.LuaArray;
import org.foxesworld.kalitech.engine.script.lua.LuaObject;

public final class LightApiImpl
extends AbstractApiModule
implements LightApi {
    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Light> lights = new ConcurrentHashMap();
    private final ConcurrentHashMap<Integer, LightState> states = new ConcurrentHashMap();
    private SimpleApplication app;

    public LightApiImpl() {
        super("light", "Light", "1.0.0");
    }

    private static void applyConfigOnJme(int id, Light l, LightState st, LightConfig c) {
        if (c.enabled != null) {
            l.setEnabled(c.enabled.booleanValue());
            st.enabled = c.enabled;
        }
        ColorRGBA base = new ColorRGBA(c.colorR, c.colorG, c.colorB, c.colorA);
        float intensity = c.intensity != null ? c.intensity.floatValue() : st.intensity;
        st.colorR = base.r;
        st.colorG = base.g;
        st.colorB = base.b;
        st.colorA = base.a;
        st.intensity = intensity;
        l.setColor(base.mult(intensity));
        if (l instanceof DirectionalLight) {
            Vector3f dir;
            DirectionalLight dl = (DirectionalLight)l;
            Vector3f vector3f = dir = c.dir != null ? c.dir : new Vector3f(-1.0f, -1.0f, -0.3f);
            if (dir.lengthSquared() < 1.0E-8f) {
                dir.set(-1.0f, -1.0f, -0.3f);
            }
            dir.normalizeLocal();
            dl.setDirection(dir);
            st.type = "directional";
            st.dir = dir.clone();
            st.pos = null;
            st.outerDeg = null;
            st.innerDeg = null;
            st.range = null;
            st.radius = null;
            return;
        }
        if (l instanceof AmbientLight) {
            st.type = "ambient";
            st.dir = null;
            st.pos = null;
            st.outerDeg = null;
            st.innerDeg = null;
            st.range = null;
            st.radius = null;
            return;
        }
        if (l instanceof PointLight) {
            PointLight pl = (PointLight)l;
            Vector3f pos = c.pos != null ? c.pos : new Vector3f(0.0f, 3.0f, 0.0f);
            pl.setPosition(pos);
            st.type = "point";
            st.pos = pos.clone();
            st.dir = null;
            if (c.radius != null && c.radius.floatValue() > 0.0f) {
                pl.setRadius(c.radius.floatValue());
                st.radius = c.radius;
            }
            st.outerDeg = null;
            st.innerDeg = null;
            st.range = null;
            return;
        }
        if (l instanceof SpotLight) {
            float innerDeg;
            Vector3f dir;
            SpotLight sl = (SpotLight)l;
            Vector3f pos = c.pos != null ? c.pos : new Vector3f(0.0f, 3.0f, 0.0f);
            Vector3f vector3f = dir = c.dir != null ? c.dir : new Vector3f(0.0f, -1.0f, 0.0f);
            if (dir.lengthSquared() < 1.0E-8f) {
                dir.set(0.0f, -1.0f, 0.0f);
            }
            sl.setPosition(pos);
            sl.setDirection(dir.normalizeLocal());
            st.type = "spot";
            st.pos = pos.clone();
            st.dir = dir.clone();
            float range = c.range != null ? c.range.floatValue() : (st.range != null ? st.range.floatValue() : 100.0f);
            sl.setSpotRange(range);
            st.range = Float.valueOf(range);
            innerDeg = c.innerDeg != null ? c.innerDeg.floatValue() : (st.innerDeg != null ? st.innerDeg.floatValue() : 15.0f);
            float outerDeg = c.outerDeg != null ? c.outerDeg.floatValue() : (st.outerDeg != null ? st.outerDeg.floatValue() : 25.0f);
            innerDeg = LightApiImpl.clamp(innerDeg, 0.0f, 89.0f);
            outerDeg = LightApiImpl.clamp(outerDeg, innerDeg, 90.0f);
            sl.setSpotInnerAngle((float)Math.toRadians(innerDeg));
            sl.setSpotOuterAngle((float)Math.toRadians(outerDeg));
            st.innerDeg = Float.valueOf(innerDeg);
            st.outerDeg = Float.valueOf(outerDeg);
            st.radius = null;
        }
    }

    private static String normalizeType(String type) {
        String t = type.trim().toLowerCase();
        if (t.equals("dir") || t.equals("sun")) {
            return "directional";
        }
        return t;
    }

    private static LuaObject stateToProxy(LightState st) {
        LinkedHashMap<String, Object> o = new LinkedHashMap<String, Object>();
        o.put("id", st.id);
        o.put("type", st.type);
        o.put("enabled", st.enabled);
        o.put("attached", st.attached);
        o.put("intensity", Float.valueOf(st.intensity));
        o.put("color", LuaArray.fromArray((Object[])new Object[]{Float.valueOf(st.colorR), Float.valueOf(st.colorG), Float.valueOf(st.colorB), Float.valueOf(st.colorA)}));
        if (st.dir != null) {
            o.put("dir", LuaArray.fromArray((Object[])new Object[]{Float.valueOf(st.dir.x), Float.valueOf(st.dir.y), Float.valueOf(st.dir.z)}));
        }
        if (st.pos != null) {
            o.put("pos", LuaArray.fromArray((Object[])new Object[]{Float.valueOf(st.pos.x), Float.valueOf(st.pos.y), Float.valueOf(st.pos.z)}));
        }
        if (st.radius != null) {
            o.put("radius", st.radius);
        }
        if (st.range != null) {
            o.put("range", st.range);
        }
        if (st.innerDeg != null) {
            o.put("innerDeg", st.innerDeg);
        }
        if (st.outerDeg != null) {
            o.put("outerDeg", st.outerDeg);
        }
        return LuaObject.fromMap(o);
    }

    private static Light createLightByType(String type) {
        return switch (type) {
            case "ambient" -> new AmbientLight();
            case "directional" -> new DirectionalLight();
            case "point" -> new PointLight();
            case "spot" -> new SpotLight();
            default -> throw new IllegalArgumentException("light.create: unsupported type=" + type);
        };
    }

    private static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.app = Objects.requireNonNull(ctx.app, "ctx.app");
    }

    public void detach() {
        SimpleApplication a = this.app;
        if (a != null) {
            this.onJmeVoid("light.detach", () -> {
                for (Light l : this.lights.values()) {
                    if (l == null) continue;
                    this.detachFromRoot(l);
                }
            });
        }
        this.lights.clear();
        this.states.clear();
        this.ids.set(1);
        this.app = null;
        super.detach();
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public LightHandle create(LuaValueRef cfg) {
        if (cfg == null || cfg.isNull()) {
            throw new IllegalArgumentException("light.create(cfg): cfg is null");
        }
        LightConfig c = LightConfig.from(cfg);
        int id = this.ids.getAndIncrement();
        return (LightHandle)this.profiled(() -> (LightHandle)this.onJmeSyncStrict("light.create", () -> {
            Light l = LightApiImpl.createLightByType(c.type);
            this.lights.put(id, l);
            LightState st = LightState.defaults(id, c.type);
            this.states.put(id, st);
            if (c.attach) {
                this.attachToRoot(l);
                st.attached = true;
            } else {
                st.attached = false;
            }
            LightApiImpl.applyConfigOnJme(id, l, st, c);
            return new LightHandle(id, c.type);
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public boolean exists(LightHandle handle) {
        return handle != null && this.lights.containsKey(handle.id());
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void set(LightHandle handle, LuaValueRef cfg) {
        if (handle == null) {
            throw new IllegalArgumentException("light.set(handle,cfg): handle is null");
        }
        if (cfg == null || cfg.isNull()) {
            return;
        }
        LightConfig c = LightConfig.from(cfg);
        this.profiledVoid(() -> this.onJmeVoid("light.set", () -> {
            Light l = this.require(handle);
            LightState st = this.requireState(handle.id());
            if (c.detach) {
                this.detachFromRoot(l);
                st.attached = false;
            }
            if (c.attach) {
                this.attachToRoot(l);
                st.attached = true;
            }
            LightApiImpl.applyConfigOnJme(handle.id(), l, st, c);
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void enable(LightHandle handle, boolean enabled) {
        if (handle == null) {
            return;
        }
        this.profiledVoid(() -> this.onJmeVoid("light.enable", () -> {
            Light l = this.lights.get(handle.id());
            if (l == null) {
                return;
            }
            l.setEnabled(enabled);
            LightState st = this.states.get(handle.id());
            if (st != null) {
                st.enabled = enabled;
            }
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void destroy(LightHandle handle) {
        if (handle == null) {
            return;
        }
        this.profiledVoid(() -> this.onJmeVoid("light.destroy", () -> {
            Light l = this.lights.remove(handle.id());
            if (l != null) {
                this.detachFromRoot(l);
            }
            this.states.remove(handle.id());
        }));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public LuaValueRef get(LightHandle handle) {
        if (handle == null) {
            return null;
        }
        LightState st = this.states.get(handle.id());
        if (st == null) {
            return null;
        }
        return LuaValueRef.fromJava(LightApiImpl.stateToProxy(st));
    }

    private Light require(LightHandle h) {
        if (h == null) {
            throw new IllegalArgumentException("light: handle is null");
        }
        Light l = this.lights.get(h.id());
        if (l == null) {
            throw new IllegalStateException("light: unknown handle id=" + h.id());
        }
        return l;
    }

    private LightState requireState(int id) {
        LightState st = this.states.get(id);
        if (st == null) {
            st = LightState.defaults(id, "unknown");
            this.states.put(id, st);
        }
        return st;
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public LuaValueRef list() {
        List idsSorted = this.states.keySet().stream().sorted().toList();
        Object[] arr = new Object[idsSorted.size()];
        for (int i = 0; i < idsSorted.size(); ++i) {
            LightState st = this.states.get(idsSorted.get(i));
            if (st == null) continue;
            LinkedHashMap<String, Object> o = new LinkedHashMap<String, Object>();
            o.put("id", st.id);
            o.put("type", st.type);
            arr[i] = LuaObject.fromMap(o);
        }
        return LuaValueRef.fromJava(LuaArray.fromArray((Object[])arr));
    }

    private void attachToRoot(Light l) {
        SimpleApplication a = this.app;
        if (a == null || a.getRootNode() == null) {
            return;
        }
        a.getRootNode().addLight(l);
    }

    private void detachFromRoot(Light l) {
        SimpleApplication a = this.app;
        if (a == null || a.getRootNode() == null) {
            return;
        }
        a.getRootNode().removeLight(l);
    }

    private static ColorRGBA parseColor(LuaValueRef v, float dr, float dg, float db, float da) {
        if (v == null || v.isNull()) return new ColorRGBA(dr, dg, db, da);
        try {
            if (v.hasArrayElements() && v.getArraySize() >= 3L) {
                float r = (float) v.getArrayElement(0L).asDouble();
                float g = (float) v.getArrayElement(1L).asDouble();
                float b = (float) v.getArrayElement(2L).asDouble();
                float a = v.getArraySize() >= 4L
                        ? (float) v.getArrayElement(3L).asDouble()
                        : da;
                return new ColorRGBA(r, g, b, a);
            }
            if (v.hasMembers() && (v.hasMember("r") || v.hasMember("g") || v.hasMember("b"))) {
                float r = (float) LightApiImpl.numMember(v, "r", dr);
                float g = (float) LightApiImpl.numMember(v, "g", dg);
                float b = (float) LightApiImpl.numMember(v, "b", db);
                float a = (float) LightApiImpl.numMember(v, "a", da);
                return new ColorRGBA(r, g, b, a);
            }
        } catch (Throwable ignored) {
        }
        return new ColorRGBA(dr, dg, db, da);
    }

    private static double numMember(LuaValueRef v, String key, double def) {
        try {
            if (v == null || v.isNull() || !v.hasMember(key)) {
                return def;
            }
            LuaValueRef m = v.getMember(key);
            if (m == null || m.isNull()) {
                return def;
            }
            return m.asDouble();
        }
        catch (Throwable t) {
            return def;
        }
    }

    private static Vector3f parseVec3Nullable(LuaValueRef v) {
        if (v == null || v.isNull()) return null;
        try {
            if (v.hasArrayElements() && v.getArraySize() >= 3L) {
                return new Vector3f(
                        (float) v.getArrayElement(0L).asDouble(),
                        (float) v.getArrayElement(1L).asDouble(),
                        (float) v.getArrayElement(2L).asDouble()
                );
            }
            if (v.hasMembers()) {
                float x = (float) LightApiImpl.numMember(v, "x", 0.0);
                float y = (float) LightApiImpl.numMember(v, "y", 0.0);
                float z = (float) LightApiImpl.numMember(v, "z", 0.0);
                return new Vector3f(x, y, z);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static final class LightConfig {
        final String type;
        final Boolean enabled;
        final boolean attach;
        final boolean detach;
        final float colorR;
        final float colorG;
        final float colorB;
        final float colorA;
        final Float intensity;
        final Vector3f dir;
        final Vector3f pos;
        final Float radius;
        final Float range;
        final Float innerDeg;
        final Float outerDeg;

        private LightConfig(String type, Boolean enabled, boolean attach, boolean detach, float colorR, float colorG, float colorB, float colorA, Float intensity, Vector3f dir, Vector3f pos, Float radius, Float range, Float innerDeg, Float outerDeg) {
            this.type = type;
            this.enabled = enabled;
            this.attach = attach;
            this.detach = detach;
            this.colorR = colorR;
            this.colorG = colorG;
            this.colorB = colorB;
            this.colorA = colorA;
            this.intensity = intensity;
            this.dir = dir;
            this.pos = pos;
            this.radius = radius;
            this.range = range;
            this.innerDeg = innerDeg;
            this.outerDeg = outerDeg;
        }

        static LightConfig from(LuaValueRef cfg) {
            Objects.requireNonNull(cfg, "cfg");
            String type = LuaCfg.str((LuaValueRef)cfg, (String)"type", null);
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("light: type is required");
            }
            String normType = LightApiImpl.normalizeType(type);
            Boolean enabled = LuaCfg.has((LuaValueRef)cfg, (String)"enabled") ? Boolean.valueOf(LuaCfg.bool((LuaValueRef)cfg, (String)"enabled", (boolean)true)) : null;
            boolean attach = LuaCfg.bool((LuaValueRef)cfg, (String)"attach", (boolean)true);
            boolean detach = LuaCfg.bool((LuaValueRef)cfg, (String)"detach", (boolean)false);
            ColorRGBA col = LightApiImpl.parseColor(LuaCfg.member((LuaValueRef)cfg, (String)"color"), 1.0f, 1.0f, 1.0f, 1.0f);
            Float intensity = LuaCfg.has((LuaValueRef)cfg, (String)"intensity") ? Float.valueOf((float)LuaCfg.num((LuaValueRef)cfg, (String)"intensity", (double)1.0)) : null;
            Vector3f dir = LightApiImpl.parseVec3Nullable(LuaCfg.member((LuaValueRef)cfg, (String)"dir"));
            Vector3f pos = LightApiImpl.parseVec3Nullable(LuaCfg.member((LuaValueRef)cfg, (String)"pos"));
            Float radius = LuaCfg.has((LuaValueRef)cfg, (String)"radius") ? Float.valueOf((float)LuaCfg.num((LuaValueRef)cfg, (String)"radius", (double)0.0)) : null;
            Float range = LuaCfg.has((LuaValueRef)cfg, (String)"range") ? Float.valueOf((float)LuaCfg.num((LuaValueRef)cfg, (String)"range", (double)100.0)) : null;
            Float innerDeg = LuaCfg.has((LuaValueRef)cfg, (String)"innerDeg") ? Float.valueOf((float)LuaCfg.num((LuaValueRef)cfg, (String)"innerDeg", (double)15.0)) : null;
            Float outerDeg = LuaCfg.has((LuaValueRef)cfg, (String)"outerDeg") ? Float.valueOf((float)LuaCfg.num((LuaValueRef)cfg, (String)"outerDeg", (double)25.0)) : null;
            return new LightConfig(normType, enabled, attach, detach, col.r, col.g, col.b, col.a, intensity, dir, pos, radius, range, innerDeg, outerDeg);
        }
    }

    private static final class LightState {
        int id;
        String type;
        boolean enabled = true;
        boolean attached = true;
        float colorR = 1.0f;
        float colorG = 1.0f;
        float colorB = 1.0f;
        float colorA = 1.0f;
        float intensity = 1.0f;
        Vector3f dir;
        Vector3f pos;
        Float radius;
        Float range;
        Float innerDeg;
        Float outerDeg;

        private LightState() {
        }

        static LightState defaults(int id, String type) {
            LightState st = new LightState();
            st.id = id;
            st.type = type;
            return st;
        }
    }

    public static final class LightHandle {
        private final int id;
        private final String type;

        public LightHandle(int id, String type) {
            this.id = id;
            this.type = type;
        }

        @LuaExport
        @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
        public int id() {
            return this.id;
        }

        @LuaExport
        @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
        public String type() {
            return this.type;
        }
    }
}

