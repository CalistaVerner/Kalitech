// FILE: org/foxesworld/kalitech/engine/modules/particles/core/FxInstance.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles.core;

import com.jme3.effect.ParticleEmitter;
import com.jme3.scene.Node;
import org.foxesworld.kalitech.engine.modules.particles.pool.EmitterPool;
import org.foxesworld.kalitech.engine.modules.particles.pool.PooledEmitter;
import org.foxesworld.kalitech.engine.modules.particles.timeline.FxTimeline;

/**
 * Runtime FX instance.
 * Can represent a single emitter or a group later.
 */
public final class FxInstance {

    public final int id;
    public final String templateName;
    public final Node root;
    public final ParticleEmitter emitter;

    public FxTimeline timeline = FxTimeline.empty();
    public long seed = 0L;

    public EmitterPool pool;
    public PooledEmitter pooled;

    public boolean alive = true;

    public FxInstance(int id, String templateName, Node root, ParticleEmitter emitter) {
        this.id = id;
        this.templateName = templateName;
        this.root = root;
        this.emitter = emitter;
    }
}