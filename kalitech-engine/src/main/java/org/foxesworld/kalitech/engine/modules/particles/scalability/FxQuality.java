// FILE: org/foxesworld/kalitech/engine/modules/particles/scalability/FxQuality.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles.scalability;

/**
 * FX quality tiers for scalability and budgeting.
 */
public enum FxQuality {
    LOW,
    MEDIUM,
    HIGH,
    ULTRA,
    AUTO;

    public static FxQuality parse(String s) {
        if (s == null) return AUTO;
        return switch (s.trim().toLowerCase()) {
            case "low" -> LOW;
            case "med", "medium" -> MEDIUM;
            case "high" -> HIGH;
            case "ultra" -> ULTRA;
            default -> AUTO;
        };
    }
}
