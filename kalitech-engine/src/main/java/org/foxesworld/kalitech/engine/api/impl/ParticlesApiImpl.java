// FILE: org/foxesworld/kalitech/engine/api/impl/ParticlesApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.foxesworld.kalitech.engine.api.interfaces.ParticlesApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.graalvm.polyglot.Value;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ParticlesApiImpl extends AbstractApiModule implements ParticlesApi {

    private static final long DEFAULT_TIMEOUT_MS = 2_000;
    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, ParticleEmitter> byId = new ConcurrentHashMap<>();
    private SimpleApplication app;
    private AssetManager assets;
    private Node root;

    public ParticlesApiImpl() {
        super("particles", "Particles", "1.0.0");
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

    // ------------------------------------------------------------
    // JS cfg parsing (tiny, defensive, no magic)
    // ------------------------------------------------------------

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
            float b = f(v, "b", def.b);
            float a = f(v, "a", def.a);
            return new ColorRGBA(r, g, b, a);
        } catch (Throwable ignored) {
            return def;
        }
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.app = ctx.app;
        this.assets = ctx.assets;
        this.root = ctx.app.getRootNode();
    }

    @Override
    public void detach() {
        // best-effort cleanup
        try {
            onJmeSyncVoid("detach", () -> {
                byId.values().forEach(e -> {
                    try {
                        e.removeFromParent();
                    } catch (Throwable ignored) {
                    }
                });
                byId.clear();
            });
        } catch (Throwable ignored) {
            byId.clear();
        } finally {
            this.root = null;
            this.assets = null;
            this.app = null;
            super.detach();
        }
    }

    // ------------------------------------------------------------
    // API
    // ------------------------------------------------------------

    @Override
    public ParticleHandle create(Value cfg) {
        return profiled(() -> onJmeSync("create", () -> {
            requireAttached();

            final int id = ids.getAndIncrement();
            final String name = str(cfg, "name", "fx-" + id);

            String typeS = str(cfg, "type", "triangle").toLowerCase();
            ParticleMesh.Type type = "point".equals(typeS) ? ParticleMesh.Type.Point : ParticleMesh.Type.Triangle;

            int max = i(cfg, "max", 256);
            ParticleEmitter em = new ParticleEmitter(name, type, max);

            // material/texture
            String tex = str(cfg, "texture", "");
            Material mat = new Material(assets, "Common/MatDefs/Misc/Particle.j3md");
            if (!tex.isBlank()) {
                mat.setTexture("Texture", assets.loadTexture(tex));
            }
            em.setMaterial(mat);

            int rows = i(cfg, "spriteRows", 1);
            int cols = i(cfg, "spriteCols", 1);
            em.setImagesX(Math.max(1, cols));
            em.setImagesY(Math.max(1, rows));

            // size
            Value size = m(cfg, "size");
            em.setStartSize(f(size, "start", 1.0f));
            em.setEndSize(f(size, "end", 0.1f));

            // life
            Value life = m(cfg, "life");
            em.setLowLife(f(life, "min", 0.5f));
            em.setHighLife(f(life, "max", 1.2f));

            // rate
            em.setParticlesPerSec(f(cfg, "rate", 32f));

            // gravity
            em.setGravity(vec3(m(cfg, "gravity"), new Vector3f(0, -3f, 0)));

            // velocity (speed scalar)
            Value vel = m(cfg, "velocity");
            em.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 1, 0));
            em.getParticleInfluencer().setVelocityVariation(1.0f);
            float vMin = f(vel, "min", 1.0f);
            float vMax = f(vel, "max", 3.0f);
            float v0 = Math.max(0.0f, (vMin + vMax) * 0.5f);
            em.getParticleInfluencer().setInitialVelocity(new Vector3f(0, v0, 0));

            // colors
            Value col = m(cfg, "color");
            em.setStartColor(color(m(col, "start"), new ColorRGBA(1, 1, 1, 1)));
            em.setEndColor(color(m(col, "end"), new ColorRGBA(1, 1, 1, 0)));

            // local/world space
            em.setInWorldSpace(!b(cfg, "local", true));

            // attach
            Objects.requireNonNull(root, "root").attachChild(em);
            byId.put(id, em);

            boolean enabled = b(cfg, "enabled", true);
            em.setEnabled(enabled);

            return new ParticleHandle(id);
        }, new ParticleHandle(0)));
    }

    @Override
    public void destroy(ParticleHandle h) {
        profiledVoid(() -> onJmeSyncVoid("destroy", () -> {
            ParticleEmitter em = get(h);
            if (em == null) return;
            byId.remove(h.id);
            em.removeFromParent();
        }));
    }

    @Override
    public void setEnabled(ParticleHandle h, boolean enabled) {
        profiledVoid(() -> onJmeSyncVoid("setEnabled", () -> {
            ParticleEmitter em = get(h);
            if (em != null) em.setEnabled(enabled);
        }));
    }

    @Override
    public void play(ParticleHandle h) {
        profiledVoid(() -> onJmeSyncVoid("play", () -> {
            ParticleEmitter em = get(h);
            if (em == null) return;
            em.setEnabled(true);
            em.setParticlesPerSec(Math.max(0.0f, em.getParticlesPerSec()));
        }));
    }

    @Override
    public void stop(ParticleHandle h) {
        profiledVoid(() -> onJmeSyncVoid("stop", () -> {
            ParticleEmitter em = get(h);
            if (em == null) return;
            em.setParticlesPerSec(0);
        }));
    }

    @Override
    public void configure(ParticleHandle h, Value cfg) {
        profiledVoid(() -> onJmeSyncVoid("configure", () -> {
            ParticleEmitter em = get(h);
            if (em == null) return;
            if (cfg == null || cfg.isNull()) return;

            // enabled
            if (cfg.hasMember("enabled")) {
                em.setEnabled(cfg.getMember("enabled").asBoolean());
            }

            // particles per sec (rate)
            if (cfg.hasMember("rate")) {
                float rate = (float) cfg.getMember("rate").asDouble();
                if (!Float.isFinite(rate) || rate < 0) rate = 0;
                em.setParticlesPerSec(rate);
            }

            // max (requires recreate in jME emitter!) -> do NOT support silently
            // If you want: implement rebuild() later, or explicitly throw.
            if (cfg.hasMember("max")) {
                // strict: fail fast so scripts know this cannot be patched safely
                throw new IllegalArgumentException("[particles] configure: 'max' cannot be changed on a live emitter");
            }

            // sprite sheet
            if (cfg.hasMember("spriteRows")) em.setImagesY(Math.max(1, (int) cfg.getMember("spriteRows").asDouble()));
            if (cfg.hasMember("spriteCols")) em.setImagesX(Math.max(1, (int) cfg.getMember("spriteCols").asDouble()));

            // world/local space (note inversion in create)
            if (cfg.hasMember("local")) {
                boolean local = cfg.getMember("local").asBoolean();
                em.setInWorldSpace(!local);
            }

            // size
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

            // life
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

            // gravity
            if (cfg.hasMember("gravity")) {
                em.setGravity(vec3(cfg.getMember("gravity"), em.getGravity()));
            }

            // colors
            if (cfg.hasMember("color")) {
                Value col = cfg.getMember("color");
                if (col != null && !col.isNull()) {
                    if (col.hasMember("start")) em.setStartColor(color(col.getMember("start"), em.getStartColor()));
                    if (col.hasMember("end")) em.setEndColor(color(col.getMember("end"), em.getEndColor()));
                }
            }

            // velocity (simple scalar model from your create())
            if (cfg.hasMember("velocity")) {
                Value vel = cfg.getMember("velocity");
                if (vel != null && !vel.isNull()) {
                    float vMin = vel.hasMember("min") ? (float) vel.getMember("min").asDouble() : 1.0f;
                    float vMax = vel.hasMember("max") ? (float) vel.getMember("max").asDouble() : 3.0f;
                    if (!Float.isFinite(vMin)) vMin = 1.0f;
                    if (!Float.isFinite(vMax)) vMax = 3.0f;
                    float v0 = Math.max(0.0f, (vMin + vMax) * 0.5f);
                    em.getParticleInfluencer().setInitialVelocity(new Vector3f(0, v0, 0));
                    em.getParticleInfluencer().setVelocityVariation(1.0f);
                }
            }

            // texture patch (safe)
            if (cfg.hasMember("texture")) {
                String tex = cfg.getMember("texture").asString();
                if (tex != null && !tex.isBlank()) {
                    Material mat = em.getMaterial();
                    if (mat == null) {
                        mat = new Material(assets, "Common/MatDefs/Misc/Particle.j3md");
                        em.setMaterial(mat);
                    }
                    mat.setTexture("Texture", assets.loadTexture(tex));
                }
            }
        }));
    }


    @Override
    public void setPosition(ParticleHandle h, Value v3) {
        profiledVoid(() -> onJmeSyncVoid("setPosition", () -> {
            ParticleEmitter em = get(h);
            if (em != null) em.setLocalTranslation(vec3(v3, em.getLocalTranslation()));
        }));
    }

    @Override
    public void setRotation(ParticleHandle h, Value q) {
        profiledVoid(() -> onJmeSyncVoid("setRotation", () -> {
            ParticleEmitter em = get(h);
            if (em != null) em.setLocalRotation(quat(q, em.getLocalRotation()));
        }));
    }

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

    @Override
    public void emitAll(ParticleHandle h) {
        profiledVoid(() -> onJmeSyncVoid("emitAll", () -> {
            ParticleEmitter em = get(h);
            if (em != null) em.emitAllParticles();
        }));
    }

    @Override
    public int alive() {
        return profiled(() -> byId.size());
    }

    private ParticleEmitter get(ParticleHandle h) {
        if (h == null || h.id <= 0) return null;
        return byId.get(h.id);
    }
}