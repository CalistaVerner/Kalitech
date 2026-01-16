// FILE: org/foxesworld/kalitech/engine/modules/physics/shapes/ShapeCache.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.shapes;

import com.jme3.bullet.collision.shapes.CollisionShape;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Thread-safe cache for collision shapes.
 */
public final class ShapeCache {

    private final ConcurrentHashMap<ShapeKey, CollisionShape> cache;

    public ShapeCache(int initialCapacity) {
        this.cache = new ConcurrentHashMap<>(Math.max(16, initialCapacity));
    }

    public CollisionShape getOrCompute(ShapeKey key, Supplier<CollisionShape> factory) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(factory, "factory");
        return cache.computeIfAbsent(key, k -> Objects.requireNonNull(factory.get(), "factory.get()"));
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}