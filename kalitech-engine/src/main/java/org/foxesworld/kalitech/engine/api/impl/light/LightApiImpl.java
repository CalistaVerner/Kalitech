package org.foxesworld.kalitech.engine.api.impl.light;

import com.jme3.app.SimpleApplication;
import com.jme3.light.*;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.LightApi;
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

import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.*;

public final class LightApiImpl implements LightApi {

    private final EngineApiImpl engine;
    private final SimpleApplication app;

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Light> lights = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, LightState> states = new ConcurrentHashMap<>();

    public LightApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.app = engine.getApp();
    }

    @HostAccess.Export
    @Override
    public LightHandle create(Value cfg) {
        //engine.render().ensureScene();
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("light.create(cfg): cfg is null");

        String type = str(cfg, "type", null);
        if (type == null || type.isBlank()) throw new IllegalArgumentException("light.create: type is required");

        String normType = normalizeType(type);
        Light l = createLightByType(normType);

        int id = ids.getAndIncrement();
        lights.put(id, l);

        LightState st = LightState.defaults(id, normType);
        states.put(id, st);

        boolean attach = bool(cfg, "attach", true);
        if (attach) {
            attachToRoot(l);
            st.attached = true;
        }

        setInternal(id, l, cfg);
        return new LightHandle(id, normType);
    }

    @HostAccess.Export
    @Override
    public void set(LightHandle handle, Value cfg) {
        Light l = require(handle);
        if (cfg == null || cfg.isNull()) return;

        LightState st = requireState(handle.id());

        boolean attach = bool(cfg, "attach", false);
        boolean detach = bool(cfg, "detach", false);
        if (detach) {
            detachFromRoot(l);
            st.attached = false;
        }
        if (attach) {
            attachToRoot(l);
            st.attached = true;
        }

        setInternal(handle.id(), l, cfg);
    }

    @HostAccess.Export
    @Override
    public void enable(LightHandle handle, boolean enabled) {
        Light l = require(handle);
        l.setEnabled(enabled);
        LightState st = states.get(handle.id());
        if (st != null) st.enabled = enabled;
    }

    @HostAccess.Export
    @Override
    public boolean exists(LightHandle handle) {
        return handle != null && lights.containsKey(handle.id());
    }

    @HostAccess.Export
    @Override
    public void destroy(LightHandle handle) {
        if (handle == null) return;

        Light l = lights.remove(handle.id());
        if (l != null) detachFromRoot(l);

        states.remove(handle.id());
    }

    // ---------------- NEW: get/list ----------------

    @HostAccess.Export
    @Override
    public Value get(LightHandle handle) {
        if (handle == null) return null;

        LightState st = states.get(handle.id());
        if (st == null) return null;

        Context ctx = engine.getRuntime().getCtx();
        if (ctx == null) return null;

        return ctx.asValue(stateToProxy(st));
    }

    @HostAccess.Export
    @Override
    public Value list() {
        Context ctx = engine.getRuntime().getCtx();
        if (ctx == null) return null;

        // array of {id,type}
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

    // ---------------- handles ----------------

    public static final class LightHandle {
        private final int id;
        private final String type;

        public LightHandle(int id, String type) {
            this.id = id;
            this.type = type;
        }

        @HostAccess.Export public int id() { return id; }
        @HostAccess.Export public String type() { return type; }
    }

    // ---------------- internal ----------------

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

    private void attachToRoot(Light l) {
        if (app == null || app.getRootNode() == null) return;
        app.getRootNode().addLight(l);
    }

    private void detachFromRoot(Light l) {
        if (app == null || app.getRootNode() == null) return;
        app.getRootNode().removeLight(l);
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

    private static String normalizeType(String type) {
        String t = type.trim().toLowerCase();
        if (t.equals("dir") || t.equals("sun")) return "directional";
        return t;
    }

    private void setInternal(int id, Light l, Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        LightState st = requireState(id);

        if (cfg.hasMember("enabled")) {
            try {
                boolean en = cfg.getMember("enabled").asBoolean();
                l.setEnabled(en);
                st.enabled = en;
            } catch (Throwable ignored) {}
        }

        ColorRGBA c = parseColor(cfg.getMember("color"), 1f, 1f, 1f, 1f);
        float intensity = (float) num(cfg, "intensity", st.intensity);

        st.colorR = c.r; st.colorG = c.g; st.colorB = c.b; st.colorA = c.a;
        st.intensity = intensity;

        l.setColor(c.mult(intensity));

        if (l instanceof DirectionalLight dl) {
            Vector3f dir = parseVec3(cfg.getMember("dir"), -1f, -1f, -0.3f);
            if (dir.lengthSquared() < 1e-8f) dir.set(-1, -1, -0.3f);
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
            Vector3f pos = parseVec3(cfg.getMember("pos"), 0f, 3f, 0f);
            pl.setPosition(pos);
            st.type = "point";
            st.pos = pos.clone();
            st.dir = null;

            float radius = (float) num(cfg, "radius", (st.radius != null ? st.radius : 0.0));
            if (radius > 0f) {
                pl.setRadius(radius);
                st.radius = radius;
            }
            st.range = st.innerDeg = st.outerDeg = null;
            return;
        }

        if (l instanceof SpotLight sl) {
            Vector3f pos = parseVec3(cfg.getMember("pos"), 0f, 3f, 0f);
            Vector3f dir = parseVec3(cfg.getMember("dir"), 0f, -1f, 0f);
            if (dir.lengthSquared() < 1e-8f) dir.set(0, -1, 0);

            sl.setPosition(pos);
            sl.setDirection(dir.normalizeLocal());

            st.type = "spot";
            st.pos = pos.clone();
            st.dir = dir.clone();

            float range = (float) num(cfg, "range", (st.range != null ? st.range : 100f));
            sl.setSpotRange(range);
            st.range = range;

            float innerDeg = (float) num(cfg, "innerDeg", (st.innerDeg != null ? st.innerDeg : 15f));
            float outerDeg = (float) num(cfg, "outerDeg", (st.outerDeg != null ? st.outerDeg : 25f));
            innerDeg = clamp(innerDeg, 0.0f, 89.0f);
            outerDeg = clamp(outerDeg, innerDeg, 90.0f);

            sl.setSpotInnerAngle((float) Math.toRadians(innerDeg));
            sl.setSpotOuterAngle((float) Math.toRadians(outerDeg));

            st.innerDeg = innerDeg;
            st.outerDeg = outerDeg;
            st.radius = null;
        }
    }

    private static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
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
                float r = (float) num(v, "r", dr);
                float g = (float) num(v, "g", dg);
                float b = (float) num(v, "b", db);
                float a = (float) num(v, "a", da);
                return new ColorRGBA(r, g, b, a);
            }
        } catch (Throwable ignored) {}

        return new ColorRGBA(dr, dg, db, da);
    }

    private static Vector3f parseVec3(Value v, float dx, float dy, float dz) {
        if (v == null || v.isNull()) return new Vector3f(dx, dy, dz);

        try {
            if (v.hasArrayElements() && v.getArraySize() >= 3) {
                return new Vector3f(
                        (float) v.getArrayElement(0).asDouble(),
                        (float) v.getArrayElement(1).asDouble(),
                        (float) v.getArrayElement(2).asDouble()
                );
            }
            if (v.hasMembers()) {
                float x = (float) num(v, "x", dx);
                float y = (float) num(v, "y", dy);
                float z = (float) num(v, "z", dz);
                return new Vector3f(x, y, z);
            }
        } catch (Throwable ignored) {}

        return new Vector3f(dx, dy, dz);
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