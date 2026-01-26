package org.foxesworld.kalitech.engine.api.types;

import org.graalvm.polyglot.HostAccess;

public final class MaterialHandle {
    private final int id;

    public MaterialHandle(int id) {
        this.id = id;
    }

    @HostAccess.Export
    public int id() {
        return id;
    }

    @Override
    public String toString() {
        return "MaterialHandle(" + id + ")";
    }
}