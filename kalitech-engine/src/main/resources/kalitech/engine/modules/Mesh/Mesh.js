// FILE: resources/kalitech/builtin/Primitives.js
// Author: Calista Verner
"use strict";

function isObj(v) {
    return v !== null && typeof v === "object" && !Array.isArray(v);
}

function num(v, fb) {
    const n = +v;
    return Number.isFinite(n) ? n : (fb || 0);
}

function normalizePos(p) {
    if (Array.isArray(p)) return [num(p[0], 0), num(p[1], 0), num(p[2], 0)];
    if (isObj(p)) {
        const x = (p.x != null) ? p.x : p[0];
        const y = (p.y != null) ? p.y : p[1];
        const z = (p.z != null) ? p.z : p[2];
        return [num(x, 0), num(y, 0), num(z, 0)];
    }
    return undefined;
}

function normalizeCfg(cfg) {
    cfg = isObj(cfg) ? cfg : {};
    const out = Object.assign({}, cfg);

    if (out.type != null) out.type = String(out.type);
    if (out.name != null) out.name = String(out.name);

    if (out.path == null) {
        if (out.model != null) out.path = out.model;
        else if (out.asset != null) out.path = out.asset;
        else if (out.url != null) out.path = out.url;
    }
    if (out.path != null) out.path = String(out.path);

    const p =
        (out.pos != null) ? out.pos :
            (out.position != null) ? out.position :
                (out.loc != null) ? out.loc :
                    (out.location != null) ? out.location :
                        undefined;

    const posN = normalizePos(p);
    if (posN !== undefined) out.pos = posN;

    if (out.radius == null && out.r != null) out.radius = out.r;
    if (out.height == null && out.h != null) out.height = out.h;

    if (out.radius != null) out.radius = num(out.radius, out.radius);
    if (out.height != null) out.height = num(out.height, out.height);

    // physics конфиг — оставляем как есть (без “legacy top-level” магии)
    if (out.physics != null && typeof out.physics === "number") out.physics = {mass: out.physics};

    return out;
}

function withType(type, cfg) {
    const c = normalizeCfg(cfg);
    c.type = String(type);
    return c;
}

function unshadedColor(rgba) {
    const c = Array.isArray(rgba) ? rgba : [1, 1, 1, 1];
    return {def: "Common/MatDefs/Misc/Unshaded.j3md", params: {Color: c}};
}

function physics(mass, opts) {
    const o = opts || {};
    const p = {mass: (mass != null ? mass : 0)};
    if (o.enabled != null) p.enabled = !!o.enabled;
    if (o.lockRotation != null) p.lockRotation = !!o.lockRotation;
    if (o.kinematic != null) p.kinematic = !!o.kinematic;
    if (o.friction != null) p.friction = o.friction;
    if (o.restitution != null) p.restitution = o.restitution;
    if (o.damping != null) p.damping = o.damping;
    if (o.collider != null) p.collider = o.collider;
    return p;
}

// ------------------- строгие предположения -------------------
// 1) SurfaceHandle имеет метод id():int (без вариантов)
// 2) engine.surface().attachedBody(surfaceId)->int
// 3) engine.physics() содержит операции только по bodyId

function surfaceId(handle) {
    if (!handle || typeof handle.id !== "function") throw new Error("[MSH] SurfaceHandle must provide id()");
    const sid = handle.id() | 0;
    if (sid <= 0) throw new Error("[MSH] invalid surfaceId=" + sid);
    return sid;
}

function resolveBodyId(engine, handle) {
    const s = engine.surface();
    if (!s || typeof s.attachedBody !== "function") {
        throw new Error("[MSH] engine.surface().attachedBody(surfaceId) is required");
    }
    const bid = s.attachedBody(surfaceId(handle)) | 0;
    if (bid <= 0) throw new Error("[MSH] surface has no physics body (bodyId=0)");
    return bid;
}

function requirePhysics(engine) {
    const p = engine.physics();
    if (!p) throw new Error("[MSH] engine.physics() is required");
    return p;
}

// ------------------- wrapper -------------------

