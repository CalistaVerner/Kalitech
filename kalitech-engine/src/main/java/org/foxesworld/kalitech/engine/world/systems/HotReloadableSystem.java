package org.foxesworld.kalitech.engine.world.systems;

public interface HotReloadableSystem {
    /**
     * Called on MAIN thread by WorldAppState when user requests hot reload (F5).
     * System must be crash-safe and never throw.
     */
    void onHotReload(SystemContext ctx, String reason);
}