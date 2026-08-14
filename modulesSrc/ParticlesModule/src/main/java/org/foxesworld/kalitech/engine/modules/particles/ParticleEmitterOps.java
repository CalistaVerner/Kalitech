/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.asset.AssetManager
 *  com.jme3.effect.ParticleEmitter
 *  com.jme3.effect.ParticleMesh$Type
 *  com.jme3.effect.shapes.EmitterBoxShape
 *  com.jme3.effect.shapes.EmitterPointShape
 *  com.jme3.effect.shapes.EmitterShape
 *  com.jme3.effect.shapes.EmitterSphereShape
 *  com.jme3.material.Material
 *  com.jme3.material.RenderState
 *  com.jme3.material.RenderState$BlendMode
 *  com.jme3.material.RenderState$FaceCullMode
 *  com.jme3.math.ColorRGBA
 *  com.jme3.math.FastMath
 *  com.jme3.math.Quaternion
 *  com.jme3.math.Vector3f
 *  com.jme3.scene.Spatial$CullHint
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.particles;

import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.effect.shapes.EmitterBoxShape;
import com.jme3.effect.shapes.EmitterPointShape;
import com.jme3.effect.shapes.EmitterShape;
import com.jme3.effect.shapes.EmitterSphereShape;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.modules.particles.ParticleLuaConfig;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class ParticleEmitterOps {
    private static final int MAX_PARTICLES_HARD_CAP = 8192;
    private static final float LIFE_MIN = 1.0E-4f;
    private static final float SIZE_MIN = 1.0E-4f;
    private static final float RATE_MAX = 1000000.0f;
    private static final Vector3f TMP_VEC3 = new Vector3f();
    private static final Quaternion TMP_QUAT = new Quaternion();
    private static final ColorRGBA TMP_COL = new ColorRGBA();

    private ParticleEmitterOps() {
    }

    public static ParticleEmitter createEmitter(AssetManager assets, LuaValueRef cfg, int id) {
        String name = ParticleLuaConfig.str(cfg, "name", "fx-" + id);
        ParticleMesh.Type type = ParticleEmitterOps.parseMeshType(ParticleLuaConfig.str(cfg, "type", "triangle"));
        int max = ParticleEmitterOps.clampInt(ParticleLuaConfig.i(cfg, "max", 256), 1, 8192);
        ParticleEmitter em = new ParticleEmitter(name, type, max);
        Material mat = new Material(assets, "Common/MatDefs/Misc/Particle.j3md");
        String tex = ParticleLuaConfig.str(cfg, "texture", "");
        if (!tex.isBlank()) {
            mat.setTexture("Texture", assets.loadTexture(tex));
        }
        em.setMaterial(mat);
        ParticleEmitterOps.applyRenderFlags(em, cfg);
        em.setImagesX(Math.max(1, ParticleLuaConfig.i(cfg, "spriteCols", 1)));
        em.setImagesY(Math.max(1, ParticleLuaConfig.i(cfg, "spriteRows", 1)));
        LuaValueRef size = ParticleLuaConfig.m(cfg, "size");
        em.setStartSize(ParticleEmitterOps.clampPosFinite(ParticleLuaConfig.f(size, "start", 1.0f), 1.0E-4f, 1000000.0f));
        em.setEndSize(ParticleEmitterOps.clampPosFinite(ParticleLuaConfig.f(size, "end", 0.1f), 1.0E-4f, 1000000.0f));
        LuaValueRef life = ParticleLuaConfig.m(cfg, "life");
        em.setLowLife(ParticleEmitterOps.clampPosFinite(ParticleLuaConfig.f(life, "min", 0.5f), 1.0E-4f, 1000000.0f));
        em.setHighLife(ParticleEmitterOps.clampPosFinite(ParticleLuaConfig.f(life, "max", 1.2f), 1.0E-4f, 1000000.0f));
        float rate = ParticleEmitterOps.clampFiniteNonNeg(ParticleLuaConfig.f(cfg, "rate", 32.0f), 0.0f, 1000000.0f);
        em.setParticlesPerSec(rate);
        ParticleLuaConfig.readVec3Into(ParticleLuaConfig.m(cfg, "gravity"), TMP_VEC3, new Vector3f(0.0f, -3.0f, 0.0f));
        em.setGravity(TMP_VEC3.clone());
        LuaValueRef col = ParticleLuaConfig.m(cfg, "color");
        ParticleLuaConfig.readColorInto(ParticleLuaConfig.m(col, "start"), TMP_COL, new ColorRGBA(1.0f, 1.0f, 1.0f, 1.0f));
        em.setStartColor(TMP_COL.clone());
        ParticleLuaConfig.readColorInto(ParticleLuaConfig.m(col, "end"), TMP_COL, new ColorRGBA(1.0f, 1.0f, 1.0f, 0.0f));
        em.setEndColor(TMP_COL.clone());
        boolean local = ParticleLuaConfig.b(cfg, "local", true);
        em.setInWorldSpace(!local);
        ParticleEmitterOps.applyAdvancedFlags(em, cfg);
        ParticleEmitterOps.applyVelocity(em, cfg);
        ParticleEmitterOps.applyShape(em, cfg);
        ParticleEmitterOps.applyTransform(em, cfg);
        return em;
    }

    public static void configureEmitter(AssetManager assets, ParticleEmitter em, LuaValueRef cfg) {
        String tex;
        LuaValueRef col;
        LuaValueRef life;
        LuaValueRef size;
        if (cfg == null || cfg.isNull()) {
            return;
        }
        if (cfg.hasMember("max")) {
            throw new IllegalArgumentException("[particles] configure: 'max' cannot be changed on a live emitter");
        }
        if (cfg.hasMember("enabled")) {
            em.setEnabled(cfg.getMember("enabled").asBoolean());
        }
        if (cfg.hasMember("rate")) {
            float r = (float)cfg.getMember("rate").asDouble();
            em.setParticlesPerSec(ParticleEmitterOps.clampFiniteNonNeg(r, 0.0f, 1000000.0f));
        }
        if (cfg.hasMember("spriteRows")) {
            em.setImagesY(Math.max(1, (int)cfg.getMember("spriteRows").asDouble()));
        }
        if (cfg.hasMember("spriteCols")) {
            em.setImagesX(Math.max(1, (int)cfg.getMember("spriteCols").asDouble()));
        }
        if (cfg.hasMember("local")) {
            boolean local = cfg.getMember("local").asBoolean();
            em.setInWorldSpace(!local);
        }
        if (cfg.hasMember("size") && (size = cfg.getMember("size")) != null && !size.isNull()) {
            float s;
            if (size.hasMember("start")) {
                s = (float)size.getMember("start").asDouble();
                em.setStartSize(ParticleEmitterOps.clampPosFinite(s, 1.0E-4f, 1000000.0f));
            }
            if (size.hasMember("end")) {
                s = (float)size.getMember("end").asDouble();
                em.setEndSize(ParticleEmitterOps.clampPosFinite(s, 1.0E-4f, 1000000.0f));
            }
        }
        if (cfg.hasMember("life") && (life = cfg.getMember("life")) != null && !life.isNull()) {
            float v;
            if (life.hasMember("min")) {
                v = (float)life.getMember("min").asDouble();
                em.setLowLife(ParticleEmitterOps.clampPosFinite(v, 1.0E-4f, 1000000.0f));
            }
            if (life.hasMember("max")) {
                v = (float)life.getMember("max").asDouble();
                em.setHighLife(ParticleEmitterOps.clampPosFinite(v, 1.0E-4f, 1000000.0f));
            }
        }
        if (cfg.hasMember("gravity")) {
            ParticleLuaConfig.readVec3Into(cfg.getMember("gravity"), TMP_VEC3, em.getGravity());
            em.setGravity(TMP_VEC3.clone());
        }
        if (cfg.hasMember("color") && (col = cfg.getMember("color")) != null && !col.isNull()) {
            if (col.hasMember("start")) {
                ParticleLuaConfig.readColorInto(col.getMember("start"), TMP_COL, em.getStartColor());
                em.setStartColor(TMP_COL.clone());
            }
            if (col.hasMember("end")) {
                ParticleLuaConfig.readColorInto(col.getMember("end"), TMP_COL, em.getEndColor());
                em.setEndColor(TMP_COL.clone());
            }
        }
        if (cfg.hasMember("texture") && (tex = cfg.getMember("texture").asString()) != null && !tex.isBlank()) {
            Material mat = em.getMaterial();
            if (mat == null) {
                mat = new Material(assets, "Common/MatDefs/Misc/Particle.j3md");
                em.setMaterial(mat);
            }
            mat.setTexture("Texture", assets.loadTexture(tex));
        }
        if (cfg.hasMember("render")) {
            ParticleEmitterOps.applyRenderFlags(em, cfg);
        }
        if (cfg.hasMember("velocity")) {
            ParticleEmitterOps.applyVelocity(em, cfg);
        }
        if (cfg.hasMember("shape")) {
            ParticleEmitterOps.applyShape(em, cfg);
        }
        ParticleEmitterOps.applyAdvancedFlags(em, cfg);
        ParticleEmitterOps.applyTransform(em, cfg);
    }

    private static void applyRenderFlags(ParticleEmitter em, LuaValueRef cfg) {
        LuaValueRef r = ParticleLuaConfig.m(cfg, "render");
        if (r == null || r.isNull()) {
            return;
        }
        Material mat = em.getMaterial();
        if (mat == null) {
            return;
        }
        RenderState rs = mat.getAdditionalRenderState();
        boolean additive = ParticleLuaConfig.b(r, "additive", false);
        rs.setBlendMode(additive ? RenderState.BlendMode.Additive : RenderState.BlendMode.Alpha);
        rs.setDepthWrite(ParticleLuaConfig.b(r, "depthWrite", false));
        rs.setDepthTest(ParticleLuaConfig.b(r, "depthTest", true));
        if (ParticleLuaConfig.b(r, "noCulling", true)) {
            rs.setFaceCullMode(RenderState.FaceCullMode.Off);
        }
    }

    private static void applyAdvancedFlags(ParticleEmitter em, LuaValueRef cfg) {
        String hint;
        if (cfg == null || cfg.isNull()) {
            return;
        }
        if (cfg.hasMember("randomAngle")) {
            em.setRandomAngle(cfg.getMember("randomAngle").asBoolean());
        }
        if (cfg.hasMember("selectRandomImage")) {
            em.setSelectRandomImage(cfg.getMember("selectRandomImage").asBoolean());
        }
        if (cfg.hasMember("facingVelocity")) {
            em.setFacingVelocity(cfg.getMember("facingVelocity").asBoolean());
        }
        if (cfg.hasMember("rotateSpeed")) {
            float rs = (float)cfg.getMember("rotateSpeed").asDouble();
            em.setRotateSpeed(ParticleEmitterOps.clampFinite(rs, -1000000.0f, 1000000.0f));
        }
        if (cfg.hasMember("faceNormal")) {
            ParticleLuaConfig.readVec3Into(cfg.getMember("faceNormal"), TMP_VEC3, Vector3f.UNIT_Z);
            em.setFaceNormal(TMP_VEC3.clone());
        }
        if (cfg.hasMember("cullHint") && (hint = cfg.getMember("cullHint").asString()) != null) {
            switch (hint.trim().toLowerCase()) {
                case "always": {
                    em.setCullHint(Spatial.CullHint.Always);
                    break;
                }
                case "never": {
                    em.setCullHint(Spatial.CullHint.Never);
                    break;
                }
                case "inherit": {
                    em.setCullHint(Spatial.CullHint.Inherit);
                    break;
                }
                case "dynamic": {
                    em.setCullHint(Spatial.CullHint.Dynamic);
                    break;
                }
            }
        }
    }

    private static void applyVelocity(ParticleEmitter em, LuaValueRef cfg) {
        LuaValueRef vel = ParticleLuaConfig.m(cfg, "velocity");
        if (vel == null || vel.isNull()) {
            return;
        }
        float vMin = ParticleEmitterOps.clampFiniteNonNeg(ParticleLuaConfig.f(vel, "min", 1.0f), 0.0f, 1000000.0f);
        float vMax = ParticleEmitterOps.clampFiniteNonNeg(ParticleLuaConfig.f(vel, "max", 3.0f), 0.0f, 1000000.0f);
        if (vMax < vMin) {
            float t = vMin;
            vMin = vMax;
            vMax = t;
        }
        float base = Math.max(0.0f, (vMin + vMax) * 0.5f);
        ParticleLuaConfig.readVec3Into(ParticleLuaConfig.m(vel, "dir"), TMP_VEC3, Vector3f.UNIT_Y);
        Vector3f dir = ParticleEmitterOps.safeDirLocal(TMP_VEC3);
        float coneDeg = ParticleEmitterOps.clampFiniteNonNeg(ParticleLuaConfig.f(vel, "coneDeg", 0.0f), 0.0f, 180.0f);
        float variation = ParticleLuaConfig.f(vel, "variation", -1.0f);
        float cone01 = FastMath.clamp((float)(coneDeg / 180.0f), (float)0.0f, (float)1.0f);
        float useVar = variation >= 0.0f ? FastMath.clamp((float)variation, (float)0.0f, (float)1.0f) : cone01;
        em.getParticleInfluencer().setInitialVelocity(dir.mult(base));
        em.getParticleInfluencer().setVelocityVariation(useVar);
    }

    private static void applyShape(ParticleEmitter em, LuaValueRef cfg) {
        String type;
        LuaValueRef sh = ParticleLuaConfig.m(cfg, "shape");
        if (sh == null || sh.isNull()) {
            return;
        }
        switch (type = ParticleLuaConfig.str(sh, "type", "point").trim().toLowerCase()) {
            case "sphere": {
                float r = ParticleEmitterOps.clampFiniteNonNeg(ParticleLuaConfig.f(sh, "radius", 0.25f), 0.0f, 1000000.0f);
                em.setShape((EmitterShape)new EmitterSphereShape(Vector3f.ZERO, r));
                break;
            }
            case "box": {
                ParticleLuaConfig.readVec3Into(ParticleLuaConfig.m(sh, "halfExtents"), TMP_VEC3, new Vector3f(0.25f, 0.25f, 0.25f));
                ParticleEmitterOps.TMP_VEC3.x = Math.max(0.0f, ParticleEmitterOps.TMP_VEC3.x);
                ParticleEmitterOps.TMP_VEC3.y = Math.max(0.0f, ParticleEmitterOps.TMP_VEC3.y);
                ParticleEmitterOps.TMP_VEC3.z = Math.max(0.0f, ParticleEmitterOps.TMP_VEC3.z);
                Vector3f max = TMP_VEC3.clone();
                Vector3f min = max.negate();
                em.setShape((EmitterShape)new EmitterBoxShape(min, max));
                break;
            }
            default: {
                em.setShape((EmitterShape)new EmitterPointShape(Vector3f.ZERO));
            }
        }
    }

    private static void applyTransform(ParticleEmitter em, LuaValueRef cfg) {
        LuaValueRef rot;
        if (cfg == null || cfg.isNull()) {
            return;
        }
        LuaValueRef pos = ParticleLuaConfig.m(cfg, "pos");
        if (pos != null && !pos.isNull()) {
            ParticleLuaConfig.readVec3Into(pos, TMP_VEC3, em.getLocalTranslation());
            em.setLocalTranslation(ParticleEmitterOps.TMP_VEC3.x, ParticleEmitterOps.TMP_VEC3.y, ParticleEmitterOps.TMP_VEC3.z);
        }
        if ((rot = ParticleLuaConfig.m(cfg, "rot")) != null && !rot.isNull()) {
            ParticleLuaConfig.readQuatInto(rot, TMP_QUAT, em.getLocalRotation());
            em.setLocalRotation(TMP_QUAT);
        }
        if (cfg.hasMember("scale")) {
            em.setLocalScale(ParticleLuaConfig.scale(cfg.getMember("scale").asDouble()));
        }
    }

    private static ParticleMesh.Type parseMeshType(String s) {
        String t = s == null ? "" : s.trim().toLowerCase();
        return "point".equals(t) ? ParticleMesh.Type.Point : ParticleMesh.Type.Triangle;
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min) {
            return min;
        }
        return Math.min(v, max);
    }

    private static float clampFinite(float v, float min, float max) {
        if (!Float.isFinite(v)) {
            return min;
        }
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    private static float clampFiniteNonNeg(float v, float min, float max) {
        if (!Float.isFinite(v)) {
            return min;
        }
        if (v < 0.0f) {
            return 0.0f;
        }
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    private static float clampPosFinite(float v, float min, float max) {
        if (!Float.isFinite(v)) {
            return min;
        }
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    private static Vector3f safeDirLocal(Vector3f d) {
        float l2 = d.x * d.x + d.y * d.y + d.z * d.z;
        if (!(l2 > 1.0E-10f) || !Float.isFinite(l2)) {
            d.set(0.0f, 1.0f, 0.0f);
            return d;
        }
        float inv = 1.0f / FastMath.sqrt((float)l2);
        d.multLocal(inv);
        return d;
    }
}

