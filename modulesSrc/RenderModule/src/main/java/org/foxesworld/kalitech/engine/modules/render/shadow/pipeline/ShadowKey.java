/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.render.shadow.pipeline;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

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

    public static <T> ShadowKey<T> frame(String name, Class<T> type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        return new ShadowKey<T>(name, type, Scope.FRAME, FRAME_IDS.getAndIncrement());
    }

    public static <T> ShadowKey<T> split(String name, Class<T> type) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        return new ShadowKey<T>(name, type, Scope.SPLIT, SPLIT_IDS.getAndIncrement());
    }

    public String name() {
        return this.name;
    }

    public Class<T> type() {
        return this.type;
    }

    public Scope scope() {
        return this.scope;
    }

    int index() {
        return this.index;
    }

    public String toString() {
        return "ShadowKey{" + String.valueOf((Object)this.scope) + ":" + this.name + ", idx=" + this.index + ", type=" + this.type.getSimpleName() + "}";
    }

    public static enum Scope {
        FRAME,
        SPLIT;

    }
}

