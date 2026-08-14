/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector3f
 *  com.jme3.scene.Spatial
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.api.EngineApiImpl
 *  org.foxesworld.kalitech.engine.api.services.SurfaceRegistry
 */
package org.foxesworld.kalitech.engine.modules.physics.runtime.service;

import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import java.util.Objects;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;
import org.foxesworld.kalitech.engine.modules.physics.PhysicsLua;
import org.foxesworld.kalitech.engine.modules.physics.runtime.service.PhysicsEventSink;

final class EnginePhysicsEventSink
implements PhysicsEventSink {
    private final EngineApiImpl engine;
    private final Logger log;

    EnginePhysicsEventSink(EngineApiImpl engine, Logger log) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void emitBodyCreated(PhysicsBodyHandle h, float mass, boolean kinematic, boolean lockRotation, String entity) {
        try {
            this.engine.getBus().emit("engine.physics.body.create", (Object)PhysicsLua.evtLua("bodyId", h.id, "surfaceId", h.surfaceId, "entity", entity, "mass", Float.valueOf(mass), "kinematic", kinematic, "lockRotation", lockRotation));
        }
        catch (Throwable t) {
            this.log.debug("[physics] emitBodyCreated failed bodyId={}", (Object)h.id, (Object)t);
        }
    }

    @Override
    public void emitBodyRemoved(PhysicsBodyHandle h, String entity) {
        try {
            this.engine.getBus().emit("engine.physics.body.remove", (Object)PhysicsLua.evtLua("bodyId", h.id, "surfaceId", h.surfaceId, "entity", entity));
        }
        catch (Throwable t) {
            this.log.debug("[physics] emitBodyRemoved failed bodyId={}", (Object)h.id, (Object)t);
        }
    }

    @Override
    public void emitBodyAdded(PhysicsBodyHandle h, SurfaceRegistry surfaces, String entity) {
        Spatial sp;
        Vector3f p = null;
        if (surfaces != null && (sp = surfaces.get(h.surfaceId)) != null) {
            p = sp.getWorldTranslation();
        }
        try {
            this.engine.getBus().emit("engine.physics.body.added", (Object)PhysicsLua.evtLua("bodyId", h.id, "surfaceId", h.surfaceId, "entity", entity, "pos", p == null ? null : PhysicsLua.luaVec3Live(p)));
        }
        catch (Throwable t) {
            this.log.debug("[physics] emitBodyAdded failed bodyId={}", (Object)h.id, (Object)t);
        }
    }
}

