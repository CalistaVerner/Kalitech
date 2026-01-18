package org.foxesworld.kalitech.engine.api.impl;


import com.jme3.app.SimpleApplication;
import com.jme3.light.*;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.LightApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

/**
 * Light API.
 *
 * <p>Threading:
 * <ul>
 *   <li>Config parsing happens on the caller thread.</li>
 *   <li>All scenegraph light mutations (create/attach/detach/set/destroy) happen on the JME thread.</li>
 * </ul>
 */
public final class LightApiImpl extends AbstractApiModule implements LightApi {

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Light> lights = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, LightState> states = new ConcurrentHashMap<>();
    private SimpleApplication app;

    public LightApiImpl() {
        super("light", "Light", "1.0.0");
    }

    private static void applyConfigOnJme(int id, Light l, LightState st, LightConfig c) {
        if (c.enabled != null) {
            l.setEnabled(c.enabled);
            st.enabled = c.enabled;
        }

        ColorRGBA base = new ColorRGBA(c.colorR, c.colorG, c.colorB, c.colorA);
        float intensity = c.intensity != null ? c.intensity : st.intensity;

        st.colorR = base.r;
        st.colorG = base.g;
        st.colorB = base.b;
        st.colorA = base.a;
        st.intensity = intensity;

        l.setColor(base.mult(intensity));

        if (l instanceof DirectionalLight dl) {
            Vector3f dir = (c.dir != null) ? c.dir : new Vector3f(-1f, -1f, -0.3f);
            if (dir.lengthSquared() < 1e-8f) dir.set(-1f, -1f, -0.3f);
            dir.normalizeLocal();
            dl.setDirection(dir);

            st.type = "directional";
            st.dir = dir.clone();
            st.pos = null;
            st.radius = st.range = st.innerDeg = st.outerDeg = null;
            return;
        }

        if (l instanceof AmbientLight) {
            st.type = "ambient";
            st.dir = null;
            st.pos = null;
            st.radius = st.range = st.innerDeg = st.outerDeg = null;
            return;
        }

        if (l instanceof PointLight pl) {
            Vector3f pos = (c.pos != null) ? c.pos : new Vector3f(0f, 3f, 0f);
            pl.setPosition(pos);

            st.type = "point";
            st.pos = pos.clone();
            st.dir = null;

            if (c.radius != null && c.radius > 0f) {
                pl.setRadius(c.radius);
                st.radius = c.radius;
            }

            st.range = st.innerDeg = st.outerDeg = null;
            return;
        }

        if (l instanceof SpotLight sl) {
            Vector3f pos = (c.pos != null) ? c.pos : new Vector3f(0f, 3f, 0f);
            Vector3f dir = (c.dir != null) ? c.dir : new Vector3f(0f, -1f, 0f);
            if (dir.lengthSquared() < 1e-8f) dir.set(0f, -1f, 0f);

            sl.setPosition(pos);
            sl.setDirection(dir.normalizeLocal());

            st.type = "spot";
            st.pos = pos.clone();
            st.dir = dir.clone();

            float range = (c.range != null) ? c.range : (st.range != null ? st.range : 100f);
            sl.setSpotRange(range);
            st.range = range;

            float innerDeg = (c.innerDeg != null) ? c.innerDeg : (st.innerDeg != null ? st.innerDeg : 15f);
            float outerDeg = (c.outerDeg != null) ? c.outerDeg : (st.outerDeg != null ? st.outerDeg : 25f);

            innerDeg = clamp(innerDeg, 0.0f, 89.0f);
            outerDeg = clamp(outerDeg, innerDeg, 90.0f);

            sl.setSpotInnerAngle((float) Math.toRadians(innerDeg));
            sl.setSpotOuterAngle((float) Math.toRadians(outerDeg));

            st.innerDeg = innerDeg;
            st.outerDeg = outerDeg;
            st.radius = null;
        }
    }

    private static String normalizeType(String type) {
        String t = type.trim().toLowerCase();
        if (t.equals("dir") || t.equals("sun")) return "directional";
        return t;
    }

    private static ProxyObject stateToProxy(LightState st) {
        Map<String, Object> o = new LinkedHashMap<>();

        o.put("id", st.id);
        o.put("type", st.type);
        o.put("enabled", st.enabled);
        o.put("attached", st.attached);

        o.put("intensity", st.intensity);
        o.put("color", ProxyArray.fromArray(st.colorR, st.colorG, st.colorB, st.colorA));

        if (st.dir != null) o.put("dir", ProxyArray.fromArray(st.dir.x, st.dir.y, st.dir.z));
        if (st.pos != null) o.put("pos", ProxyArray.fromArray(st.pos.x, st.pos.y, st.pos.z));

        if (st.radius != null) o.put("radius", st.radius);
        if (st.range != null) o.put("range", st.range);
        if (st.innerDeg != null) o.put("innerDeg", st.innerDeg);
        if (st.outerDeg != null) o.put("outerDeg", st.outerDeg);

        return ProxyObject.fromMap(o);
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

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.app = Objects.requireNonNull(ctx.app, "ctx.app");
    }

