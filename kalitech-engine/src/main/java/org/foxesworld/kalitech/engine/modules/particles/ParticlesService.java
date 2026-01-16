// FILE: org/foxesworld/kalitech/engine/modules/particles/ParticlesService.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles;

import com.jme3.effect.ParticleEmitter;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.modules.particles.scalability.FxQuality;
import org.foxesworld.kalitech.engine.modules.particles.scalability.FxScalability;
import org.graalvm.polyglot.Value;

import java.util.Collection;
import java.util.Objects;

/**
 * Runtime FX management layer (AAA foundation).
 * Backward compatibility:
 * - If beginFrame() is never called, spawn budget is treated as unlimited (legacy behavior).
 */
public final class ParticlesService {

    public static final String UD_PRIORITY = "fx.priority";
    public static final String UD_MAX_DISTANCE = "fx.maxDistance";

    private final FxScalability scalability = new FxScalability();

    private int spawnBudgetLeft = Integer.MAX_VALUE;
    private boolean frameBegun = false;

    public FxQuality getQuality() {
        return scalability.getQuality();
    }

    public void setQuality(FxQuality q) {
        scalability.setQuality(Objects.requireNonNull(q, "q"));
    }

    public FxScalability.Budget budget() {
        return scalability.active();
    }

    /**
     * Call once per frame to enable budgeting.
     * If not called, budgeting remains unlimited (legacy behavior).
     */
    public void beginFrame() {
        frameBegun = true;
        spawnBudgetLeft = budget().maxSpawnParticlesPerFrame;
    }

    /**
     * Used by API to keep behavior stable.
     */
    public void resetToLegacyUnlimited() {
        frameBegun = false;
        spawnBudgetLeft = Integer.MAX_VALUE;
    }

    public boolean isFrameBegun() {
        return frameBegun;
    }

    public void applyBudgetMeta(ParticleEmitter em, Value cfg) {
        if (em == null || cfg == null || cfg.isNull() || !cfg.hasMember("budget")) return;

        Value b = cfg.getMember("budget");
        if (b == null || b.isNull()) return;

        if (b.hasMember("priority")) {
            float p = (float) b.getMember("priority").asDouble();
            if (Float.isFinite(p)) em.setUserData(UD_PRIORITY, p);
        }
        if (b.hasMember("maxDistance")) {
            float d = (float) b.getMember("maxDistance").asDouble();
            if (Float.isFinite(d) && d > 0f) em.setUserData(UD_MAX_DISTANCE, d);
        }
    }

    public void cullByDistance(Collection<ParticleEmitter> emitters, float camX, float camY, float camZ) {
        if (emitters == null || emitters.isEmpty()) return;

        float global = budget().globalCullDistanceMeters;

        for (ParticleEmitter em : emitters) {
            if (em == null) continue;

            Float per = em.getUserData(UD_MAX_DISTANCE);
            float max = (per != null && per > 0f) ? per : global;
            float max2 = max * max;

            float dx = em.getWorldTranslation().x - camX;
            float dy = em.getWorldTranslation().y - camY;
            float dz = em.getWorldTranslation().z - camZ;
            float d2 = dx * dx + dy * dy + dz * dz;

            if (!(d2 >= 0f) || !Float.isFinite(d2)) {
                em.setCullHint(Spatial.CullHint.Inherit);
                continue;
            }

            if (d2 > max2) em.setCullHint(Spatial.CullHint.Always);
            else em.setCullHint(Spatial.CullHint.Inherit);
        }
    }

    /**
     * Enforces per-frame spawn budget.
     * Legacy behavior: if beginFrame() wasn't called, budget is unlimited.
     */
    public int allowSpawn(int requested) {
        if (requested <= 0) return 0;

        if (!frameBegun) {
            return requested; // legacy/unlimited
        }

        if (spawnBudgetLeft <= 0) return 0;

        int allowed = Math.min(requested, spawnBudgetLeft);
        spawnBudgetLeft -= allowed;
        return allowed;
    }
}