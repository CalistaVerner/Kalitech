// FILE: org/foxesworld/kalitech/engine/modules/physics/PhysicsContacts.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.physics;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import org.foxesworld.kalitech.engine.api.interfaces.physics.PhysicsBodyHandle;
import org.foxesworld.kalitech.engine.util.LongHashSet;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Map;

import static org.foxesworld.kalitech.engine.modules.physics.CollisionPairKey.*;
import static org.foxesworld.kalitech.engine.modules.physics.PhysicsJs.*;

/**
 * Collision + contact pipeline (physics-thread only).
 *
 * Responsibilities:
 *  - drain pending add/remove with a budget in prePhysicsTick
 *  - aggregate contact information per body-pair per physics step
 *  - emit begin/stay/end and postStep events
 *  - emit body lifecycle telemetry: added-to-space, wake/sleep, move
 *  - emit high-level impact events (thresholded)
 *
 * Contract (legacy-safe):
 *  - every event emitted from this class includes a top-level "pos" as a JS object {x,y,z}
 */
public final class PhysicsContacts {

    // Move telemetry thresholds (world units / radians-ish).
    private static final float MOVE_POS_EPS2 = 0.0004f; // 2cm^2
    private static final float MOVE_ROT_DOT_EPS = 1.0f - 1.0e-4f;

    // Impact thresholds.
    private static final float IMPACT_MIN_IMPULSE = 1.25f;
    private static final float IMPACT_MIN_REL_SPEED = 1.5f;
    private static final float IMPACT_MIN_ENERGY = 0.35f;

