// FILE: resources/kalitech/builtin/Primitives.js
// Author: Calista Verner
"use strict";

/**
 * Builtin primitives factory (engine-extension).
 *
 * Contract:
 *   module.exports(engine, K) => api
 *   module.exports.META = { name, globalName, version, description, engineMin }
 *
 * Wrapper features:
 *   g.applyImpulse({x,y,z})
 *   g.applyCentralForce({x,y,z})
 *   g.velocity() / g.velocity({x,y,z})
 *   g.position() / g.position([x,y,z]) / g.position({x,y,z})
 *   g.teleport(...)
 *   g.lockRotation(true)
 *
 * IMPORTANT:
 *  - mesh.create({ physics: ... }) should create the body on Java side.
 *  - Wrapper MUST NOT accidentally create a second body.
 *
 * Stability strategy:
 *  1) Try resolve existing body by surfaceId (preferred):
 *      - engine.surface().attachedBody(surfaceId)  -> bodyId
 *      - engine.physics().bodyOfSurface(surfaceId) -> bodyId
 *      - engine.physics().handle(bodyId)           -> bodyHandle
 *  2) If not available (old engine build), fallback ONCE to:
 *      engine.physics().body({ surface: handle, mass: massHint })
 *     This is safe only if PhysicsApiImpl caches body per surfaceId.
 */

function _isObj(v) { return !!v && typeof v === "object" && !Array.isArray(v); }
function _num(v, fb) { const n = +v; return Number.isFinite(n) ? n : (fb || 0); }

function _normalizePos(p) {
    if (Array.isArray(p)) return [_num(p[0], 0), _num(p[1], 0), _num(p[2], 0)];
    if (_isObj(p)) {
        const x = (p.x != null) ? p.x : p[0];
        const y = (p.y != null) ? p.y : p[1];
        const z = (p.z != null) ? p.z : p[2];
        return [_num(x, 0), _num(y, 0), _num(z, 0)];
    }
    return undefined;
}

function _normalizePhysics(cfg) {
    let p = cfg.physics;

    if (typeof p === "number") p = { mass: p };
    else if (!_isObj(p)) p = (_isObj(p) ? p : undefined);

    // legacy/top-level compatibility
    const topMass = (cfg.mass != null) ? cfg.mass : undefined;
    const topEnabled = (cfg.physicsEnabled != null) ? cfg.physicsEnabled : cfg.enabled;
    const topLock = cfg.lockRotation;
    const topKin = cfg.kinematic;

    const topFriction = cfg.friction;
    const topRest = cfg.restitution;
    const topDamp = cfg.damping;
    const topCollider = cfg.collider;

    const hasAny =
        p != null ||
        topMass != null || topEnabled != null || topLock != null || topKin != null ||
        topFriction != null || topRest != null || topDamp != null || topCollider != null;

    if (!hasAny) return undefined;

    p = p || {};
    if (topMass != null && p.mass == null) p.mass = _num(topMass, 0);
    if (topEnabled != null && p.enabled == null) p.enabled = !!topEnabled;
    if (topLock != null && p.lockRotation == null) p.lockRotation = !!topLock;
    if (topKin != null && p.kinematic == null) p.kinematic = !!topKin;

    if (topFriction != null && p.friction == null) p.friction = topFriction;
    if (topRest != null && p.restitution == null) p.restitution = topRest;
    if (topDamp != null && p.damping == null) p.damping = topDamp;
    if (topCollider != null && p.collider == null) p.collider = topCollider;

    return p;
}

