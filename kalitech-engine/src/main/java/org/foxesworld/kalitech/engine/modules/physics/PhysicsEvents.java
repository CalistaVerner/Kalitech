package org.foxesworld.kalitech.engine.modules.physics;

/**
 * Script event topics emitted by physics module.
 */
public final class PhysicsEvents {
    public static final String BODY_CREATE = "engine.physics.body.create";
    public static final String BODY_REMOVE = "engine.physics.body.remove";

    /**
     * Emitted when the body was actually added to PhysicsSpace (physics thread).
     * Useful to track the moment it starts simulating.
     */
    public static final String BODY_ADDED = "engine.physics.body.added";

    /**
     * Emitted when a body transitions from inactive to active.
     */
    public static final String BODY_WAKE = "engine.physics.body.wake";

    /**
     * Emitted when a body transitions from active to inactive.
     */
    public static final String BODY_SLEEP = "engine.physics.body.sleep";

    /**
     * Emitted when the body position/rotation changes beyond thresholds.
     */
    public static final String BODY_MOVE = "engine.physics.body.move";

    public static final String COLL_BEGIN = "engine.physics.collision.begin";
    public static final String COLL_STAY = "engine.physics.collision.stay";
    public static final String COLL_END = "engine.physics.collision.end";

    /**
     * High-level impact event (aggregated per pair per step).
     */
    public static final String IMPACT = "engine.physics.impact";

    public static final String POST_STEP = "engine.physics.postStep";

    private PhysicsEvents() {
    }
}