/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 */
package org.foxesworld.kalitech.engine.modules.camera;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;

public final class Vec3View {
    private volatile double x;
    private volatile double y;
    private volatile double z;

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @LuaExport
    public double x() {
        return this.x;
    }

    @LuaExport
    public double y() {
        return this.y;
    }

    @LuaExport
    public double z() {
        return this.z;
    }
}

