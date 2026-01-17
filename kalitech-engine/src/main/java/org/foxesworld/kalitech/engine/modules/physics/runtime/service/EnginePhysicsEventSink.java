// FILE: org/foxesworld/kalitech/engine/modules/physics/runtime/EnginePhysicsEventSink.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics.runtime.service;

import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.api.services.SurfaceRegistry;

import java.util.Objects;

import static org.foxesworld.kalitech.engine.modules.physics.PhysicsJs.evtJs;
import static org.foxesworld.kalitech.engine.modules.physics.PhysicsJs.jsVec3Live;

final class EnginePhysicsEventSink implements PhysicsEventSink {

    private final EngineApiImpl engine;
    private final Logger log;

    EnginePhysicsEventSink(EngineApiImpl engine, Logger log) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void emitBodyCreated(PhysicsBodyHandle h, float mass, boolean kinematic, boolean lockRotation, String entity) {
        try {
            engine.getBus().emit("engine.physics.body.create", evtJs(
                    "bodyId", h.id,
                    "surfaceId", h.surfaceId,
                    "entity", entity,
                    "mass", mass,
                    "kinematic", kinematic,
                    "lockRotation", lockRotation
            ));
        } catch (Throwable t) {
            log.debug("[physics] emitBodyCreated failed bodyId={}", h.id, t);
        }
    }

    @Override
    public void emitBodyRemoved(PhysicsBodyHandle h, String entity) {
        try {
            engine.getBus().emit("engine.physics.body.remove", evtJs(
                    "bodyId", h.id,
                    "surfaceId", h.surfaceId,
                    "entity", entity
            ));
        } catch (Throwable t) {
            log.debug("[physics] emitBodyRemoved failed bodyId={}", h.id, t);
        }
    }

    @Override
    public void emitBodyAdded(PhysicsBodyHandle h, SurfaceRegistry surfaces, String entity) {
        Vector3f p = null;
        if (surfaces != null) {
            Spatial sp = surfaces.get(h.surfaceId);
            if (sp != null) p = sp.getWorldTranslation();
        }

        try {
            engine.getBus().emit("engine.physics.body.added", evtJs(
                    "bodyId", h.id,
                    "surfaceId", h.surfaceId,
                    "entity", entity,
                    "pos", p == null ? null : jsVec3Live(p)
            ));
        } catch (Throwable t) {
            log.debug("[physics] emitBodyAdded failed bodyId={}", h.id, t);
        }
    }
}