function wrapSurface(engine, handle, cfg) {
    if (handle && handle.__isPrimitiveWrapper) return handle;

    const p = requirePhysics(engine);
    const bid = resolveBodyId(engine, handle);

    const proxy = new Proxy(Object.create(null), {
        get(_t, prop) {
            if (prop === "__isPrimitiveWrapper") return true;
            if (prop === "__surface") return handle;

            if (prop === "bodyId") return function bodyId() {
                return bid;
            };

            if (prop === "applyImpulse") {
                return function applyImpulse(v3) {
                    return p.applyImpulse(bid, v3);
                };
            }
            if (prop === "applyCentralForce") {
                return function applyCentralForce(v3) {
                    return p.applyCentralForce(bid, v3);
                };
            }
            if (prop === "velocity") {
                return function velocity(v3) {
                    if (arguments.length === 0) return p.velocity(bid);
                    return p.velocity(bid, v3);
                };
            }
            if (prop === "position") {
                return function position(v) {
                    if (arguments.length === 0) return p.position(bid);
                    // setter через warp — одно имя, один путь
                    return p.warp(bid, v);
                };
            }
            if (prop === "teleport") {
                return function teleport(v) {
                    return p.warp(bid, v);
                };
            }
            if (prop === "lockRotation") {
                return function lockRotation(lock) {
                    return p.lockRotation(bid, !!lock);
                };
            }

            if (prop === "setVisible") {
                return function setVisible(v) {
                    const s = engine.surface();
                    return s.setVisible(surfaceId(handle), !!v);
                };
            }
            if (prop === "setCull") {
                return function setCull(hint) {
                    const s = engine.surface();
                    return s.setCull(surfaceId(handle), String(hint));
                };
            }

            // passthrough к SurfaceHandle (прямо)
            const v = handle[prop];
            if (typeof v === "function") return v.bind(handle);
            return v;
        }
    });

    return proxy;
}

// ------------------- factory(engine,K) => api -------------------

function create(engine /*, K */) {
    if (!engine) throw new Error("[MSH] engine is required");

    function mesh() {
        const m = engine.mesh();
        if (!m || typeof m.create !== "function") throw new Error("[MSH] engine.mesh().create(cfg) is required");
        return m;
    }

    function createOne(cfg) {
        cfg = normalizeCfg(cfg);
        const h = mesh().create(cfg);
        return wrapSurface(engine, h, cfg);
    }

    function box(cfg) {
        return createOne(withType("box", cfg));
    }

    function cube(cfg) {
        return createOne(withType("box", cfg));
    }

    function sphere(cfg) {
        return createOne(withType("sphere", cfg));
    }

    function cylinder(cfg) {
        return createOne(withType("cylinder", cfg));
    }

    function capsule(cfg) {
        return createOne(withType("capsule", cfg));
    }

    function loadModel(pathOrCfg, cfg) {
        let c;
        if (typeof pathOrCfg === "string") {
            c = normalizeCfg(cfg);
            c.type = "model";
            c.path = String(pathOrCfg);
        } else {
            c = normalizeCfg(pathOrCfg);
            c.type = "model";
        }
        if (!c.path || String(c.path).trim() === "") throw new Error("[MSH] loadModel: path is required");
        return createOne(c);
    }

    function many(list) {
        if (!Array.isArray(list)) throw new Error("[MSH] many(list): array required");
        const m = mesh();
        const out = new Array(list.length);
        for (let i = 0; i < list.length; i++) {
            const cfg = normalizeCfg(list[i]);
            out[i] = wrapSurface(engine, m.create(cfg), cfg);
        }
        return out;
    }

    function builder(type) {
        const state = normalizeCfg({type});

        const b = {
            size(v) {
                state.size = num(v, state.size);
                return b;
            },
            name(v) {
                state.name = String(v);
                return b;
            },
            pos(x, y, z) {
                if (Array.isArray(x) || isObj(x)) state.pos = normalizePos(x);
                else state.pos = [num(x, 0), num(y, 0), num(z, 0)];
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
                state.physics = physics(mass, opts || {});
                return b;
            },
            create() {
                return createOne(state);
            },
            cfg() {
                return Object.assign({}, state);
            }
        };

        return b;
    }

    return Object.freeze({
        create: createOne,
        box, cube, sphere, cylinder, capsule,
        loadModel,
        many,
        unshadedColor,
        physics,
        builder,
        box$: () => builder("box"),
        cube$: () => builder("box"),
        sphere$: () => builder("sphere"),
        cylinder$: () => builder("cylinder"),
        capsule$: () => builder("capsule"),
        model$: () => builder("model")
    });
}

create.META = {
    name: "mesh",
    globalName: "MSH",
    version: "2.0.0",
    description: "Strict mesh primitives factory with bodyId-only physics calls (no fallbacks)",
    engineMin: "0.1.5"
};

module.exports = create;