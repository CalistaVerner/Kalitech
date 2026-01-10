package org.foxesworld.kalitech.engine.ecs;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stable UUID registry for ECS entities.
 *
 * Contract:
 * - UUID is the only external/public id.
 * - int entityId is internal dense index.
 *
 * Sentinel:
 * - UUID (0,0) is reserved.
 */
public final class EntityUuids {

    private static final long EMPTY = 0L;

    private final UuidToIntMap index = new UuidToIntMap(256);

    private long[] msbById = new long[0];
    private long[] lsbById = new long[0];
    private int capacity = 0;

    private static int nextPow2(int v) {
        int x = 1;
        while (x < v) x <<= 1;
        return x;
    }

    void onCreate(int entityId) {
        if (entityId <= 0) throw new IllegalArgumentException("entityId must be > 0");
        ensureCapacity(entityId);

        long msb, lsb;
        do {
            msb = ThreadLocalRandom.current().nextLong();
            lsb = ThreadLocalRandom.current().nextLong();
        } while ((msb == 0L && lsb == 0L) || index.contains(msb, lsb));

        setInternal(entityId, msb, lsb);
    }

    void onDestroy(int entityId) {
        if (entityId <= 0) throw new IllegalArgumentException("entityId must be > 0");
        if (entityId >= capacity) return;

        long msb = msbById[entityId];
        long lsb = lsbById[entityId];
        if (msb == EMPTY && lsb == EMPTY) return;

        index.remove(msb, lsb);
        msbById[entityId] = 0L;
        lsbById[entityId] = 0L;
    }

    /**
     * Force-set UUID for an entity (save/load).
     * INTERNAL: not a public "register by id" API.
     */
    void setForLoad(int entityId, String uuid) {
        if (entityId <= 0) throw new IllegalArgumentException("entityId must be > 0");
        if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("uuid is blank");

        UUID u = UUID.fromString(uuid.trim());
        long msb = u.getMostSignificantBits();
        long lsb = u.getLeastSignificantBits();
        if (msb == 0L && lsb == 0L) throw new IllegalArgumentException("UUID 0/0 is reserved");

        ensureCapacity(entityId);
        setInternal(entityId, msb, lsb);
    }

    public int entityIdOf(String uuid) {
        if (uuid == null || uuid.isBlank()) return EntityId.NULL;
        UUID u = UUID.fromString(uuid.trim());
        return index.get(u.getMostSignificantBits(), u.getLeastSignificantBits());
    }

    public void reset() {
        msbById = new long[0];
        lsbById = new long[0];
        capacity = 0;
        index.clear();
    }

    public String uuidStringOf(int entityId) {
        if (entityId <= 0 || entityId >= capacity) return "";
        long msb = msbById[entityId];
        long lsb = lsbById[entityId];
        if (msb == 0L && lsb == 0L) return "";
        return new UUID(msb, lsb).toString();
    }

    private void ensureCapacity(int entityId) {
        if (entityId < capacity) return;

        int newCap = nextPow2(entityId + 1);
        if (newCap <= capacity) newCap = entityId + 1;

        long[] nM = new long[newCap];
        long[] nL = new long[newCap];

        if (capacity > 0) {
            System.arraycopy(msbById, 0, nM, 0, Math.min(msbById.length, nM.length));
            System.arraycopy(lsbById, 0, nL, 0, Math.min(lsbById.length, nL.length));
        }

        msbById = nM;
        lsbById = nL;
        capacity = newCap;
    }

    private void setInternal(int entityId, long msb, long lsb) {
        long pM = msbById[entityId];
        long pL = lsbById[entityId];
        if (pM != EMPTY || pL != EMPTY) {
            index.remove(pM, pL);
        }

        int existing = index.get(msb, lsb);
        if (existing != EntityId.NULL && existing != entityId) {
            throw new IllegalStateException("UUID already assigned to entityId=" + existing);
        }

        msbById[entityId] = msb;
        lsbById[entityId] = lsb;
        index.put(msb, lsb, entityId);
    }
}