// FILE: ThreadMode.java
package org.foxesworld.kalitech.engine.world.systems;

/**
 * Where and how a {@link KSystem} executes.
 *
 * <p>Important constraints:
 * <ul>
 *   <li>In this project, {@code GraalScriptRuntime} is thread-confined: a runtime is owned by one thread.</li>
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
     * <p>Systems using this mode must request an isolated runtime profile via {@link KSystem#runtimeProfile()}.
     */
    WORKER_DEDICATED
}