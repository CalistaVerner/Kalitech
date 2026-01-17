// FILE: org/foxesworld/kalitech/engine/modules/particles/ParticleEmitterOps.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles;

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
import com.jme3.scene.Spatial;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.modules.particles.ParticlesHostAccess.*;

/**
 * Deterministic creation/configuration of ParticleEmitter from JS config.
 * <p>
 * AAA goals:
 * <ul>
 *   <li>Clamp and sanitize all numeric inputs</li>
 *   <li>Optional advanced keys without breaking existing configs</li>
 *   <li>Minimize allocations in hot paths</li>
 * </ul>
 */
public final class ParticleEmitterOps {

    private static final int MAX_PARTICLES_HARD_CAP = 8192;
    private static final float LIFE_MIN = 1e-4f;
    private static final float SIZE_MIN = 1e-4f;
    private static final float RATE_MAX = 1_000_000f;

    private static final Vector3f TMP_VEC3 = new Vector3f();
    private static final Quaternion TMP_QUAT = new Quaternion();
    private static final ColorRGBA TMP_COL = new ColorRGBA();

    private ParticleEmitterOps() {
    }

    public static ParticleEmitter createEmitter(AssetManager assets, Value cfg, int id) {
        final String name = str(cfg, "name", "fx-" + id);
        final ParticleMesh.Type type = parseMeshType(str(cfg, "type", "triangle"));
        final int max = clampInt(i(cfg, "max", 256), 1, MAX_PARTICLES_HARD_CAP);

        final ParticleEmitter em = new ParticleEmitter(name, type, max);

        final Material mat = new Material(assets, "Common/MatDefs/Misc/Particle.j3md");
        final String tex = str(cfg, "texture", "");
        if (!tex.isBlank()) mat.setTexture("Texture", assets.loadTexture(tex));
        em.setMaterial(mat);

        applyRenderFlags(em, cfg);

        em.setImagesX(Math.max(1, i(cfg, "spriteCols", 1)));
        em.setImagesY(Math.max(1, i(cfg, "spriteRows", 1)));

        final Value size = m(cfg, "size");
        em.setStartSize(clampPosFinite(f(size, "start", 1.0f), SIZE_MIN, 1e6f));
        em.setEndSize(clampPosFinite(f(size, "end", 0.1f), SIZE_MIN, 1e6f));

        final Value life = m(cfg, "life");
        em.setLowLife(clampPosFinite(f(life, "min", 0.5f), LIFE_MIN, 1e6f));
        em.setHighLife(clampPosFinite(f(life, "max", 1.2f), LIFE_MIN, 1e6f));

        final float rate = clampFiniteNonNeg(f(cfg, "rate", 32f), 0f, RATE_MAX);
        em.setParticlesPerSec(rate);

        // gravity (no allocation)
        readVec3Into(m(cfg, "gravity"), TMP_VEC3, new Vector3f(0, -3f, 0));
        em.setGravity(TMP_VEC3.clone());

        final Value col = m(cfg, "color");
        readColorInto(m(col, "start"), TMP_COL, new ColorRGBA(1, 1, 1, 1));
        em.setStartColor(TMP_COL.clone());
        readColorInto(m(col, "end"), TMP_COL, new ColorRGBA(1, 1, 1, 0));
        em.setEndColor(TMP_COL.clone());

        final boolean local = b(cfg, "local", true);
        em.setInWorldSpace(!local);

        applyAdvancedFlags(em, cfg);
        applyVelocity(em, cfg);
        applyShape(em, cfg);
        applyTransform(em, cfg);

        return em;
    }

