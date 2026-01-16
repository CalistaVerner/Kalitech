package org.foxesworld.kalitech.engine.ecs;

public final class EntityId {
    private EntityId() {}
    public static final int NULL = -1;

    /**
     * Packs an (entityId,generation) pair into a single long handle.
     * Useful for internal systems that need stale-reference checks.
     */
    public static long packHandle(int entityId, int generation) {
        return ((long) generation << 32) | (entityId & 0xFFFFFFFFL);
    }

    /**
     * Extracts the entityId from a packed handle.
     */
    public static int unpackEntityId(long handle) {
        return (int) handle;
    }

    /**
     * Extracts the generation from a packed handle.
     */
    public static int unpackGeneration(long handle) {
        return (int) (handle >>> 32);
    }
}