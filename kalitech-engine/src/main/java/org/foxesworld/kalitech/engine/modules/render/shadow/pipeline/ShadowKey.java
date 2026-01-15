// FILE: org/foxesworld/kalitech/engine/modules/render/shadow/pipeline/ShadowKey.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Typed key used to access shared shadow workspace data.
 *
 * @param <T> value type
 */
public final class ShadowKey<T> {

    private static final AtomicInteger FRAME_IDS = new AtomicInteger(0);
    private static final AtomicInteger SPLIT_IDS = new AtomicInteger(0);
    private final String name;
    private final Class<T> type;
    private final Scope scope;
    private final int index;
    private ShadowKey(String name, Class<T> type, Scope scope, int index) {
        this.name = name;
        this.type = type;
        this.scope = scope;
        this.index = index;
    }

    /**
     * Creates a frame-scope key.
     */
    public static <T> ShadowKey<T> frame(String name, Class<T> type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        return new ShadowKey<>(name, type, Scope.FRAME, FRAME_IDS.getAndIncrement());
    }

    /**
     * Creates a split-scope key.
     */
    public static <T> ShadowKey<T> split(String name, Class<T> type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        return new ShadowKey<>(name, type, Scope.SPLIT, SPLIT_IDS.getAndIncrement());
    }

    public String name() {
        return name;
    }

    public Class<T> type() {
        return type;
    }

    public Scope scope() {
        return scope;
    }

    int index() {
        return index;
    }

    @Override
    public String toString() {
        return "ShadowKey{" + scope + ":" + name + ", idx=" + index + ", type=" + type.getSimpleName() + "}";
    }

    public enum Scope {
        FRAME,
        SPLIT
    }
}