    public static void configureEmitter(AssetManager assets, ParticleEmitter em, Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        if (cfg.hasMember("max")) {
            throw new IllegalArgumentException("[particles] configure: 'max' cannot be changed on a live emitter");
        }

        if (cfg.hasMember("enabled")) em.setEnabled(cfg.getMember("enabled").asBoolean());

        if (cfg.hasMember("rate")) {
            float r = (float) cfg.getMember("rate").asDouble();
            em.setParticlesPerSec(clampFiniteNonNeg(r, 0f, RATE_MAX));
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
                    float s = (float) size.getMember("start").asDouble();
                    em.setStartSize(clampPosFinite(s, SIZE_MIN, 1e6f));
                }
                if (size.hasMember("end")) {
                    float s = (float) size.getMember("end").asDouble();
                    em.setEndSize(clampPosFinite(s, SIZE_MIN, 1e6f));
                }
            }
        }

        if (cfg.hasMember("life")) {
            Value life = cfg.getMember("life");
            if (life != null && !life.isNull()) {
                if (life.hasMember("min")) {
                    float v = (float) life.getMember("min").asDouble();
                    em.setLowLife(clampPosFinite(v, LIFE_MIN, 1e6f));
                }
                if (life.hasMember("max")) {
                    float v = (float) life.getMember("max").asDouble();
                    em.setHighLife(clampPosFinite(v, LIFE_MIN, 1e6f));
                }
            }
        }

        if (cfg.hasMember("gravity")) {
            readVec3Into(cfg.getMember("gravity"), TMP_VEC3, em.getGravity());
            em.setGravity(TMP_VEC3.clone());
        }

        if (cfg.hasMember("color")) {
            Value col = cfg.getMember("color");
            if (col != null && !col.isNull()) {
                if (col.hasMember("start")) {
                    readColorInto(col.getMember("start"), TMP_COL, em.getStartColor());
                    em.setStartColor(TMP_COL.clone());
                }
                if (col.hasMember("end")) {
                    readColorInto(col.getMember("end"), TMP_COL, em.getEndColor());
                    em.setEndColor(TMP_COL.clone());
                }
            }
        }

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

        if (cfg.hasMember("render")) applyRenderFlags(em, cfg);
        if (cfg.hasMember("velocity")) applyVelocity(em, cfg);
        if (cfg.hasMember("shape")) applyShape(em, cfg);

        applyAdvancedFlags(em, cfg);
        applyTransform(em, cfg);
    }

    // ------------------------ apply-* ------------------------

    private static void applyRenderFlags(ParticleEmitter em, Value cfg) {
        Value r = m(cfg, "render");
        if (r == null || r.isNull()) return;

        Material mat = em.getMaterial();
        if (mat == null) return;

        RenderState rs = mat.getAdditionalRenderState();

        boolean additive = b(r, "additive", false);
        rs.setBlendMode(additive ? RenderState.BlendMode.Additive : RenderState.BlendMode.Alpha);

        rs.setDepthWrite(b(r, "depthWrite", false));
        rs.setDepthTest(b(r, "depthTest", true));

        if (b(r, "noCulling", true)) rs.setFaceCullMode(RenderState.FaceCullMode.Off);
    }

    private static void applyAdvancedFlags(ParticleEmitter em, Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        if (cfg.hasMember("randomAngle")) em.setRandomAngle(cfg.getMember("randomAngle").asBoolean());
        if (cfg.hasMember("selectRandomImage")) em.setSelectRandomImage(cfg.getMember("selectRandomImage").asBoolean());
        if (cfg.hasMember("facingVelocity")) em.setFacingVelocity(cfg.getMember("facingVelocity").asBoolean());
        //if (cfg.hasMember("pointSprite")) em.setPointSprite(cfg.getMember("pointSprite").asBoolean());

        if (cfg.hasMember("rotateSpeed")) {
            float rs = (float) cfg.getMember("rotateSpeed").asDouble();
            em.setRotateSpeed(clampFinite(rs, -1e6f, 1e6f));
        }

        if (cfg.hasMember("faceNormal")) {
            readVec3Into(cfg.getMember("faceNormal"), TMP_VEC3, Vector3f.UNIT_Z);
            em.setFaceNormal(TMP_VEC3.clone());
        }

        if (cfg.hasMember("cullHint")) {
            String hint = cfg.getMember("cullHint").asString();
            if (hint != null) {
                switch (hint.trim().toLowerCase()) {
                    case "always" -> em.setCullHint(Spatial.CullHint.Always);
                    case "never" -> em.setCullHint(Spatial.CullHint.Never);
                    case "inherit" -> em.setCullHint(Spatial.CullHint.Inherit);
                    case "dynamic" -> em.setCullHint(Spatial.CullHint.Dynamic);
                    default -> {
                    }
                }
            }
        }
    }

    private static void applyVelocity(ParticleEmitter em, Value cfg) {
        Value vel = m(cfg, "velocity");
        if (vel == null || vel.isNull()) return;

        float vMin = clampFiniteNonNeg(f(vel, "min", 1.0f), 0f, 1e6f);
        float vMax = clampFiniteNonNeg(f(vel, "max", 3.0f), 0f, 1e6f);
        if (vMax < vMin) {
            float t = vMin;
            vMin = vMax;
            vMax = t;
        }

        float base = Math.max(0.0f, (vMin + vMax) * 0.5f);

        readVec3Into(m(vel, "dir"), TMP_VEC3, Vector3f.UNIT_Y);
        Vector3f dir = safeDirLocal(TMP_VEC3);

        float coneDeg = clampFiniteNonNeg(f(vel, "coneDeg", 0.0f), 0f, 180f);
        float variation = f(vel, "variation", -1.0f);

        float cone01 = FastMath.clamp(coneDeg / 180.0f, 0f, 1f);
        float useVar = variation >= 0.0f ? FastMath.clamp(variation, 0f, 1f) : cone01;

        em.getParticleInfluencer().setInitialVelocity(dir.mult(base));
        em.getParticleInfluencer().setVelocityVariation(useVar);
    }

    private static void applyShape(ParticleEmitter em, Value cfg) {
        Value sh = m(cfg, "shape");
        if (sh == null || sh.isNull()) return;

        String type = str(sh, "type", "point").trim().toLowerCase();

        switch (type) {
            case "sphere" -> {
                float r = clampFiniteNonNeg(f(sh, "radius", 0.25f), 0f, 1e6f);
                em.setShape(new EmitterSphereShape(Vector3f.ZERO, r));
            }
            case "box" -> {
                readVec3Into(m(sh, "halfExtents"), TMP_VEC3, new Vector3f(0.25f, 0.25f, 0.25f));
                TMP_VEC3.x = Math.max(0f, TMP_VEC3.x);
                TMP_VEC3.y = Math.max(0f, TMP_VEC3.y);
                TMP_VEC3.z = Math.max(0f, TMP_VEC3.z);
                Vector3f max = TMP_VEC3.clone();
                Vector3f min = max.negate();
                em.setShape(new EmitterBoxShape(min, max));
            }
            default -> em.setShape(new EmitterPointShape(Vector3f.ZERO));
        }
    }

    private static void applyTransform(ParticleEmitter em, Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        Value pos = m(cfg, "pos");
        if (pos != null && !pos.isNull()) {
            readVec3Into(pos, TMP_VEC3, em.getLocalTranslation());
            em.setLocalTranslation(TMP_VEC3.x, TMP_VEC3.y, TMP_VEC3.z);
        }

        Value rot = m(cfg, "rot");
        if (rot != null && !rot.isNull()) {
            readQuatInto(rot, TMP_QUAT, em.getLocalRotation());
            em.setLocalRotation(TMP_QUAT);
        }

        if (cfg.hasMember("scale")) em.setLocalScale(scale(cfg.getMember("scale").asDouble()));
    }

    // ------------------------ utils ------------------------

    private static ParticleMesh.Type parseMeshType(String s) {
        String t = (s == null ? "" : s.trim().toLowerCase());
        return "point".equals(t) ? ParticleMesh.Type.Point : ParticleMesh.Type.Triangle;
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min) return min;
        return Math.min(v, max);
    }

    private static float clampFinite(float v, float min, float max) {
        if (!Float.isFinite(v)) return min;
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static float clampFiniteNonNeg(float v, float min, float max) {
        if (!Float.isFinite(v)) return min;
        if (v < 0f) return 0f;
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static float clampPosFinite(float v, float min, float max) {
        if (!Float.isFinite(v)) return min;
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static Vector3f safeDirLocal(Vector3f d) {
        float l2 = d.x * d.x + d.y * d.y + d.z * d.z;
        if (!(l2 > 1e-10f) || !Float.isFinite(l2)) {
            d.set(0f, 1f, 0f);
            return d;
        }
        float inv = 1.0f / FastMath.sqrt(l2);
        d.multLocal(inv);
        return d;
    }
}