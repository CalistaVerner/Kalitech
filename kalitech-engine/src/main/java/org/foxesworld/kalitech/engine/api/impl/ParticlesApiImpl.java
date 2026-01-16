// FILE: org/foxesworld/kalitech/engine/api/impl/ParticlesApiImpl.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.effect.shapes.EmitterBoxShape;
import com.jme3.effect.shapes.EmitterPointShape;
import com.jme3.effect.shapes.EmitterSphereShape;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.ParticlesApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ParticlesApiImpl extends AbstractApiModule implements ParticlesApi {

    private static final Logger log = LogManager.getLogger(ParticlesApiImpl.class);

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, ParticleEmitter> byId = new ConcurrentHashMap<>();

    private SimpleApplication app;
    private AssetManager assets;
    private Node root;

    public ParticlesApiImpl() {
        super("particles", "Particles", "2.2.1");
    }

    private static boolean dbg() {
        return log.isDebugEnabled();
    }

    private static boolean trc() {
        return log.isTraceEnabled();
    }

    private static String sVec(Vector3f v) {
        if (v == null) return "null";
        return "(" + v.x + "," + v.y + "," + v.z + ")";
    }

    private static String sQuat(Quaternion q) {
        if (q == null) return "null";
        return "(" + q.getX() + "," + q.getY() + "," + q.getZ() + "," + q.getW() + ")";
    }

    private static String sCol(ColorRGBA c) {
        if (c == null) return "null";
        return "(" + c.r + "," + c.g + "," + c.b + "," + c.a + ")";
    }

    private static Value m(Value v, String key) {
        try {
            if (v == null || v.isNull() || !v.hasMember(key)) return null;
            return v.getMember(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String str(Value v, String key, String def) {
        try {
            Value x = m(v, key);
            if (x == null || x.isNull()) return def;
            String s = x.asString();
            return (s == null || s.isBlank()) ? def : s;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static int i(Value v, String key, int def) {
        try {
            Value x = m(v, key);
            if (x == null || x.isNull()) return def;
            int n = (int) x.asDouble();
            return (n > 0) ? n : def;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static float f(Value v, String key, float def) {
        try {
            Value x = m(v, key);
            if (x == null || x.isNull()) return def;
            float n = (float) x.asDouble();
            return Float.isFinite(n) ? n : def;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static boolean b(Value v, String key, boolean def) {
        try {
            Value x = m(v, key);
            if (x == null || x.isNull()) return def;
            return x.asBoolean();
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static Vector3f vec3(Value v, Vector3f def) {
        try {
            if (v == null || v.isNull()) return def;

            float x = v.hasMember("x") ? (float) v.getMember("x").asDouble()
                    : (v.hasArrayElements() && v.getArraySize() > 0 ? (float) v.getArrayElement(0).asDouble() : def.x);
            float y = v.hasMember("y") ? (float) v.getMember("y").asDouble()
                    : (v.hasArrayElements() && v.getArraySize() > 1 ? (float) v.getArrayElement(1).asDouble() : def.y);
            float z = v.hasMember("z") ? (float) v.getMember("z").asDouble()
                    : (v.hasArrayElements() && v.getArraySize() > 2 ? (float) v.getArrayElement(2).asDouble() : def.z);

            if (!Float.isFinite(x)) x = def.x;
            if (!Float.isFinite(y)) y = def.y;
            if (!Float.isFinite(z)) z = def.z;

            return new Vector3f(x, y, z);
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static Quaternion quat(Value v, Quaternion def) {
        try {
            if (v == null || v.isNull()) return def;

            float x = (float) (m(v, "x") != null ? v.getMember("x").asDouble() : def.getX());
            float y = (float) (m(v, "y") != null ? v.getMember("y").asDouble() : def.getY());
            float z = (float) (m(v, "z") != null ? v.getMember("z").asDouble() : def.getZ());
            float w = (float) (m(v, "w") != null ? v.getMember("w").asDouble() : def.getW());

            if (!Float.isFinite(x)) x = def.getX();
            if (!Float.isFinite(y)) y = def.getY();
            if (!Float.isFinite(z)) z = def.getZ();
            if (!Float.isFinite(w)) w = def.getW();

            return new Quaternion(x, y, z, w);
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static ColorRGBA color(Value v, ColorRGBA def) {
        try {
            if (v == null || v.isNull()) return def;
            float r = f(v, "r", def.r);
            float g = f(v, "g", def.g);
            float bb = f(v, "b", def.b);
            float a = f(v, "a", def.a);

            if (!Float.isFinite(r)) r = def.r;
            if (!Float.isFinite(g)) g = def.g;
            if (!Float.isFinite(bb)) bb = def.b;
            if (!Float.isFinite(a)) a = def.a;

            return new ColorRGBA(r, g, bb, a);
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static Vector3f safeDir(Vector3f d) {
        if (d == null) return new Vector3f(0, 1, 0);
        float l2 = d.x * d.x + d.y * d.y + d.z * d.z;
        if (!(l2 > 1e-10f) || !Float.isFinite(l2)) return new Vector3f(0, 1, 0);
        return d.mult(1.0f / FastMath.sqrt(l2));
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.app = ctx.app;
        this.assets = ctx.assets;
        this.root = ctx.app.getRootNode();

        if (dbg()) {
            log.debug("[particles] attach ok app={} assets={} root={}",
                    (app != null ? app.getClass().getSimpleName() : "null"),
                    (assets != null ? "ok" : "null"),
                    (root != null ? "ok" : "null"));
        }
    }

    @Override
    public void detach() {
        if (dbg()) log.debug("[particles] detach begin alive={}", byId.size());
        try {
            onJmeSyncVoid("detach", () -> {
                byId.forEach((id, e) -> {
                    try {
                        if (trc())
                            log.trace("[particles] detach remove id={} name={}", id, (e != null ? e.getName() : "null"));
                        if (e != null) e.removeFromParent();
                    } catch (Throwable t) {
                        log.warn("[particles] detach remove failed id={} err={}", id, t.toString());
                    }
                });
                byId.clear();
            });
        } catch (Throwable t) {
            log.warn("[particles] detach sync failed err={}", t.toString());
            byId.clear();
        } finally {
            this.root = null;
            this.assets = null;
            this.app = null;
            super.detach();
            if (dbg()) log.debug("[particles] detach end");
        }
    }

    private void applyRenderFlags(ParticleEmitter em, Value cfg) {
        Value r = m(cfg, "render");
        if (r == null || r.isNull()) return;

        Material mat = em.getMaterial();
        if (mat == null) {
            log.warn("[particles] render flags skipped: material null name={}", em.getName());
            return;
        }

        RenderState rs = mat.getAdditionalRenderState();

        boolean additive = b(r, "additive", false);
        rs.setBlendMode(additive ? RenderState.BlendMode.Additive : RenderState.BlendMode.Alpha);

        boolean depthWrite = b(r, "depthWrite", false);
        rs.setDepthWrite(depthWrite);

        boolean depthTest = b(r, "depthTest", true);
        rs.setDepthTest(depthTest);

        boolean faceCullOff = b(r, "noCulling", true);
        if (faceCullOff) rs.setFaceCullMode(RenderState.FaceCullMode.Off);

        if (trc()) {
            log.trace("[particles] render name={} additive={} depthWrite={} depthTest={} noCulling={}",
                    em.getName(), additive, depthWrite, depthTest, faceCullOff);
        }
    }

    private void applyVelocity(ParticleEmitter em, Value cfg) {
        Value vel = m(cfg, "velocity");
        if (vel == null || vel.isNull()) return;

        float vMin = f(vel, "min", 1.0f);
        float vMax = f(vel, "max", 3.0f);
        if (!Float.isFinite(vMin)) vMin = 1.0f;
        if (!Float.isFinite(vMax)) vMax = 3.0f;
        if (vMax < vMin) {
            float t = vMin;
            vMin = vMax;
            vMax = t;
        }

        float base = Math.max(0.0f, (vMin + vMax) * 0.5f);

        Vector3f dirIn = vec3(m(vel, "dir"), new Vector3f(0, 1, 0));
        Vector3f dir = safeDir(dirIn);
        float coneDeg = f(vel, "coneDeg", 0.0f);
        float variation = f(vel, "variation", -1.0f);

        if (!Float.isFinite(coneDeg) || coneDeg < 0) coneDeg = 0;
        if (!Float.isFinite(variation)) variation = -1.0f;

        float cone01 = FastMath.clamp(coneDeg / 180.0f, 0f, 1f);

        float useVar;
        if (variation >= 0.0f) {
            useVar = FastMath.clamp(variation, 0f, 1f);
        } else {
            useVar = FastMath.clamp(cone01, 0f, 1f);
        }

        em.getParticleInfluencer().setInitialVelocity(dir.mult(base));
        em.getParticleInfluencer().setVelocityVariation(useVar);

        if (trc()) {
            log.trace("[particles] velocity name={} min={} max={} base={} dirIn={} dir={} coneDeg={} var={}",
                    em.getName(), vMin, vMax, base, sVec(dirIn), sVec(dir), coneDeg, useVar);
        }
    }

    private void applyShape(ParticleEmitter em, Value cfg) {
        Value sh = m(cfg, "shape");
        if (sh == null || sh.isNull()) return;

        String type = str(sh, "type", "point").trim().toLowerCase();

        switch (type) {
            case "sphere" -> {
                float r = f(sh, "radius", 0.25f);
                if (!Float.isFinite(r) || r < 0) r = 0.25f;
                em.setShape(new EmitterSphereShape(Vector3f.ZERO, r));
                if (trc()) log.trace("[particles] shape name={} type=sphere radius={}", em.getName(), r);
            }
            case "box" -> {
                Vector3f he = vec3(m(sh, "halfExtents"), new Vector3f(0.25f, 0.25f, 0.25f));
                he.x = Math.max(0, he.x);
                he.y = Math.max(0, he.y);
                he.z = Math.max(0, he.z);
                em.setShape(new EmitterBoxShape(he.negate(), he));
                if (trc()) log.trace("[particles] shape name={} type=box halfExtents={}", em.getName(), sVec(he));
            }
            case "point" -> {
                em.setShape(new EmitterPointShape(Vector3f.ZERO));
                if (trc()) log.trace("[particles] shape name={} type=point", em.getName());
            }
            default -> {
                em.setShape(new EmitterPointShape(Vector3f.ZERO));
                log.warn("[particles] shape unknown type='{}' -> fallback point (name={})", type, em.getName());
            }
        }
    }

    // ------------------------------------------------------------
    // API (exported to JS)
    // ------------------------------------------------------------

    @HostAccess.Export
    @Override
    public ParticleHandle create(Value cfg) {
        return profiled(() -> onJmeSync("create", () -> {
            requireAttached();

            if (assets == null || root == null) {
                log.error("[particles] create failed: assets/root missing assets={} root={}",
                        (assets != null ? "ok" : "null"), (root != null ? "ok" : "null"));
                return new ParticleHandle(0);
            }

            final int id = ids.getAndIncrement();
            final String name = str(cfg, "name", "fx-" + id);

            String typeS = str(cfg, "type", "triangle").toLowerCase();
            ParticleMesh.Type type = "point".equals(typeS) ? ParticleMesh.Type.Point : ParticleMesh.Type.Triangle;

            int max = i(cfg, "max", 256);
            ParticleEmitter em = new ParticleEmitter(name, type, max);

            String tex = str(cfg, "texture", "");
            Material mat = new Material(assets, "Common/MatDefs/Misc/Particle.j3md");
            if (!tex.isBlank()) {
                try {
                    mat.setTexture("Texture", assets.loadTexture(tex));
                } catch (Throwable t) {
                    log.warn("[particles] create texture load failed id={} name={} tex='{}' err={}", id, name, tex, t.toString());
                }
            }
            em.setMaterial(mat);

            applyRenderFlags(em, cfg);

            int rows = i(cfg, "spriteRows", 1);
            int cols = i(cfg, "spriteCols", 1);
            em.setImagesX(Math.max(1, cols));
            em.setImagesY(Math.max(1, rows));

            Value size = m(cfg, "size");
            float startSize = f(size, "start", 1.0f);
            float endSize = f(size, "end", 0.1f);
            em.setStartSize(startSize);
            em.setEndSize(endSize);

            Value life = m(cfg, "life");
            float lowLife = Math.max(1e-4f, f(life, "min", 0.5f));
            float highLife = Math.max(1e-4f, f(life, "max", 1.2f));
            em.setLowLife(lowLife);
            em.setHighLife(highLife);

            float rate = f(cfg, "rate", 32f);
            if (!Float.isFinite(rate) || rate < 0) {
                if (dbg()) log.debug("[particles] create id={} name={} invalid rate={} -> 0", id, name, rate);
                rate = 0;
            }
            em.setParticlesPerSec(rate);

            Vector3f gravity = vec3(m(cfg, "gravity"), new Vector3f(0, -3f, 0));
            em.setGravity(gravity);

            Value col = m(cfg, "color");
            ColorRGBA cStart = color(m(col, "start"), new ColorRGBA(1, 1, 1, 1));
            ColorRGBA cEnd = color(m(col, "end"), new ColorRGBA(1, 1, 1, 0));
            em.setStartColor(cStart);
            em.setEndColor(cEnd);

            boolean local = b(cfg, "local", true);
            em.setInWorldSpace(!local);

            applyVelocity(em, cfg);
            applyShape(em, cfg);

            Vector3f posApplied = null;
            Quaternion rotApplied = null;
            Float scaleApplied = null;

            if (cfg != null && !cfg.isNull()) {
                Value pos = m(cfg, "pos");
                if (pos != null && !pos.isNull()) {
                    posApplied = vec3(pos, em.getLocalTranslation());
                    em.setLocalTranslation(posApplied);
                }

                Value rot = m(cfg, "rot");
                if (rot != null && !rot.isNull()) {
                    rotApplied = quat(rot, em.getLocalRotation());
                    em.setLocalRotation(rotApplied);
                }

                if (cfg.hasMember("scale")) {
                    float s = (float) cfg.getMember("scale").asDouble();
                    if (!Float.isFinite(s) || s <= 0) {
                        log.warn("[particles] create id={} name={} invalid scale={} -> 1", id, name, s);
                        s = 1f;
                    }
                    scaleApplied = s;
                    em.setLocalScale(s);
                }
            }

            Objects.requireNonNull(root, "root").attachChild(em);
            byId.put(id, em);

            boolean enabled = b(cfg, "enabled", true);
            em.setEnabled(enabled);

            if (dbg()) {
                log.debug("[particles] create ok id={} name={} type={} max={} tex='{}' imgs={}x{} rate={} local={} life=[{}..{}] size=[{}..{}] gravity={} startColor={} endColor={} pos={} rot={} scale={} enabled={} alive={}",
                        id, name, type, max, tex,
                        em.getImagesX(), em.getImagesY(),
                        rate, local,
                        lowLife, highLife,
                        startSize, endSize,
                        sVec(gravity),
                        sCol(cStart), sCol(cEnd),
                        (posApplied != null ? sVec(posApplied) : "default"),
                        (rotApplied != null ? sQuat(rotApplied) : "default"),
                        (scaleApplied != null ? String.valueOf(scaleApplied) : "default"),
                        enabled,
                        byId.size());
            }

            return new ParticleHandle(id);
        }, new ParticleHandle(0)));
    }

    @HostAccess.Export
    @Override
    public void destroy(ParticleHandle h) {
        profiledVoid(() -> onJmeSyncVoid("destroy", () -> {
            if (h == null || h.id <= 0) {
                if (dbg()) log.debug("[particles] destroy ignored: invalid handle {}", (h != null ? h.id : "null"));
                return;
            }

            ParticleEmitter em = get(h);
            if (em == null) {
                if (dbg()) log.debug("[particles] destroy ignored: not found id={} alive={}", h.id, byId.size());
                byId.remove(h.id);
                return;
            }

            byId.remove(h.id);
            try {
                em.removeFromParent();
                if (dbg()) log.debug("[particles] destroy ok id={} name={} alive={}", h.id, em.getName(), byId.size());
            } catch (Throwable t) {
                log.warn("[particles] destroy failed id={} name={} err={}", h.id, em.getName(), t.toString());
            }
        }));
    }

    @HostAccess.Export
    @Override
    public void setEnabled(ParticleHandle h, boolean enabled) {
        profiledVoid(() -> onJmeSyncVoid("setEnabled", () -> {
            ParticleEmitter em = get(h);
            if (em != null) {
                em.setEnabled(enabled);
                if (trc()) log.trace("[particles] setEnabled id={} name={} enabled={}", h.id, em.getName(), enabled);
            } else if (dbg()) {
                log.debug("[particles] setEnabled ignored: not found id={} enabled={}", (h != null ? h.id : 0), enabled);
            }
        }));
    }

    @HostAccess.Export
    @Override
    public void play(ParticleHandle h) {
        profiledVoid(() -> onJmeSyncVoid("play", () -> {
            ParticleEmitter em = get(h);
            if (em == null) {
                if (dbg()) log.debug("[particles] play ignored: not found id={}", (h != null ? h.id : 0));
                return;
            }
            em.setEnabled(true);
            em.setParticlesPerSec(Math.max(0.0f, em.getParticlesPerSec()));
            if (dbg())
                log.debug("[particles] play ok id={} name={} rate={} enabled={}", h.id, em.getName(), em.getParticlesPerSec(), em.isEnabled());
        }));
    }

    @HostAccess.Export
    @Override
    public void stop(ParticleHandle h) {
        profiledVoid(() -> onJmeSyncVoid("stop", () -> {
            ParticleEmitter em = get(h);
            if (em == null) {
                if (dbg()) log.debug("[particles] stop ignored: not found id={}", (h != null ? h.id : 0));
                return;
            }
            em.setParticlesPerSec(0);
            if (dbg()) log.debug("[particles] stop ok id={} name={}", h.id, em.getName());
        }));
    }

    @HostAccess.Export
    @Override
    public void configure(ParticleHandle h, Value cfg) {
        profiledVoid(() -> onJmeSyncVoid("configure", () -> {
            ParticleEmitter em = get(h);
            if (em == null) {
                if (dbg()) log.debug("[particles] configure ignored: not found id={}", (h != null ? h.id : 0));
                return;
            }
            if (cfg == null || cfg.isNull()) {
                if (dbg()) log.debug("[particles] configure ignored: null cfg id={} name={}", h.id, em.getName());
                return;
            }

            if (trc()) log.trace("[particles] configure begin id={} name={}", h.id, em.getName());

            if (cfg.hasMember("enabled")) {
                boolean en = cfg.getMember("enabled").asBoolean();
                em.setEnabled(en);
                if (trc()) log.trace("[particles] configure enabled={} id={} name={}", en, h.id, em.getName());
            }

            if (cfg.hasMember("rate")) {
                float rate = (float) cfg.getMember("rate").asDouble();
                if (!Float.isFinite(rate) || rate < 0) {
                    log.warn("[particles] configure invalid rate={} -> 0 id={} name={}", rate, h.id, em.getName());
                    rate = 0;
                }
                em.setParticlesPerSec(rate);
                if (trc()) log.trace("[particles] configure rate={} id={} name={}", rate, h.id, em.getName());
            }

            if (cfg.hasMember("max")) {
                log.warn("[particles] configure rejected: 'max' cannot be changed id={} name={}", h.id, em.getName());
                throw new IllegalArgumentException("[particles] configure: 'max' cannot be changed on a live emitter");
            }

            if (cfg.hasMember("spriteRows")) em.setImagesY(Math.max(1, (int) cfg.getMember("spriteRows").asDouble()));
            if (cfg.hasMember("spriteCols")) em.setImagesX(Math.max(1, (int) cfg.getMember("spriteCols").asDouble()));

            if (cfg.hasMember("local")) {
                boolean local = cfg.getMember("local").asBoolean();
                em.setInWorldSpace(!local);
            }

            if (cfg.hasMember("size")) {
                Value size = cfg.getMember("size");
                if (size != null && !size.isNull()) {
                    if (size.hasMember("start")) {
                        float v = (float) size.getMember("start").asDouble();
                        if (Float.isFinite(v)) em.setStartSize(v);
                    }
                    if (size.hasMember("end")) {
                        float v = (float) size.getMember("end").asDouble();
                        if (Float.isFinite(v)) em.setEndSize(v);
                    }
                }
            }

            if (cfg.hasMember("life")) {
                Value life = cfg.getMember("life");
                if (life != null && !life.isNull()) {
                    if (life.hasMember("min")) {
                        float v = (float) life.getMember("min").asDouble();
                        if (Float.isFinite(v) && v > 0) em.setLowLife(v);
                    }
                    if (life.hasMember("max")) {
                        float v = (float) life.getMember("max").asDouble();
                        if (Float.isFinite(v) && v > 0) em.setHighLife(v);
                    }
                }
            }

            if (cfg.hasMember("gravity")) em.setGravity(vec3(cfg.getMember("gravity"), em.getGravity()));

            if (cfg.hasMember("color")) {
                Value col = cfg.getMember("color");
                if (col != null && !col.isNull()) {
                    if (col.hasMember("start")) em.setStartColor(color(col.getMember("start"), em.getStartColor()));
                    if (col.hasMember("end")) em.setEndColor(color(col.getMember("end"), em.getEndColor()));
                }
            }

            if (cfg.hasMember("texture")) {
                String tex = cfg.getMember("texture").asString();
                if (tex != null && !tex.isBlank()) {
                    try {
                        Material mat = em.getMaterial();
                        if (mat == null) {
                            mat = new Material(assets, "Common/MatDefs/Misc/Particle.j3md");
                            em.setMaterial(mat);
                        }
                        mat.setTexture("Texture", assets.loadTexture(tex));
                        if (dbg())
                            log.debug("[particles] configure texture ok id={} name={} tex='{}'", h.id, em.getName(), tex);
                    } catch (Throwable t) {
                        log.warn("[particles] configure texture failed id={} name={} tex='{}' err={}", h.id, em.getName(), tex, t.toString());
                    }
                }
            }

            if (cfg.hasMember("render")) applyRenderFlags(em, cfg);
            if (cfg.hasMember("velocity")) applyVelocity(em, cfg);
            if (cfg.hasMember("shape")) applyShape(em, cfg);

            if (cfg.hasMember("pos")) em.setLocalTranslation(vec3(cfg.getMember("pos"), em.getLocalTranslation()));
            if (cfg.hasMember("rot")) em.setLocalRotation(quat(cfg.getMember("rot"), em.getLocalRotation()));
            if (cfg.hasMember("scale")) {
                float s = (float) cfg.getMember("scale").asDouble();
                if (!Float.isFinite(s) || s <= 0) {
                    log.warn("[particles] configure invalid scale={} -> 1 id={} name={}", s, h.id, em.getName());
                    s = 1f;
                }
                em.setLocalScale(s);
            }

            if (dbg()) {
                log.debug("[particles] configure ok id={} name={} rate={} enabled={} local={} life=[{}..{}] size=[{}..{}] gravity={} alive={}",
                        h.id, em.getName(), em.getParticlesPerSec(), em.isEnabled(), !em.isInWorldSpace(),
                        em.getLowLife(), em.getHighLife(),
                        em.getStartSize(), em.getEndSize(),
                        sVec(em.getGravity()),
                        byId.size());
            }
        }));
    }

    @HostAccess.Export
    @Override
    public void setPosition(ParticleHandle h, Value v3) {
        profiledVoid(() -> onJmeSyncVoid("setPosition", () -> {
            ParticleEmitter em = get(h);
            if (em != null) em.setLocalTranslation(vec3(v3, em.getLocalTranslation()));
        }));
    }

    @HostAccess.Export
    @Override
    public void setRotation(ParticleHandle h, Value q) {
        profiledVoid(() -> onJmeSyncVoid("setRotation", () -> {
            ParticleEmitter em = get(h);
            if (em != null) em.setLocalRotation(quat(q, em.getLocalRotation()));
        }));
    }

    @HostAccess.Export
    @Override
    public void setScale(ParticleHandle h, double s) {
        profiledVoid(() -> onJmeSyncVoid("setScale", () -> {
            ParticleEmitter em = get(h);
            if (em == null) return;
            float fs = (float) s;
            if (!Float.isFinite(fs) || fs <= 0) fs = 1f;
            em.setLocalScale(fs);
        }));
    }

    @HostAccess.Export
    @Override
    public void emitAll(ParticleHandle h) {
        profiledVoid(() -> onJmeSyncVoid("emitAll", () -> {
            ParticleEmitter em = get(h);
            if (em != null) em.emitAllParticles();
        }));
    }

    @HostAccess.Export
    public void emit(ParticleHandle h, int count) {
        profiledVoid(() -> onJmeSyncVoid("emit", () -> {
            ParticleEmitter em = get(h);
            if (em == null) return;
            if (count <= 0) em.emitAllParticles();
            else em.emitParticles(count);
        }));
    }

    @HostAccess.Export
    @Override
    public int alive() {
        return profiled(() -> byId.size());
    }

    private ParticleEmitter get(ParticleHandle h) {
        if (h == null || h.id <= 0) return null;
        return byId.get(h.id);
    }
}