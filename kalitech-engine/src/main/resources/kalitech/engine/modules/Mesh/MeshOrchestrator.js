"use strict";

const M = require("./helpers/MeshMath.js");
const C = require("./helpers/MeshCfg.js");
const I = require("./helpers/MeshIds.js");

// ------------------------------------------------------------
// utils
// ------------------------------------------------------------

function warpArgs(x, y, z) {
    const n = M.normalizePos(x);
    if (n) return n;
    return [M.num(x, 0), M.num(y, 0), M.num(z, 0)];
}

function hostMethod(target, name) {
    const m = target[name];
    if (typeof m !== "function") return null;
    return function () {
        return m.apply(target, arguments);
    };
}

function cloneCfgShallow(state) {
    const out = Object.assign({}, state);
    if (state.physics) out.physics = Object.assign({}, state.physics);
    return out;
}

// ------------------------------------------------------------
// Orchestrator
// ------------------------------------------------------------

class MeshOrchestrator {

    constructor(ENGINE) {
        if (!ENGINE) throw new Error("[MESH] ENGINE is required");

        this.ENGINE = ENGINE;

        const mesh = ENGINE.mesh();
        if (!mesh) throw new Error("[MESH] ENGINE.mesh() is required");
        if (typeof mesh.create !== "function") throw new Error("[MESH] ENGINE.mesh().create(cfg) is required");

        // strict contracts
        I.requireSurface(ENGINE);
        I.requirePhysics(ENGINE);

        this._mesh = mesh;
        this._decorated = null;
    }

    // ------------------------------------------------------------
    // SurfaceHandle -> object-mesh (object model)
    // ------------------------------------------------------------

    wrapSurface(handle) {
        if (handle && handle.__isMeshWrapper) return handle;

        const ENGINE = this.ENGINE;
        const sid = I.surfaceId(handle);

        let cachedBodyId = 0;
        const bodyId = () => {
            if (cachedBodyId > 0) return cachedBodyId;
            cachedBodyId = I.resolveBodyId(ENGINE, sid);
            return cachedBodyId;
        };

        const proxy = new Proxy(Object.create(null), {
            get(_t, prop) {
                if (prop === "__isMeshWrapper") return true;
                if (prop === "__surface") return handle;
                if (prop === "surfaceId") return () => sid;
                if (prop === "bodyId") return () => bodyId();

                // ---------------- physics (ENGINE.physics ONLY) ----------------

                if (prop === "warp") {
                    return (x, y, z) => {
                        const p = I.requirePhysics(ENGINE);
                        return p.warp(bodyId(), warpArgs(x, y, z));
                    };
                }

                if (prop === "position") {
                    return (v) => {
                        const p = I.requirePhysics(ENGINE);
                        if (arguments.length === 0) return p.position(bodyId());
                        return p.warp(bodyId(), v);
                    };
                }

                if (prop === "velocity") {
                    return (v) => {
                        const p = I.requirePhysics(ENGINE);
                        if (arguments.length === 0) return p.velocity(bodyId());
                        return p.velocity(bodyId(), v);
                    };
                }

                if (prop === "yaw") {
                    return (yawRad) => {
                        const p = I.requirePhysics(ENGINE);
                        return p.yaw(bodyId(), +yawRad);
                    };
                }

                if (prop === "applyImpulse") {
                    return (v3) => {
                        const p = I.requirePhysics(ENGINE);
                        return p.applyImpulse(bodyId(), v3);
                    };
                }

                // optional passthroughs (если есть в ENGINE.physics())
                if (prop === "applyCentralForce") {
                    return (v3) => {
                        const p = I.requirePhysics(ENGINE);
                        return p.applyCentralForce(bodyId(), v3);
                    };
                }

                if (prop === "applyTorque") {
                    return (v3) => {
                        const p = I.requirePhysics(ENGINE);
                        return p.applyTorque(bodyId(), v3);
                    };
                }

                if (prop === "angularVelocity") {
                    return (v3) => {
                        const p = I.requirePhysics(ENGINE);
                        if (arguments.length === 0) return p.angularVelocity(bodyId());
                        return p.angularVelocity(bodyId(), v3);
                    };
                }

                if (prop === "clearForces") {
                    return () => {
                        const p = I.requirePhysics(ENGINE);
                        return p.clearForces(bodyId());
                    };
                }

                if (prop === "collisionGroups") {
                    return (group, mask) => {
                        const p = I.requirePhysics(ENGINE);
                        return p.collisionGroups(bodyId(), group | 0, mask | 0);
                    };
                }

                if (prop === "lockRotation") {
                    return (lock) => {
                        const p = I.requirePhysics(ENGINE);
                        return p.lockRotation(bodyId(), !!lock);
                    };
                }

                if (prop === "setKinematic") {
                    return (k) => {
                        const p = I.requirePhysics(ENGINE);
                        return p.setKinematic(bodyId(), !!k);
                    };
                }

                // ---------------- surface flags ----------------

                if (prop === "setVisible") {
                    return (v) => {
                        const s = I.requireSurface(ENGINE);
                        return s.setVisible(sid, !!v);
                    };
                }

                if (prop === "setCull") {
                    return (hint) => {
                        const s = I.requireSurface(ENGINE);
                        return s.setCull(sid, String(hint));
                    };
                }

                // ---------------- passthrough to SurfaceHandle ----------------

                const v = handle[prop];
                if (typeof v === "function") {
                    return v.bind ? v.bind(handle) : function () {
                        return v.apply(handle, arguments);
                    };
                }
                return v;
            }
        });

        return proxy;
    }

