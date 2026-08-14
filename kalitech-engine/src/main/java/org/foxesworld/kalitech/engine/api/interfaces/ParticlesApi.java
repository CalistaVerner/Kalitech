package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface ParticlesApi {

    /**
     * Create a particle emitter from Lua config.
     * <p>
     * Expected cfg fields (all optional):
     * - name: string
     * - type: "triangle" | "point" (default: triangle)
     * - max: number (default: 256)
     * - texture: string (asset path)
     * - spriteRows/spriteCols: number
     * - size: {start,end}
     * - life: {min,max}
     * - rate: number (particles/sec)
     * - gravity: {x,y,z}
     * - velocity: {min,max}  (speed scalar)
     * - color: {start:{r,g,b,a}, end:{r,g,b,a}}
     * - local: boolean (default true)
     * - enabled: boolean (default true)
     */
    @LuaExport
    ParticleHandle create(LuaValueRef cfg);

    @LuaExport
    void destroy(ParticleHandle h);

    @LuaExport
    void setEnabled(ParticleHandle h, boolean enabled);

    @LuaExport
    void play(ParticleHandle h);

    @LuaExport
    void stop(ParticleHandle h);

    void configure(ParticleHandle h, LuaValueRef cfg);

    // Transform primitives
    @LuaExport
    void setPosition(ParticleHandle h, LuaValueRef vec3);

    @LuaExport
    void setRotation(ParticleHandle h, LuaValueRef quat); // {x,y,z,w}

    @LuaExport
    void setScale(ParticleHandle h, double s);

    // Quick burst helper (one-shot)
    @LuaExport
    void emitAll(ParticleHandle h);

    // Optional stats/debug
    @LuaExport
    int alive();

    // Handle is intentionally tiny and stable for Lua.
    final class ParticleHandle {
        @LuaExport
        public final int id;

        public ParticleHandle(int id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "ParticleHandle(" + id + ")";
        }
    }
}