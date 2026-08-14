package org.foxesworld.kalitech.engine.world.systems;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;

/**
 * Per-frame performance snapshot (main/world thread).
 *
 * <p>All fields are nanoseconds unless noted.
 * Exposed to Lua so you can build in-game profiler/overlay without touching Java.
 */
public final class FrameStats {

    @LuaExport public final long frameIndex;
    @LuaExport public final long budgetNanos;

    @LuaExport public final long frameNanos;
    @LuaExport public final long drainJobsNanos;
    @LuaExport public final long hotReloadNanos;
    @LuaExport public final long eventsNanos;
    @LuaExport public final long worldUpdateNanos;
    @LuaExport public final long awaitWorkersNanos;
    @LuaExport public final long poolMaintenanceNanos;

    @LuaExport public final int jobDrainBudget;
    @LuaExport public final long dispatcherCalls;
    @LuaExport public final long dispatcherTimeouts;

    public FrameStats(
            long frameIndex,
            long budgetNanos,
            long frameNanos,
            long drainJobsNanos,
            long hotReloadNanos,
            long eventsNanos,
            long worldUpdateNanos,
            long awaitWorkersNanos,
            long poolMaintenanceNanos,
            int jobDrainBudget,
            long dispatcherCalls,
            long dispatcherTimeouts
    ) {
        this.frameIndex = frameIndex;
        this.budgetNanos = budgetNanos;
        this.frameNanos = frameNanos;
        this.drainJobsNanos = drainJobsNanos;
        this.hotReloadNanos = hotReloadNanos;
        this.eventsNanos = eventsNanos;
        this.worldUpdateNanos = worldUpdateNanos;
        this.awaitWorkersNanos = awaitWorkersNanos;
        this.poolMaintenanceNanos = poolMaintenanceNanos;
        this.jobDrainBudget = jobDrainBudget;
        this.dispatcherCalls = dispatcherCalls;
        this.dispatcherTimeouts = dispatcherTimeouts;
    }
}