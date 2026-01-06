// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world.systems;

/**
 * KSystem
 *
 * A single world system executed either on the world thread or on worker lanes.
 * Provides scheduling hints (thread mode, runtime profile, budgets, priorities, and tick rates)
 * used by {@link SystemScheduler} to keep the frame stable under load.
 */
public interface KSystem {

    /** Called once when the system starts. */
    default void onStart(SystemContext ctx) {}

    /** Called on each tick (frequency depends on {@link #threadMode()} and scheduler policy). */
    default void onUpdate(SystemContext ctx, float tpf) {}

    /** Called once when the system stops. */
    default void onStop(SystemContext ctx) {}

    /**
     * Execution model for this system.
     *
     * <p><b>MAIN</b>:
     * <ul>
     *   <li>Runs on the world/main thread.</li>
     *   <li>Uses the shared "world" runtime and its hot caches.</li>
     *   <li>Safe to call engine/api, touch ECS and scene (subject to engine threading rules).</li>
     * </ul>
     *
     * <p><b>WORKER_*</b>:
     * <ul>
     *   <li>Runs on a worker thread/lane owned by {@link SystemScheduler}.</li>
     *   <li>Must not mutate jME scenegraph directly.</li>
     *   <li>Compute off-thread and apply changes via {@link SystemContext#jobs()} or main-thread queues.</li>
     * </ul>
     */
    default ThreadMode threadMode() {
        return ThreadMode.MAIN;
    }

    /**
     * Runtime profile requested for this system.
     *
     * <p>For MAIN systems, "world" is recommended (default).
     * For worker systems, return a unique profile per system (e.g. "sys.ai", "sys.ui", ...).
     *
     * <p>NOTE: for {@link ThreadMode#WORKER_STRIPED} the scheduler will internally suffix ".laneN"
     * to preserve ScriptRuntime owner-thread confinement.
     */
    default String runtimeProfile() {
        return "world";
    }

    // ---------------------------------------------------------------------
    // Production scheduling hints (AAA stability)
    // ---------------------------------------------------------------------

    /**
     * System priority (0..100). Higher priority systems get scheduled more aggressively under pressure.
     *
     * <p>Guideline:
     * <ul>
     *   <li>80..100: critical path (player input/camera/critical simulation)</li>
     *   <li>50: default</li>
     *   <li>0..30: background tasks (LOD, analytics, non-critical AI)</li>
     * </ul>
     */
    default int priority() {
        return 50;
    }

    /**
     * Desired tick rate (Hz). The scheduler may scale it down towards {@link #minHz()} under pressure.
     */
    default double desiredHz() {
        return 60.0;
    }

    /** Minimum allowed tick rate (Hz) when scaling down (must be > 0). */
    default double minHz() {
        return desiredHz();
    }

    /** Maximum allowed tick rate (Hz) when scaling up (must be >= desiredHz). */
    default double maxHz() {
        return desiredHz();
    }

    /**
     * Soft per-tick budget (nanoseconds). 0 means "no soft budget".
     * If exceeded repeatedly, the scheduler may reduce tick rate.
     */
    default long softBudgetNanos() {
        return 0L;
    }

    /**
     * Hard per-tick budget (nanoseconds). 0 means "no hard budget".
     * If exceeded, the scheduler escalates (more aggressive tick-rate reduction, strikes, logs).
     */
    default long hardBudgetNanos() {
        return 0L;
    }

    /**
     * True if the system is known to generate main-thread apply commands (heavy spawns/physics/scene ops).
     *
     * <p>When the main-thread apply queue is overloaded, such systems may be throttled/skipped
     * (unless they are high priority).
     */
    default boolean generatesMainThreadCommands() {
        return false;
    }
}