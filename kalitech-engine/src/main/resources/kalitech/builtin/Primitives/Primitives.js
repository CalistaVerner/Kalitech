// FILE: resources/kalitech/builtin/Primitives.js
// Author: Calista Verner
"use strict";

/**
 * Builtin primitives factory.
 *
 * Wraps returned SurfaceHandle so scripts can call:
 *   g.applyImpulse({x,y,z})
 *   g.velocity({x,y,z})
 *   g.position([x,y,z]) / g.position({x,y,z})
 *   g.lockRotation(true)
 *
 * IMPORTANT (new API):
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
module.exports = function primitivesFactory(K) {
    K = K || (globalThis.__kalitech || Object.create(null));
    if (!K.builtins) K.builtins = Object.create(null);

    function requireEngine() {
        const eng = (K.builtins && K.builtins.engine) || K._engine || globalThis.engine;
        if (!eng) throw new Error("[builtin/primitives] engine not attached");
        return eng;
    }

    function mesh() {
        const eng = requireEngine();
        const m = eng.mesh && eng.mesh();
        if (!m || typeof m.create !== "function") {
            throw new Error("[builtin/primitives] engine.mesh().create(cfg) is required");
        }
        return m;
    }

    // ---------- config normalization (IMPORTANT) ----------

    function _isObj(v) { return !!v && typeof v === "object" && !Array.isArray(v); }
    function _num(v, fb) { const n = +v; return Number.isFinite(n) ? n : (fb || 0); }

    function _normalizePos(p) {
        // keep array [x,y,z] OR {x,y,z} (engine side can parse both)
        if (Array.isArray(p)) {
            return [ _num(p[0], 0), _num(p[1], 0), _num(p[2], 0) ];
        }
        if (_isObj(p)) {
            // accept {x,y,z} or {0:..,1:..,2:..}
            const x = (p.x != null) ? p.x : p[0];
            const y = (p.y != null) ? p.y : p[1];
            const z = (p.z != null) ? p.z : p[2];
            return [ _num(x, 0), _num(y, 0), _num(z, 0) ];
        }
        return undefined;
    }

    function _normalizePhysics(cfg) {
        // allow:
        //  physics: {mass, lockRotation,...}
        //  physics: 80  -> {mass:80}
        //  mass: 80     -> physics.mass = 80
        //  lockRotation/kinematic/... at top-level -> move into physics
        let p = cfg.physics;

        if (typeof p === "number") p = { mass: p };
        else if (!_isObj(p)) p = (_isObj(p) ? p : undefined);

        // top-level shorthands
        const topMass = (cfg.mass != null) ? cfg.mass : undefined;
        const topEnabled = (cfg.physicsEnabled != null) ? cfg.physicsEnabled : cfg.enabled; // optional alias
        const topLock = cfg.lockRotation;
        const topKin = cfg.kinematic;

        const topFriction = cfg.friction;
        const topRest = cfg.restitution;
        const topDamp = cfg.damping;
        const topCollider = cfg.collider;

        // if any physics info exists anywhere — ensure object
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
        // Goal: preserve and normalize options you listed:
        // radius, height, name, pos, physics
        cfg = (_isObj(cfg)) ? cfg : {};

        const out = Object.assign({}, cfg);

        // --- type normalization ---
        if (out.type != null) out.type = String(out.type);

        // --- name ---
        if (out.name != null) out.name = String(out.name);

        // --- position: accept pos | position | loc | location ---
        const p =
            (out.pos != null) ? out.pos :
                (out.position != null) ? out.position :
                    (out.loc != null) ? out.loc :
                        (out.location != null) ? out.location :
                            undefined;

        const posN = _normalizePos(p);
        if (posN !== undefined) out.pos = posN;

        // --- geometry params: keep radius/height, plus aliases r/h ---
        if (out.radius == null && out.r != null) out.radius = out.r;
        if (out.height == null && out.h != null) out.height = out.h;

        if (out.radius != null) out.radius = _num(out.radius, out.radius);
        if (out.height != null) out.height = _num(out.height, out.height);

        // --- physics normalization ---
        const phys = _normalizePhysics(out);
        if (phys !== undefined) out.physics = phys;

        return out;
    }

    function withType(type, cfg) {
        const c = normalizeCfg(cfg);
        if (!c.type) c.type = type;
        else c.type = String(c.type);
        return c;
    }

    function unshadedColor(rgba) {
        const c = Array.isArray(rgba) ? rgba : [1, 1, 1, 1];
        return {
            def: "Common/MatDefs/Misc/Unshaded.j3md",
            params: { Color: c }
        };
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
        if (cfg && cfg.mass != null) return _readNum(cfg.mass, 0); // fallback
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

    function _resolveBodyIdBySurface(eng, surfaceHandleOrId) {
        const sid = (typeof surfaceHandleOrId === "number") ? (surfaceHandleOrId | 0) : _surfaceId(surfaceHandleOrId);
        if (!sid) return 0;

        // Preferred: surface.attachedBody(surfaceId)
        try {
            const s = eng.surface && eng.surface();
            if (s && typeof s.attachedBody === "function") {
                const bid = s.attachedBody(sid);
                if (typeof bid === "number" && isFinite(bid) && bid > 0) return bid | 0;
            }
        } catch (_) {}

        // Alternative: physics.bodyOfSurface(surfaceId)
        try {
            const p = eng.physics && eng.physics();
            if (p && typeof p.bodyOfSurface === "function") {
                const bid = p.bodyOfSurface(sid);
                if (typeof bid === "number" && isFinite(bid) && bid > 0) return bid | 0;
            }
        } catch (_) {}

        return 0;
    }

    function _resolveBodyHandleById(eng, bodyId) {
        const bid = bodyId | 0;
        if (!bid) return null;

        // Preferred: physics.handle(bodyId) -> handle
        try {
            const p = eng.physics && eng.physics();
            if (p && typeof p.handle === "function") {
                const h = p.handle(bid);
                if (h != null) return h;
            }
        } catch (_) {}

        // Fallback: many physics APIs accept int id directly
        return bid;
    }

    // ------------------- Surface wrapper with physics sugar -------------------

    function wrapSurface(handle, cfg) {
        if (handle && handle.__isPrimitiveWrapper) return handle;

        const eng = requireEngine();
        const massHint = _resolveMassFromCfg(cfg);

        let cachedBodyId = 0;
        let cachedBody = null;
        let triedFallbackCreate = false;

        function _bodyHandle() {
            if (cachedBody != null) return cachedBody;

            // 1) Try resolve existing body linked to surface
            if (!cachedBodyId) cachedBodyId = _resolveBodyIdBySurface(eng, handle);
            if (cachedBodyId) {
                cachedBody = _resolveBodyHandleById(eng, cachedBodyId);
                if (cachedBody != null) return cachedBody;
            }

            // 2) Fallback ONCE: ensure body exists via legacy call (must be cached per surfaceId on engine side)
            if (!triedFallbackCreate) {
                triedFallbackCreate = true;
                try {
                    const p = eng.physics && eng.physics();
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

                // ---------- physics sugar ----------
                if (prop === "applyImpulse") {
                    return function applyImpulse(vec3) {
                        const b = _bodyHandle();
                        if (!b) return;
                        // allow both: physics.applyImpulse(handle, vec3) OR body.applyImpulse(vec3)
                        try {
                            if (b && typeof b.applyImpulse === "function") return b.applyImpulse(vec3);
                        } catch (_) {}
                        eng.physics().applyImpulse(b, vec3);
                    };
                }
                if (prop === "applyCentralForce") {
                    return function applyCentralForce(vec3) {
                        const b = _bodyHandle();
                        if (!b) return;
                        try {
                            if (b && typeof b.applyCentralForce === "function") return b.applyCentralForce(vec3);
                        } catch (_) {}
                        eng.physics().applyCentralForce(b, vec3);
                    };
                }
                if (prop === "velocity") {
                    return function velocity(v) {
                        const b = _bodyHandle();
                        if (!b) return undefined;
                        if (arguments.length === 0) {
                            try { if (b && typeof b.velocity === "function") return b.velocity(); } catch (_) {}
                            return eng.physics().velocity(b);
                        }
                        try { if (b && typeof b.velocity === "function") return b.velocity(v); } catch (_) {}
                        eng.physics().velocity(b, v);
                    };
                }
                if (prop === "position") {
                    return function position(p) {
                        const b = _bodyHandle();
                        if (!b) return undefined;
                        if (arguments.length === 0) {
                            try { if (b && typeof b.position === "function") return b.position(); } catch (_) {}
                            return eng.physics().position(b);
                        }
                        try {
                            if (b && typeof b.teleport === "function") return b.teleport(p);
                        } catch (_) {}
                        eng.physics().position(b, p);
                    };
                }
                if (prop === "teleport") {
                    return function teleport(p) {
                        const b = _bodyHandle();
                        if (!b) return;
                        try { if (b && typeof b.teleport === "function") return b.teleport(p); } catch (_) {}
                        eng.physics().position(b, p);
                    };
                }
                if (prop === "lockRotation") {
                    return function lockRotation(lock) {
                        const b = _bodyHandle();
                        if (!b) return;
                        try { if (b && typeof b.lockRotation === "function") return b.lockRotation(!!lock); } catch (_) {}
                        eng.physics().lockRotation(b, !!lock);
                    };
                }

                // transparent pass-through
                const v = handle[prop];
                if (typeof v === "function") return v.bind(handle);
                return v;
            },
            set(_t, prop, value) {
                _t[prop] = value;
                return true;
            },
            has(_t, prop) {
                if (prop in _t) return true;
                return prop in handle;
            },
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

    // ------------------- factory API (wrap results) -------------------

    function create(cfg) {
        cfg = normalizeCfg(cfg);
        const h = mesh().create(cfg);
        return wrapSurface(h, cfg);
    }

    function box(cfg) {
        cfg = withType("box", cfg);
        const h = mesh().create(cfg);
        return wrapSurface(h, cfg);
    }

    function cube(cfg) {
        cfg = withType("box", cfg);
        const h = mesh().create(cfg);
        return wrapSurface(h, cfg);
    }

    function sphere(cfg) {
        cfg = withType("sphere", cfg);
        const h = mesh().create(cfg);
        return wrapSurface(h, cfg);
    }

    function cylinder(cfg) {
        cfg = withType("cylinder", cfg);
        const h = mesh().create(cfg);
        return wrapSurface(h, cfg);
    }

    function capsule(cfg) {
        cfg = withType("capsule", cfg);
        const h = mesh().create(cfg);
        return wrapSurface(h, cfg);
    }

    function many(list) {
        if (!Array.isArray(list)) throw new Error("[builtin/primitives] many(list): array required");
        const m = mesh();
        const out = new Array(list.length);
        for (let i = 0; i < list.length; i++) {
            const cfg = normalizeCfg(list[i]);
            out[i] = wrapSurface(m.create(cfg), cfg);
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
                else state.pos = [ _num(x, 0), _num(y, 0), _num(z, 0) ];
                return b;
            },
            material(m) { state.material = m; return b; },
            physics(mass, opts) { state.physics = physics(mass, opts || {}); return b; },
            create() { return create(state); },
            cfg() { return Object.assign({}, state); }
        };

        return b;
    }


    const api = Object.freeze({
        // старое API — НЕ ТРОГАЕМ
        create,
        box,
        cube,
        sphere,
        cylinder,
        capsule,
        many,
        unshadedColor,
        physics,

        builder,
        box$: () => builder("box"),
        cube$: () => builder("box"),
        sphere$: () => builder("sphere"),
        cylinder$: () => builder("cylinder"),
        capsule$: () => builder("capsule")
    });


    return api;
};