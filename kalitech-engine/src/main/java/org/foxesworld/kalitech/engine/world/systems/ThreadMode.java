// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world.systems;

/**
 * ThreadMode
 *
 * Defines where and how a {@link KSystem} executes (world thread vs worker threads).
 * Worker systems must compute off-thread and apply changes back on the world thread.
 */
public enum ThreadMode {

    /**
     * Runs on the world/main thread.
     *
     * <p>Best for systems that:
     * <ul>
     *   <li>Touch the scenegraph/physics directly.</li>
     *   <li>Need the shared "world" ScriptRuntime and hot caches.</li>
     * </ul>
     */
    MAIN,

    /**
     * Runs on a dedicated worker thread (1 thread per system).
     *
     * <p>Use for special/isolated systems:
     * <ul>
     *   <li>Tools / UI sandboxes / hotreload runtimes</li>
     *   <li>Systems that must not share a lane with others</li>
     * </ul>
     */
    WORKER_DEDICATED,

    /**
     * Runs on a shared worker lane (single-thread executor) owned by {@link SystemScheduler}.
     *
     * <p>AAA intent:
     * <ul>
     *   <li>Fixed number of worker threads (≈ CPU cores) instead of 1 thread per system.</li>
     *   <li>Stable performance: avoids thread explosion and reduces context switches.</li>
     *   <li>Preserves ScriptRuntime thread confinement: lane == runtime owner thread.</li>
     * </ul>
     */
    WORKER_STRIPED
}