function normalizeCfg(cfg) {
    cfg = (_isObj(cfg)) ? cfg : {};
    const out = Object.assign({}, cfg);

    if (out.type != null) out.type = String(out.type);
    if (out.name != null) out.name = String(out.name);

    const p =
        (out.pos != null) ? out.pos :
            (out.position != null) ? out.position :
                (out.loc != null) ? out.loc :
                    (out.location != null) ? out.location :
                        undefined;

    const posN = _normalizePos(p);
    if (posN !== undefined) out.pos = posN;

    if (out.radius == null && out.r != null) out.radius = out.r;
    if (out.height == null && out.h != null) out.height = out.h;

    if (out.radius != null) out.radius = _num(out.radius, out.radius);
    if (out.height != null) out.height = _num(out.height, out.height);

    const phys = _normalizePhysics(out);
    if (phys !== undefined) out.physics = phys;

    return out;
}

function withType(type, cfg) {
    const c = normalizeCfg(cfg);
    c.type = String(type);
    return c;
}

function unshadedColor(rgba) {
    const c = Array.isArray(rgba) ? rgba : [1, 1, 1, 1];
    return { def: "Common/MatDefs/Misc/Unshaded.j3md", params: { Color: c } };
}

function physics(mass, opts) {
    const o = opts || {};
    const p = { mass: (mass != null ? mass : 0) };

    if (o.enabled != null) p.enabled = !!o.enabled;
    if (o.lockRotation != null) p.lockRotation = !!o.lockRotation;
    if (o.kinematic != null) p.kinematic = !!o.kinematic;

    if (o.friction != null) p.friction = o.friction;
    if (o.restitution != null) p.restitution = o.restitution;
    if (o.damping != null) p.damping = o.damping;

    if (o.collider != null) p.collider = o.collider;
    return p;
}

// ------------------- Body resolution helpers -------------------

function _readNum(v, fb) {
    const n = +v;
    return Number.isFinite(n) ? n : (fb || 0);
}

function _resolveMassFromCfg(cfg) {
    const p = cfg && cfg.physics;
    if (p && typeof p === "object" && p.mass != null) return _readNum(p.mass, 0);
    if (cfg && cfg.mass != null) return _readNum(cfg.mass, 0);
    return 0;
}