    @Override
    public void detach() {
        SimpleApplication a = app;

        if (a != null) {
            onJmeVoid("light.detach", () -> {
                for (Light l : lights.values()) {
                    if (l != null) detachFromRoot(l);
                }
            });
        }

        lights.clear();
        states.clear();
        ids.set(1);

        this.app = null;
        super.detach();
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public LightHandle create(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("light.create(cfg): cfg is null");

        LightConfig c = LightConfig.from(cfg);
        int id = ids.getAndIncrement();

        return profiled(() -> onJmeSyncStrict("light.create", () -> {
            Light l = createLightByType(c.type);
            lights.put(id, l);

            LightState st = LightState.defaults(id, c.type);
            states.put(id, st);

            if (c.attach) {
                attachToRoot(l);
                st.attached = true;
            } else {
                st.attached = false;
            }

            applyConfigOnJme(id, l, st, c);
            return new LightHandle(id, c.type);
        }));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public boolean exists(LightHandle handle) {
        return handle != null && lights.containsKey(handle.id());
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void set(LightHandle handle, Value cfg) {
        if (handle == null) throw new IllegalArgumentException("light.set(handle,cfg): handle is null");
        if (cfg == null || cfg.isNull()) return;

        LightConfig c = LightConfig.from(cfg);

        profiledVoid(() -> onJmeVoid("light.set", () -> {
            Light l = require(handle);
            LightState st = requireState(handle.id());

            if (c.detach) {
                detachFromRoot(l);
                st.attached = false;
            }
            if (c.attach) {
                attachToRoot(l);
                st.attached = true;
            }

            applyConfigOnJme(handle.id(), l, st, c);
        }));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void enable(LightHandle handle, boolean enabled) {
        if (handle == null) return;

        profiledVoid(() -> onJmeVoid("light.enable", () -> {
            Light l = lights.get(handle.id());
            if (l == null) return;

            l.setEnabled(enabled);
            LightState st = states.get(handle.id());
            if (st != null) st.enabled = enabled;
        }));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void destroy(LightHandle handle) {
        if (handle == null) return;

        profiledVoid(() -> onJmeVoid("light.destroy", () -> {
            Light l = lights.remove(handle.id());
            if (l != null) detachFromRoot(l);
            states.remove(handle.id());
        }));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Value get(LightHandle handle) {
        if (handle == null) return null;

        LightState st = states.get(handle.id());
        if (st == null) return null;

        Context ctx = (engine == null || engine.getRuntime() == null) ? null : engine.getRuntime().getCtx();
        if (ctx == null) return null;

        return ctx.asValue(stateToProxy(st));
    }

    private Light require(LightHandle h) {
        if (h == null) throw new IllegalArgumentException("light: handle is null");
        Light l = lights.get(h.id());
        if (l == null) throw new IllegalStateException("light: unknown handle id=" + h.id());
        return l;
    }

    private LightState requireState(int id) {
        LightState st = states.get(id);
        if (st == null) {
            st = LightState.defaults(id, "unknown");
            states.put(id, st);
        }
        return st;
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public Value list() {
        Context ctx = (engine == null || engine.getRuntime() == null) ? null : engine.getRuntime().getCtx();
        if (ctx == null) return null;

        var idsSorted = states.keySet().stream().sorted().toList();
        Object[] arr = new Object[idsSorted.size()];

        for (int i = 0; i < idsSorted.size(); i++) {
            LightState st = states.get(idsSorted.get(i));
            if (st == null) continue;

            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", st.id);
            o.put("type", st.type);
            arr[i] = ProxyObject.fromMap(o);
        }

        return ctx.asValue(ProxyArray.fromArray(arr));
    }

    private void attachToRoot(Light l) {
        SimpleApplication a = app;
        if (a == null || a.getRootNode() == null) return;
        a.getRootNode().addLight(l);
    }

    private void detachFromRoot(Light l) {
        SimpleApplication a = app;
        if (a == null || a.getRootNode() == null) return;
        a.getRootNode().removeLight(l);
    }

    public static final class LightHandle {
        private final int id;
        private final String type;

        public LightHandle(int id, String type) {
            this.id = id;
            this.type = type;
        }

        @HostAccess.Export
        @ApiMethod(
                thread = ApiThreadRule.ANY,
                sync = false,
                flags = {ApiFlag.SANDBOX_ALLOWED},
                cost = ApiCostHint.NORMAL
        )
        public int id() {
            return id;
        }

        @HostAccess.Export
        @ApiMethod(
                thread = ApiThreadRule.ANY,
                sync = false,
                flags = {ApiFlag.SANDBOX_ALLOWED},
                cost = ApiCostHint.NORMAL
        )
        public String type() {
            return type;
        }
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

        private LightConfig(
                String type,
                Boolean enabled,
                boolean attach,
                boolean detach,
                float colorR, float colorG, float colorB, float colorA,
                Float intensity,
                Vector3f dir,
                Vector3f pos,
                Float radius,
                Float range,
                Float innerDeg,
                Float outerDeg
        ) {
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

        static LightConfig from(Value cfg) {
            Objects.requireNonNull(cfg, "cfg");

            String type = str(cfg, "type", null);
            if (type == null || type.isBlank()) throw new IllegalArgumentException("light: type is required");
            String normType = normalizeType(type);

            Boolean enabled = has(cfg, "enabled") ? bool(cfg, "enabled", true) : null;

            boolean attach = bool(cfg, "attach", true);
            boolean detach = bool(cfg, "detach", false);

            ColorRGBA col = parseColor(member(cfg, "color"), 1f, 1f, 1f, 1f);

            Float intensity = has(cfg, "intensity") ? (float) num(cfg, "intensity", 1.0) : null;

            Vector3f dir = parseVec3Nullable(member(cfg, "dir"));
            Vector3f pos = parseVec3Nullable(member(cfg, "pos"));

            Float radius = has(cfg, "radius") ? (float) num(cfg, "radius", 0.0) : null;
            Float range = has(cfg, "range") ? (float) num(cfg, "range", 100.0) : null;
            Float innerDeg = has(cfg, "innerDeg") ? (float) num(cfg, "innerDeg", 15.0) : null;
            Float outerDeg = has(cfg, "outerDeg") ? (float) num(cfg, "outerDeg", 25.0) : null;

            return new LightConfig(
                    normType,
                    enabled,
                    attach,
                    detach,
                    col.r, col.g, col.b, col.a,
                    intensity,
                    dir,
                    pos,
                    radius,
                    range,
                    innerDeg,
                    outerDeg
            );
        }

        }


        private static ColorRGBA parseColor(Value v, float dr, float dg, float db, float da) {
            if (v == null || v.isNull()) return new ColorRGBA(dr, dg, db, da);

            try {
                if (v.hasArrayElements()) {
                    long n = v.getArraySize();
                    if (n >= 3) {
                        float r = (float) v.getArrayElement(0).asDouble();
                        float g = (float) v.getArrayElement(1).asDouble();
                        float b = (float) v.getArrayElement(2).asDouble();
                        float a = (n >= 4) ? (float) v.getArrayElement(3).asDouble() : da;
                        return new ColorRGBA(r, g, b, a);
                    }
                }
                if (v.hasMembers() && (v.hasMember("r") || v.hasMember("g") || v.hasMember("b"))) {
                    float r = (float) numMember(v, "r", dr);
                    float g = (float) numMember(v, "g", dg);
                    float b = (float) numMember(v, "b", db);
                    float a = (float) numMember(v, "a", da);
                    return new ColorRGBA(r, g, b, a);
                }
            } catch (Throwable t) {
                return new ColorRGBA(dr, dg, db, da);
            }

            return new ColorRGBA(dr, dg, db, da);
        }

        private static double numMember(Value v, String key, double def) {
            try {
                if (v == null || v.isNull() || !v.hasMember(key)) return def;
                Value m = v.getMember(key);
                if (m == null || m.isNull()) return def;
                return m.asDouble();
            } catch (Throwable t) {
                return def;
            }
        }

        private static Vector3f parseVec3Nullable(Value v) {
            if (v == null || v.isNull()) return null;

            try {
                if (v.hasArrayElements() && v.getArraySize() >= 3) {
                    return new Vector3f(
                            (float) v.getArrayElement(0).asDouble(),
                            (float) v.getArrayElement(1).asDouble(),
                            (float) v.getArrayElement(2).asDouble()
                    );
                }
                if (v.hasMembers()) {
                    float x = (float) numMember(v, "x", 0.0);
                    float y = (float) numMember(v, "y", 0.0);
                    float z = (float) numMember(v, "z", 0.0);
                    return new Vector3f(x, y, z);
                }
            } catch (Throwable t) {
                return null;
            }

            return null;
        }


    private static final class LightState {
        int id;
        String type;
        boolean enabled = true;
        boolean attached = true;

        float colorR = 1f, colorG = 1f, colorB = 1f, colorA = 1f;
        float intensity = 1f;

        Vector3f dir;
        Vector3f pos;

        Float radius;
        Float range;
        Float innerDeg;
        Float outerDeg;

        static LightState defaults(int id, String type) {
            LightState st = new LightState();
            st.id = id;
            st.type = type;
            return st;
        }
    }
}