    private static final Vector3f ZERO = new Vector3f(0f, 0f, 0f);
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);

    private final PhysicsState S;

    private final LongHashSet currKeys = new LongHashSet(4096);
    private final LongHashSet prevKeys = new LongHashSet(4096);
    // Temp objects (physics thread only).
    private final Vector3f tmpPoint = new Vector3f();
    private final Vector3f tmpNormal = new Vector3f();
    private final Vector3f tmpDelta = new Vector3f();
    private final Quaternion tmpRot = new Quaternion();
    private LongContactMap currAgg = new LongContactMap(4096);
    private LongContactMap prevAgg = new LongContactMap(4096);

    public PhysicsContacts(PhysicsState state) {
        this.S = state;
    }

    private static Vector3f snap(Vector3f v) {
        if (v == null) return ZERO;
        return new Vector3f(v);
    }

    private static ProxyObject jsPosProxy(Vector3f v) {
        // Must never be null; ZERO is safe and immutable in our code.
        return jsVec3Live(v == null ? ZERO : v);
    }

    private static ProxyObject jsPosProxySnap(Vector3f v) {
        return jsPosProxy(snap(v));
    }

    private static ProxyObject jsPosFromRb(RigidBodyControl rb) {
        try {
            Vector3f p = (rb != null) ? rb.getPhysicsLocation() : null;
            return jsPosProxySnap(p);
        } catch (Throwable ignored) {
            return jsPosProxy(ZERO);
        }
    }

    private static Vector3f contactPointVec(ContactAgg agg) {
        int pts = Math.max(1, agg.points);
        float inv = 1f / (float) pts;
        float px = agg.sumPx * inv;
        float py = agg.sumPy * inv;
        float pz = agg.sumPz * inv;
        return new Vector3f(px, py, pz);
    }

    private static Vector3f contactNormalVec(ContactAgg agg) {
        int pts = Math.max(1, agg.points);
        float inv = 1f / (float) pts;

        float nx = agg.sumNx * inv;
        float ny = agg.sumNy * inv;
        float nz = agg.sumNz * inv;

        float l2 = nx * nx + ny * ny + nz * nz;
        if (l2 > 1e-12f) {
            float invL = (float) (1.0 / Math.sqrt(l2));
            nx *= invL;
            ny *= invL;
            nz *= invL;
        } else {
            nx = UP.x;
            ny = UP.y;
            nz = UP.z;
        }

        return new Vector3f(nx, ny, nz);
    }

    private static Map<String, Object> contactPayload(ContactAgg agg, Vector3f p, Vector3f n) {
        return PhysicsState.evt(
                "points", agg.points,
                "maxImpulse", agg.maxImpulse,
                "point", jsVec3Live(p),
                "normal", jsVec3Live(n)
        );
    }

    void ensureBound(PhysicsSpace space) {
        if (space == null) return;

        if (S.collisionListenerBound.compareAndSet(false, true)) {
            space.addCollisionListener(new PhysicsCollisionListener() {
                @Override
                public void collision(PhysicsCollisionEvent e) {
                    if (e == null) return;

                    int a = S.bodyIdFromCollisionObject(e.getObjectA());
                    int b = S.bodyIdFromCollisionObject(e.getObjectB());
                    long k = pairKey(a, b);
                    if (k == 0L) return;

                    currKeys.add(k);

                    ContactAgg agg = currAgg.getOrCreate(k);
                    if (agg == null) return;

                    float impulse = 0f;
                    try {
                        impulse = e.getAppliedImpulse();
                    } catch (Throwable ignored) {
                    }

                    Vector3f pA = null;
                    Vector3f pB = null;
                    Vector3f nB = null;
                    try {
                        pA = e.getPositionWorldOnA();
                    } catch (Throwable ignored) {
                    }
                    try {
                        pB = e.getPositionWorldOnB();
                    } catch (Throwable ignored) {
                    }
                    try {
                        nB = e.getNormalWorldOnB();
                    } catch (Throwable ignored) {
                    }

                    Vector3f p = null;
                    if (pA != null && pB != null) {
                        tmpPoint.set((pA.x + pB.x) * 0.5f, (pA.y + pB.y) * 0.5f, (pA.z + pB.z) * 0.5f);
                        p = tmpPoint;
                    } else if (pA != null) {
                        p = pA;
                    } else if (pB != null) {
                        p = pB;
                    }

                    Vector3f n = null;
                    if (nB != null) {
                        tmpNormal.set(nB);
                        n = tmpNormal;
                    }

                    agg.add(impulse, p, n);
                }
            });
        }

        if (S.tickListenerBound.compareAndSet(false, true)) {
            space.addTickListener(new PhysicsTickListener() {
                @Override
                public void prePhysicsTick(PhysicsSpace space, float timeStep) {
                    drainPending(space);

                    // start new step
                    currKeys.clear();
                    currAgg.clear();
                }

                @Override
                public void physicsTick(PhysicsSpace space, float timeStep) {
                    long step = S.physicsStepCounter.incrementAndGet();

                    emitCollisions(step, timeStep);
                    emitBodyTelemetry(step, timeStep);

                    swapStep();

                    // Keep contract: include pos always (use origin).
                    S.bus().emit(PhysicsEvents.POST_STEP, PhysicsState.evt(
                            "step", step,
                            "dt", timeStep,
                            "pos", jsPosProxy(ZERO)
                    ));
                }
            });
        }
    }

    private void emitCollisions(long step, float dt) {
        // begin + stay
        currKeys.forEach((LongHashSet.LongConsumer) k -> {
            ContactAgg agg = currAgg.get(k);
            if (agg == null || agg.points <= 0) return;

            boolean begin = !prevKeys.contains(k);
            if (begin) {
                emitCollision(PhysicsEvents.COLL_BEGIN, step, dt, k, agg);
                if (shouldEmitImpact(k, agg)) {
                    emitCollision(PhysicsEvents.IMPACT, step, dt, k, agg);
                }
            }
            emitCollision(PhysicsEvents.COLL_STAY, step, dt, k, agg);
        });

        // end
        prevKeys.forEach((LongHashSet.LongConsumer) k -> {
            if (currKeys.contains(k)) return;
            ContactAgg agg = prevAgg.get(k);
            if (agg == null || agg.points <= 0) return;
            emitCollision(PhysicsEvents.COLL_END, step, dt, k, agg);
        });
    }

    private void emitBodyTelemetry(long step, float dt) {
        for (PhysicsBodyHandle h : S.byId.values()) {
            if (h == null) continue;

            RigidBodyControl rb;
            try {
                rb = h.__raw();
            } catch (Throwable t) {
                continue;
            }
            if (rb == null) continue;

            BodyState st = S.bodyState.computeIfAbsent(h.id, k -> new BodyState());

            Vector3f p;
            Quaternion q;
            Vector3f lv;
            Vector3f av;
            boolean active;
            try {
                p = rb.getPhysicsLocation();
            } catch (Throwable t) {
                p = null;
            }
            try {
                q = rb.getPhysicsRotation();
            } catch (Throwable t) {
                q = null;
            }
            try {
                lv = rb.getLinearVelocity();
            } catch (Throwable t) {
                lv = null;
            }
            try {
                av = rb.getAngularVelocity();
            } catch (Throwable t) {
                av = null;
            }
            try {
                active = rb.isActive();
            } catch (Throwable t) {
                active = true;
            }

            if (!st.init) {
                if (p != null) st.pos.set(p);
                if (q != null) st.rot.set(q);
                if (lv != null) st.linVel.set(lv);
                if (av != null) st.angVel.set(av);
                st.active = active;
                st.init = true;
                continue;
            }

            // wake/sleep
            if (active != st.active) {
                st.active = active;
                S.bus().emit("e" +
                                "ngine.physics.body.move",
                        evtJs(
                                "step", step,
                                "dt", dt,
                                "bodyId", h.id,
                                "surfaceId", h.surfaceId,
                                "entity", entityOfSurface(h.surfaceId),
                                "pos", jsVec3(p),
                                "rot", (q == null ? null : jsQuat(q)),
                                "vel", (lv == null ? null : jsVec3(lv)),
                                "angVel", (av == null ? null : jsVec3(av)),
                                "active", active));

            }

            boolean moved = false;
            if (p != null) {
                tmpDelta.set(p).subtractLocal(st.pos);
                if (tmpDelta.lengthSquared() >= MOVE_POS_EPS2) moved = true;
            }
            if (!moved && q != null) {
                float dot = Math.abs(st.rot.dot(q));
                if (dot < MOVE_ROT_DOT_EPS) moved = true;
            }

            if (moved) {
                Map<String, Object> payload = PhysicsState.evt(
                        "step", step,
                        "dt", dt,
                        "pos", jsVec3(p),
                        "body", PhysicsEventPayloads.bodySnapshot(S, h)
                );

                // Keep old fields, but expose them as JS live objects too (snapshotted).
                if (p != null) payload.put("deltaPos", jsPosProxySnap(tmpDelta));
                if (q != null) payload.put("rot", PhysicsEventPayloads.quat(q));
                if (lv != null) payload.put("vel", jsPosProxySnap(lv));
                if (av != null) payload.put("angVel", jsPosProxySnap(av));

                S.bus().emit(PhysicsEvents.BODY_MOVE, payload);
            }

            if (p != null) st.pos.set(p);
            if (q != null) st.rot.set(q);
            if (lv != null) st.linVel.set(lv);
            if (av != null) st.angVel.set(av);
        }
    }

    /**
     * Resolve entity identifier for surface.
     * Looks for common userData keys on Spatial.
     */
    private String entityOfSurface(int surfaceId) {
        if (surfaceId <= 0) return null;
        Spatial sp = this.S.surfaces.get(surfaceId);
        if (sp == null) return null;

        try {
            Object v = sp.getUserData("entityUuid");
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }

        try {
            Object v = sp.getUserData("entityId");
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }

        try {
            Object v = sp.getUserData("uuid");
            if (v != null) return String.valueOf(v);
        } catch (Throwable ignored) {
        }

        return null;
    }

    private boolean shouldEmitImpact(long k, ContactAgg agg) {
        if (agg == null) return false;
        if (!(Float.isFinite(agg.maxImpulse) && agg.maxImpulse >= IMPACT_MIN_IMPULSE)) return false;

        int aId = keyA(k);
        int bId = keyB(k);
        PhysicsBodyHandle a = S.byId.get(aId);
        PhysicsBodyHandle b = S.byId.get(bId);
        if (a == null || b == null) return false;

        RigidBodyControl ra = a.__raw();
        RigidBodyControl rb = b.__raw();

        Spatial sa = null;
        Spatial sb = null;
        try {
            sa = S.surfaces.get(a.surfaceId);
        } catch (Throwable ignored) {
        }
        try {
            sb = S.surfaces.get(b.surfaceId);
        } catch (Throwable ignored) {
        }

        boolean hard = PhysicsEventPayloads.isHardSurface(ra, sa) || PhysicsEventPayloads.isHardSurface(rb, sb);
        if (!hard) return false;

        float relSpeed = PhysicsEventPayloads.relativeSpeedApprox(ra, rb);
        if (!(Float.isFinite(relSpeed) && relSpeed >= IMPACT_MIN_REL_SPEED)) return false;

        float rm = PhysicsEventPayloads.reducedMassSafe(
                PhysicsEventPayloads.massSafe(ra),
                PhysicsEventPayloads.massSafe(rb)
        );
        float energy = 0.5f * rm * relSpeed * relSpeed;
        return Float.isFinite(energy) && energy >= IMPACT_MIN_ENERGY;
    }

    private void emitCollision(String topic, long step, float dt, long key, ContactAgg agg) {
        int aId = keyA(key);
        int bId = keyB(key);

        PhysicsBodyHandle a = S.byId.get(aId);
        PhysicsBodyHandle b = S.byId.get(bId);
        if (a == null || b == null) return;

        RigidBodyControl ra;
        RigidBodyControl rb;
        try {
            ra = a.__raw();
            rb = b.__raw();
        } catch (Throwable t) {
            return;
        }

        float relSpeed = PhysicsEventPayloads.relativeSpeedApprox(ra, rb);
        float rm = PhysicsEventPayloads.reducedMassSafe(
                PhysicsEventPayloads.massSafe(ra),
                PhysicsEventPayloads.massSafe(rb)
        );
        float energy = 0.5f * rm * relSpeed * relSpeed;

        Vector3f p = contactPointVec(agg);
        Vector3f n = contactNormalVec(agg);

        S.bus().emit(topic, PhysicsState.evt(
                "step", step,
                "dt", dt,
                "pair", PhysicsState.evt("key", key, "aId", aId, "bId", bId),
                "pos", jsVec3(p),
                "normal", jsPosProxy(n),
                "a", PhysicsEventPayloads.bodySnapshot(S, a),
                "b", PhysicsEventPayloads.bodySnapshot(S, b),
                "contact", contactPayload(agg, p, n),
                "rel", PhysicsState.evt(
                        "speed", relSpeed,
                        "reducedMass", rm,
                        "energy", energy
                )
        ));
    }

    private void drainPending(PhysicsSpace space) {
        int addBudget = (PhysicsState.FLUSH_MAX_PER_TICK > 0) ? PhysicsState.FLUSH_MAX_PER_TICK : 256;
        while (addBudget-- > 0) {
            RigidBodyControl rb = S.pendingAdd.poll();
            if (rb == null) break;
            try {
                space.add(rb);

                Integer id = S.idByControl.get(rb);
                if (id != null && id > 0) {
                    PhysicsBodyHandle h = S.byId.get(id);
                    if (h != null) {
                        S.indexCollisionObject(h);

                        S.bus().emit(PhysicsEvents.BODY_ADDED, PhysicsState.evt(
                                "body", PhysicsEventPayloads.bodySnapshot(S, h),
                                "pos", jsVec3(rb.getPhysicsLocation())
                        ));
                    }
                }
            } catch (Throwable t) {
                PhysicsState.log.error("[physics] space.add failed", t);
            }
        }

        int remBudget = (PhysicsState.FLUSH_MAX_PER_TICK > 0) ? PhysicsState.FLUSH_MAX_PER_TICK : 256;
        while (remBudget-- > 0) {
            RigidBodyControl rb = S.pendingRemove.poll();
            if (rb == null) break;
            try {
                space.remove(rb);
            } catch (Throwable t) {
                PhysicsState.log.error("[physics] space.remove failed", t);
            }
        }
    }

    void swapStep() {
        currKeys.swapWith(prevKeys);

        LongContactMap t = currAgg;
        currAgg = prevAgg;
        prevAgg = t;
    }
}

