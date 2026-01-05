// FILE: KSystem.java
package org.foxesworld.kalitech.engine.world.systems;

public interface KSystem {
    default void onStart(SystemContext ctx) {}
    default void onUpdate(SystemContext ctx, float tpf) {}
    default void onStop(SystemContext ctx) {}

    /**
     * Execution model for this system.
     *
     * <p><b>MAIN</b>:
     * <ul>
     *   <li>Runs on the world/main thread.</li>
     *   <li>Uses the shared "world" runtime and its hot caches.</li>
     *   <li>Safe to call engine/api, touch ECS and scene (subject to your engine threading rules).</li>
     * </ul>
     *
     * <p><b>WORKER_DEDICATED</b>:
     * <ul>
     *   <li>Runs on a dedicated single thread owned by the system.</li>
     *   <li>Must use an isolated runtime profile (see {@link #runtimeProfile()}).</li>
     *   <li>Must not touch jME scenegraph directly; push changes through {@link SystemContext#jobs()}.</li>
     * </ul>
     */
    default ThreadMode threadMode() {
        return ThreadMode.MAIN;
    }

    /**
     * Runtime profile requested for this system.
     *
     * <p>For MAIN systems, "world" is recommended (default).
     * For WORKER_DEDICATED systems, return a unique profile per system (e.g. "sys.ai", "sys.ui", ...)
     * to ensure proper isolation and owner-thread confinement.
     */
    default String runtimeProfile() {
        return "world";
    }
}
