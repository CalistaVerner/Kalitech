// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/pipeline/ShadowOrders.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

/**
 * Canonical filter order constants for the shadow pipeline.
 * <p>
 * Use these constants instead of magic numbers to keep ordering deterministic and readable.
 */
public final class ShadowOrders {

    /**
     * Very early stage: cascade split stabilization, LOD decisions, hysteresis.
     */
    public static final int CASCADE_STABILIZATION = -1000;
    /**
     * Early stage: deterministic light basis / anti-flip.
     */
    public static final int LIGHT_BASIS = -900;
    /**
     * Camera fitting stage: computes shadow camera frustum/position.
     */
    public static final int SHADOW_CAM_FIT = -500;
    /**
     * Optional temporal gating logic (may disable snap for tiny camera deltas).
     */
    public static final int TEMPORAL_GATE = 100;
    /**
     * Finalization stage: texel snap and hold-last-snap hysteresis.
     */
    public static final int TEXEL_SNAP_FINAL = 1000;
    /**
     * Late stage: debug/telemetry/trace.
     */
    public static final int DEBUG = 2000;

    private ShadowOrders() {
    }
}