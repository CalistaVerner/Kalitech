// FILE: ThreadMode.java
package org.foxesworld.kalitech.engine.world.systems;

/**
 * Where and how a {@link KSystem} executes.
 *
 * <p>Important constraints:
 * <ul>
 *   <li>In this project, {@code ScriptRuntime} is thread-confined: a runtime is owned by one thread.</li>
 *   <li>In jME, most scenegraph mutations are expected to happen on the main/render thread.</li>
 * </ul>
 *
 * <p>Therefore, worker systems should compute data off-thread and apply changes via
 * {@link SystemContext#jobs()} back on the world thread.
 */
public enum ThreadMode {
    /**
     * Runs on the world/main thread.
     */
    MAIN,

    /**
     * Runs on a dedicated single thread, owned by the system.
     *
     * <p>Systems using this mode should request an isolated runtime profile via {@link KSystem#runtimeProfile()}.
     */
    WORKER_DEDICATED,

    /**
     * Runs on a shared worker lane (single-thread executor) owned by {@link SystemScheduler}.
     *
     * <p>AAA intent:
     * <ul>
     *   <li>Fixed number of worker threads (≈ CPU cores) instead of 1 thread per system.</li>
     *   <li>Stable performance: avoids thread explosion and reduces context switches.</li>
     *   <li>Still preserves {@code ScriptRuntime} thread confinement: lane == runtime owner thread.</li>
     * </ul>
     */
    WORKER_STRIPED
}