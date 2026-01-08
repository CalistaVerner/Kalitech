package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

@HostAccess.Implementable
public interface ParticlesApi {

    /**
     * Create a particle emitter from JS config.
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
    @HostAccess.Export
    ParticleHandle create(Value cfg);

    @HostAccess.Export
    void destroy(ParticleHandle h);

    @HostAccess.Export
    void setEnabled(ParticleHandle h, boolean enabled);

    @HostAccess.Export
    void play(ParticleHandle h);

    @HostAccess.Export
    void stop(ParticleHandle h);

    void configure(ParticleHandle h, Value cfg);

    // Transform primitives
    @HostAccess.Export
    void setPosition(ParticleHandle h, Value vec3);

    @HostAccess.Export
    void setRotation(ParticleHandle h, Value quat); // {x,y,z,w}

    @HostAccess.Export
    void setScale(ParticleHandle h, double s);

    // Quick burst helper (one-shot)
    @HostAccess.Export
    void emitAll(ParticleHandle h);

    // Optional stats/debug
    @HostAccess.Export
    int alive();

    // Handle is intentionally tiny and stable for JS.
    final class ParticleHandle {
        @HostAccess.Export
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