function _surfaceId(handle) {
    if (!handle) return 0;
    try {
        if (typeof handle.id === "number") return handle.id | 0;
        if (typeof handle.surfaceId === "number") return handle.surfaceId | 0;
    } catch (_) {}
    try {
        if (typeof handle.id === "function") {
            const v = handle.id();
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
    } catch (_) {}
    try {
        if (typeof handle.surfaceId === "function") {
            const v = handle.surfaceId();
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
    } catch (_) {}
    return 0;
}

function _resolveBodyIdBySurface(engine, surfaceHandleOrId) {
    const sid = (typeof surfaceHandleOrId === "number") ? (surfaceHandleOrId | 0) : _surfaceId(surfaceHandleOrId);
    if (!sid) return 0;

    try {
        const s = engine.surface && engine.surface();
        if (s && typeof s.attachedBody === "function") {
            const bid = s.attachedBody(sid);
            if (typeof bid === "number" && isFinite(bid) && bid > 0) return bid | 0;
        }
    } catch (_) {}

    try {
        const p = engine.physics && engine.physics();
        if (p && typeof p.bodyOfSurface === "function") {
            const bid = p.bodyOfSurface(sid);
            if (typeof bid === "number" && isFinite(bid) && bid > 0) return bid | 0;
        }
    } catch (_) {}

    return 0;
}

function _resolveBodyHandleById(engine, bodyId) {
    const bid = bodyId | 0;
    if (!bid) return null;

    try {
        const p = engine.physics && engine.physics();
        if (p && typeof p.handle === "function") {
            const h = p.handle(bid);
            if (h != null) return h;
        }
    } catch (_) {}

    // last resort: some engines may accept id directly in physics ops
    return bid;
}

// ------------------- Surface wrapper with physics sugar -------------------

function wrapSurface(engine, handle, cfg) {
    if (handle && handle.__isPrimitiveWrapper) return handle;

    const massHint = _resolveMassFromCfg(cfg);

    let cachedBodyId = 0;
    let cachedBody = null;
    let triedFallbackCreate = false;

    function _bodyHandle() {
        if (cachedBody != null) return cachedBody;

        if (!cachedBodyId) cachedBodyId = _resolveBodyIdBySurface(engine, handle);
        if (cachedBodyId) {
            cachedBody = _resolveBodyHandleById(engine, cachedBodyId);
            if (cachedBody != null) return cachedBody;
        }

        // fallback (old engine builds) – ONCE
        if (!triedFallbackCreate) {
            triedFallbackCreate = true;
            try {
                const p = engine.physics && engine.physics();
                if (p && typeof p.body === "function") {
                    const b = p.body({ surface: handle, mass: massHint });
                    cachedBody = b || null;
                    return cachedBody;
                }
            } catch (_) {}
        }

        return null;
    }

    const proxy = new Proxy(Object.create(null), {
        get(_t, prop) {
            if (prop === "__isPrimitiveWrapper") return true;
            if (prop === "__surface") return handle;

            if (prop === "applyImpulse") {
                return function applyImpulse(vec3) {
                    const b = _bodyHandle();
                    if (!b) return;
                    try { if (b && typeof b.applyImpulse === "function") return b.applyImpulse(vec3); } catch (_) {}
                    const p = engine.physics && engine.physics();
                    if (p && typeof p.applyImpulse === "function") return p.applyImpulse(b, vec3);
                };
            }

            if (prop === "applyCentralForce") {
                return function applyCentralForce(vec3) {
                    const b = _bodyHandle();
                    if (!b) return;
                    try { if (b && typeof b.applyCentralForce === "function") return b.applyCentralForce(vec3); } catch (_) {}
                    const p = engine.physics && engine.physics();
                    if (p && typeof p.applyCentralForce === "function") return p.applyCentralForce(b, vec3);
                };
            }

            if (prop === "velocity") {
                return function velocity(v) {
                    const b = _bodyHandle();
                    if (!b) return undefined;
                    const p = engine.physics && engine.physics();
                    if (arguments.length === 0) {
                        try { if (b && typeof b.velocity === "function") return b.velocity(); } catch (_) {}
                        if (p && typeof p.velocity === "function") return p.velocity(b);
                        return undefined;
                    }
                    try { if (b && typeof b.velocity === "function") return b.velocity(v); } catch (_) {}
                    if (p && typeof p.velocity === "function") return p.velocity(b, v);
                };
            }

            if (prop === "position") {
                return function position(v) {
                    const b = _bodyHandle();
                    if (!b) return undefined;
                    const p = engine.physics && engine.physics();
                    if (arguments.length === 0) {
                        try { if (b && typeof b.position === "function") return b.position(); } catch (_) {}
                        if (p && typeof p.position === "function") return p.position(b);
                        return undefined;
                    }
                    // setter
                    try { if (b && typeof b.teleport === "function") return b.teleport(v); } catch (_) {}
                    if (p && typeof p.position === "function") return p.position(b, v);
                };
            }

            if (prop === "teleport") {
                return function teleport(v) {
                    const b = _bodyHandle();
                    if (!b) return;
                    const p = engine.physics && engine.physics();
                    try { if (b && typeof b.teleport === "function") return b.teleport(v); } catch (_) {}
                    if (p && typeof p.position === "function") return p.position(b, v);
                };
            }

            if (prop === "lockRotation") {
                return function lockRotation(lock) {
                    const b = _bodyHandle();
                    if (!b) return;
                    const p = engine.physics && engine.physics();
                    try { if (b && typeof b.lockRotation === "function") return b.lockRotation(!!lock); } catch (_) {}
                    if (p && typeof p.lockRotation === "function") return p.lockRotation(b, !!lock);
                };
            }

            // passthrough to surface handle
            const v = handle[prop];
            if (typeof v === "function") return v.bind(handle);
            return v;
        },
        set(_t, prop, value) { _t[prop] = value; return true; },
        has(_t, prop) { if (prop in _t) return true; return prop in handle; },
        ownKeys(_t) {
            const keys = new Set(Object.keys(_t));
            try { Object.keys(handle).forEach(k => keys.add(k)); } catch (_) {}
            keys.add("applyImpulse");
            keys.add("applyCentralForce");
            keys.add("velocity");
            keys.add("position");
            keys.add("teleport");
            keys.add("lockRotation");
            return Array.from(keys);
        },
        getOwnPropertyDescriptor(_t, prop) {
            if (prop in _t) return Object.getOwnPropertyDescriptor(_t, prop);
            return { configurable: true, enumerable: true, writable: true, value: this.get(_t, prop) };
        }
    });

    return proxy;
}

// ------------------- factory(engine,K) => api -------------------

function create(engine /*, K */) {
    if (!engine) throw new Error("[MSH] engine is required");

    function mesh() {
        const m = engine.mesh && engine.mesh();
        if (!m || typeof m.create !== "function") {
            throw new Error("[MSH] engine.mesh().create(cfg) is required");
        }
        return m;
    }

    function createOne(cfg) {
        cfg = normalizeCfg(cfg);
        const h = mesh().create(cfg);
        return wrapSurface(engine, h, cfg);
    }

    function box(cfg) { return createOne(withType("box", cfg)); }
    function cube(cfg) { return createOne(withType("box", cfg)); }
    function sphere(cfg) { return createOne(withType("sphere", cfg)); }
    function cylinder(cfg) { return createOne(withType("cylinder", cfg)); }
    function capsule(cfg) { return createOne(withType("capsule", cfg)); }

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
        const state = normalizeCfg({ type });

        const b = {
            size(v) { state.size = _num(v, state.size); return b; },
            name(v) { state.name = String(v); return b; },
            pos(x, y, z) {
                if (Array.isArray(x) || _isObj(x)) state.pos = _normalizePos(x);
                else state.pos = [_num(x, 0), _num(y, 0), _num(z, 0)];
                return b;
            },
            material(m) { state.material = m; return b; },

            // physics builder
            physics(mass, opts) { state.physics = physics(mass, opts || {}); return b; },

            // alias setters (handy)
            mass(v) { state.physics = state.physics || {}; state.physics.mass = _num(v, 0); return b; },
            kinematic(v = true) { state.physics = state.physics || {}; state.physics.kinematic = !!v; return b; },
            enabled(v = true) { state.physics = state.physics || {}; state.physics.enabled = !!v; return b; },
            lockRotation(v = true) { state.physics = state.physics || {}; state.physics.lockRotation = !!v; return b; },
            friction(v) { state.physics = state.physics || {}; state.physics.friction = v; return b; },
            restitution(v) { state.physics = state.physics || {}; state.physics.restitution = v; return b; },
            damping(linear, angular) {
                state.physics = state.physics || {};
                state.physics.damping = { linear: linear, angular: angular };
                return b;
            },
            collider(v) { state.physics = state.physics || {}; state.physics.collider = v; return b; },

            create() { return createOne(state); },
            cfg() { return Object.assign({}, state); }
        };

        return b;
    }

    const api = Object.freeze({
        // primitives
        create: createOne,
        box,
        cube,
        sphere,
        cylinder,
        capsule,
        many,

        // materials helpers
        unshadedColor,

        // physics helper (cfg builder)
        physics,

        // builders
        builder,
        box$: () => builder("box"),
        cube$: () => builder("box"),
        sphere$: () => builder("sphere"),
        cylinder$: () => builder("cylinder"),
        capsule$: () => builder("capsule")
    });

    return api;
}

// META (adult contract)
create.META = {
    name: "primitives",
    globalName: "MSH",
    version: "1.0.0",
    description: "Mesh primitives factory with physics-aware SurfaceHandle wrapper (velocity/teleport/impulse)",
    engineMin: "0.1.0"
};

module.exports = create;