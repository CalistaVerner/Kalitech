// FILE: org/foxesworld/kalitech/engine/world/systems/HotReloadableSystem.java
package org.foxesworld.kalitech.engine.world.systems;

public interface HotReloadableSystem {
    /**
     * Called on MAIN thread during world hot reload.
     * Must be crash-safe and never throw.
     */
    void onHotReload(SystemContext ctx, String reason);
}