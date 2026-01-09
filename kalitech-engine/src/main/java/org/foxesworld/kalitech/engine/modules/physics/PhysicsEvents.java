package org.foxesworld.kalitech.engine.modules.physics;

/**
 * Script event topics emitted by physics module.
 */
public final class PhysicsEvents {
    public static final String BODY_CREATE = "engine.physics.body.create";
    public static final String BODY_REMOVE = "engine.physics.body.remove";
    public static final String COLL_BEGIN = "engine.physics.collision.begin";
    public static final String COLL_STAY = "engine.physics.collision.stay";
    public static final String COLL_END = "engine.physics.collision.end";
    public static final String POST_STEP = "engine.physics.postStep";

    private PhysicsEvents() {
    }
}