    // ------------------------------------------------------------
    // Decorate ENGINE.mesh() via Proxy (safe for JavaObject)
    // ------------------------------------------------------------

    decorateMeshApi() {
        if (this._decorated) return this._decorated;

        const orch = this;
        const mesh = this._mesh;

        const decorated = new Proxy(mesh, {
            get(target, prop) {

                // ---------------- create() ----------------
                if (prop === "create") {
                    const createFn = hostMethod(target, "create") || (typeof target.create === "function" ? target.create.bind(target) : null);
                    if (!createFn) throw new Error("[MESH] ENGINE.mesh().create(cfg) is required");

                    return (cfg) => {
                        const c = C.normalizeCfg(cfg);
                        const h = createFn(c);
                        return orch.wrapSurface(h);
                    };
                }

                // ---------------- loadModel(path,cfg?) / loadModel(cfg) ----------------
                if (prop === "loadModel") {
                    return (pathOrCfg, cfg) => {
                        let c;
                        if (typeof pathOrCfg === "string") {
                            c = C.normalizeCfg(cfg);
                            c.type = "model";
                            c.path = String(pathOrCfg);
                        } else {
                            c = C.normalizeCfg(pathOrCfg);
                            c.type = "model";
                        }
                        if (!c.path || String(c.path).trim() === "") {
                            throw new Error("[MESH] loadModel: path is required");
                        }
                        return decorated.create(c);
                    };
                }

                // ---------------- many() ----------------
                if (prop === "many" && typeof target.many === "function") {
                    const manyFn = hostMethod(target, "many") || target.many.bind(target);
                    return (list) => {
                        const arr = manyFn(list);
                        if (!Array.isArray(arr)) return arr;
                        for (let i = 0; i < arr.length; i++) arr[i] = orch.wrapSurface(arr[i]);
                        return arr;
                    };
                }

                // ---------------- DSL / builder ----------------
                if (prop === "builder") {
                    return (type) => {
                        const state = C.normalizeCfg({type: String(type)});

                        const b = {
                            /**
                             * Унифицированный размер.
                             * - sphere: size(v) -> cfg.radius = v
                             * - остальные: size(v) -> cfg.size = v
                             */
                            size(v) {
                                const n = M.num(v, (state.type === "sphere" ? state.radius : state.size));
                                if (state.type === "sphere") state.radius = n;
                                else state.size = n;
                                return b;
                            },

                            name(v) {
                                state.name = String(v);
                                return b;
                            },

                            pos(x, y, z) {
                                if (Array.isArray(x) || M.isObj(x)) state.pos = M.normalizePos(x);
                                else state.pos = [M.num(x, 0), M.num(y, 0), M.num(z, 0)];
                                return b;
                            },

                            material(m) {
                                state.material = m;
                                return b;
                            },

                            path(v) {
                                state.path = String(v);
                                return b;
                            },

                            model(v) {
                                state.path = String(v);
                                return b;
                            },

                            physics(mass, opts) {
                                const o = opts || {};
                                const p = {mass: (mass != null ? mass : 0)};
                                if (o.enabled != null) p.enabled = !!o.enabled;
                                if (o.lockRotation != null) p.lockRotation = !!o.lockRotation;
                                if (o.kinematic != null) p.kinematic = !!o.kinematic;
                                if (o.friction != null) p.friction = o.friction;
                                if (o.restitution != null) p.restitution = o.restitution;
                                if (o.damping != null) p.damping = o.damping;
                                if (o.collider != null) p.collider = o.collider;
                                state.physics = p;
                                return b;
                            },

                            cfg() {
                                return cloneCfgShallow(state);
                            },

                            create() {
                                const out = cloneCfgShallow(state);

                                // FIX: sphere + physics enabled + collider missing -> auto collider.radius
                                if (out.type === "sphere" && out.physics && out.physics.enabled !== false && !out.physics.collider) {
                                    const r = M.num(out.radius, M.num(out.size, 1.0));
                                    out.physics.collider = {type: "sphere", radius: r};
                                }

                                return decorated.create(out);
                            }
                        };

                        return b;
                    };
                }

                if (prop === "box$") return () => decorated.builder("box");
                if (prop === "cube$") return () => decorated.builder("box");
                if (prop === "sphere$") return () => decorated.builder("sphere");
                if (prop === "cylinder$") return () => decorated.builder("cylinder");
                if (prop === "capsule$") return () => decorated.builder("capsule");
                if (prop === "model$") return () => decorated.builder("model");

                // ---------------- default passthrough ----------------
                const v = target[prop];
                if (typeof v === "function") {
                    const fn = hostMethod(target, prop);
                    return fn || v;
                }
                return v;
            }
        });

        this._decorated = decorated;
        return decorated;
    }
}

module.exports = MeshOrchestrator;