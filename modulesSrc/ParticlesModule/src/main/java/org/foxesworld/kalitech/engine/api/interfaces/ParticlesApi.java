/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport$Implementable
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface ParticlesApi {
    @LuaExport
    public ParticleHandle create(LuaValueRef var1);

    @LuaExport
    public void destroy(ParticleHandle var1);

    @LuaExport
    public void setEnabled(ParticleHandle var1, boolean var2);

    @LuaExport
    public void play(ParticleHandle var1);

    @LuaExport
    public void stop(ParticleHandle var1);

    public void configure(ParticleHandle var1, LuaValueRef var2);

    @LuaExport
    public void setPosition(ParticleHandle var1, LuaValueRef var2);

    @LuaExport
    public void setRotation(ParticleHandle var1, LuaValueRef var2);

    @LuaExport
    public void setScale(ParticleHandle var1, double var2);

    @LuaExport
    public void emitAll(ParticleHandle var1);

    @LuaExport
    public int alive();

    public static final class ParticleHandle {
        @LuaExport
        public final int id;

        public ParticleHandle(int id) {
            this.id = id;
        }

        public String toString() {
            return "ParticleHandle(" + this.id + ")";
        }
    }
}

