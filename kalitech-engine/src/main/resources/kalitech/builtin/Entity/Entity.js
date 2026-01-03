// FILE: resources/kalitech/builtin/Entity.js
// Author: Calista Verner
"use strict";

/**
 * Builtin entity factory (engine-extension).
 *
 * Contract:
 *   module.exports(engine, K) => api
 *   module.exports.META = { name, globalName, version, description, engineMin }
 *
 * Goals:
 *  - simple + declarative entity creation: entity + surface + body + components
 *  - player-like use cases out-of-the-box, but no hardcoding to Player
 *  - explicit ids for Java interop (avoid lossy coercion)
 *  - NO silent try/catch: fail loudly with context
 */

function _isObj(v) { return !!v && typeof v === "object" && !Array.isArray(v); }
function _num(v, fb) { const n = +v; return Number.isFinite(n) ? n : (fb || 0); }
function _bool(v, fb) { return (v == null) ? !!fb : !!v; }

function _vec3(v, fbX, fbY, fbZ) {
    if (Array.isArray(v)) return [_num(v[0], fbX), _num(v[1], fbY), _num(v[2], fbZ)];
    if (_isObj(v)) {
        const x = (v.x != null) ? v.x : v[0];
        const y = (v.y != null) ? v.y : v[1];
        const z = (v.z != null) ? v.z : v[2];
        return [_num(x, fbX), _num(y, fbY), _num(z, fbZ)];
    }
    return [fbX, fbY, fbZ];
}

function _deepMerge(dst, src) {
    dst = (dst && typeof dst === "object") ? dst : {};
    if (!src || typeof src !== "object") return dst;
    for (const k of Object.keys(src)) {
        const sv = src[k];
        const dv = dst[k];
        if (sv && typeof sv === "object" && !Array.isArray(sv)) dst[k] = _deepMerge(dv, sv);
        else dst[k] = sv;
    }
    return dst;
}

/**
 * Extract numeric id from various handle shapes.
 * NO silent catches: if some handle misbehaves, you'll see it.
 */
function idOf(h, kind /* "body"|"surface"|"entity" */) {
    if (h == null) return 0;
    if (typeof h === "number") return h | 0;

    // coercion helpers (may throw -> that's OK)
    if (typeof h.valueOf === "function") {
        const v = h.valueOf();
        if (typeof v === "number" && isFinite(v)) return v | 0;
    }

    // direct fields
    if (typeof h.id === "number") return h.id | 0;
    if (typeof h.bodyId === "number") return h.bodyId | 0;
    if (typeof h.surfaceId === "number") return h.surfaceId | 0;
    if (typeof h.entityId === "number") return h.entityId | 0;

    const bodyFns = ["id", "getId", "bodyId", "getBodyId", "handle"];
    const surfFns = ["id", "getId", "surfaceId", "getSurfaceId", "handle"];
    const entFns  = ["id", "getId", "entityId", "getEntityId"];

    const fnNames = kind === "body" ? bodyFns : (kind === "surface" ? surfFns : entFns);

    for (let i = 0; i < fnNames.length; i++) {
        const n = fnNames[i];
        const fn = h[n];
        if (typeof fn === "function") {
            const v = fn.call(h);
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
    }

    return 0;
}

function _req(cond, msg) {
    if (!cond) throw new Error(msg);
}

function _errCtx(msg, e) {
    const m = (e && e.stack) ? e.stack : String(e);
    return msg + " :: " + m;
}

// ---------------- physics binding helpers ----------------

function _bodyIdFromHandle(bodyOrId) {
    if (bodyOrId == null) return 0;
    if (typeof bodyOrId === "number") return bodyOrId | 0;
    try {
        if (typeof bodyOrId.valueOf === "function") {
            const v = bodyOrId.valueOf();
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
    } catch (_) {}
    try {
        if (typeof bodyOrId.id === "number") return bodyOrId.id | 0;
        if (typeof bodyOrId.bodyId === "number") return bodyOrId.bodyId | 0;
    } catch (_) {}
    try {
        if (typeof bodyOrId.id === "function") {
            const v = bodyOrId.id();
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
        if (typeof bodyOrId.bodyId === "function") {
            const v = bodyOrId.bodyId();
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
        if (typeof bodyOrId.getId === "function") {
            const v = bodyOrId.getId();
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
    } catch (_) {}
    return 0;
}

function _surfaceId(handleOrId) {
    if (handleOrId == null) return 0;
    if (typeof handleOrId === "number") return handleOrId | 0;
    try {
        if (typeof handleOrId.id === "number") return handleOrId.id | 0;
        if (typeof handleOrId.surfaceId === "number") return handleOrId.surfaceId | 0;
    } catch (_) {}
    try {
        if (typeof handleOrId.id === "function") {
            const v = handleOrId.id();
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
        if (typeof handleOrId.surfaceId === "function") {
            const v = handleOrId.surfaceId();
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
    } catch (_) {}
    return 0;
}

function _resolveBodyIdBySurface(engine, surfaceHandleOrId) {
    const sid = _surfaceId(surfaceHandleOrId);
    if (!sid) return 0;

    // preferred: SurfaceApi knows the binding
    try {
        const s = engine.surface && engine.surface();
        if (s && typeof s.attachedBody === "function") {
            const bid = s.attachedBody(sid);
            if (typeof bid === "number" && isFinite(bid) && bid > 0) return bid | 0;
        }
    } catch (_) {}

    // fallback: PhysicsApi mapping
    try {
        const p = engine.physics && engine.physics();
        if (p && typeof p.bodyOfSurface === "function") {
            const bid = p.bodyOfSurface(sid);
            if (typeof bid === "number" && isFinite(bid) && bid > 0) return bid | 0;
        }
    } catch (_) {}

    return 0;
}

// ------------------------ EntityHandle ------------------------

class EntityHandle {
    constructor(engine, ctx) {
        this._engine = engine;

        // primitives only
        this.entityId  = (ctx.entityId | 0);
        this.surface   = ctx.surface || null;
        this.body      = ctx.body || null;
        this.surfaceId = (ctx.surfaceId | 0);
        this.bodyId    = (ctx.bodyId | 0);

        this._destroyers = Array.isArray(ctx._destroyers) ? ctx._destroyers : [];

        // cache (lazy) for ref wrapper if you want it
        this._bodyRef = null;
        this._refId = 0;

        // logger (strict)
        _req(engine && engine.log && typeof engine.log === "function", "[ENT] engine.log() is required");
        this._log = engine.log();
        _req(this._log && this._log.info && this._log.warn && this._log.error, "[ENT] engine.log() must provide info/warn/error");
    }

    // --- ids ---
    id() { return (this.entityId | 0); }
    surfaceHandleId() { return (this.surfaceId | 0); }
    bodyHandleId() { return (this.bodyId | 0); }

    valueOf() { return (this.entityId | 0); }
    toString() { return String(this.entityId | 0); }
    [Symbol.toPrimitive](hint) {
        if (hint === "number") return (this.entityId | 0);
        return String(this.entityId | 0);
    }

    setVisible(v) {
        const sid = this.surfaceId | 0;
        if (!sid) throw new Error("[ENT] setVisible: surfaceId=0 entityId=" + (this.entityId | 0));

        const s = this._engine.surface && this._engine.surface();
        if (!s || typeof s.setVisible !== "function") {
            throw new Error("[ENT] setVisible: engine.surface().setVisible(surfaceId,bool) missing");
        }
        s.setVisible(sid, !!v);
        return this;
    }

    setCull(hint) {
        const sid = this.surfaceId | 0;
        if (!sid) throw new Error("[ENT] setCull: surfaceId=0 entityId=" + (this.entityId | 0));

        const s = this._engine.surface && this._engine.surface();
        if (!s || typeof s.setCull !== "function") {
            throw new Error("[ENT] setCull: engine.surface().setCull(surfaceId,string) missing");
        }
        s.setCull(sid, String(hint));
        return this;
    }

    // ---------------- physics helpers ----------------

    hasBody() { return (this.bodyId | 0) > 0; }

    requireBodyId(opName) {
        const id = (this.bodyId | 0);
        if (id <= 0) throw new Error("[ENT] " + opName + ": entity has no bodyId (entityId=" + (this.entityId | 0) + ")");
        return id;
    }

    physApi() {
        const p = this._engine.physics();
        _req(p, "[ENT] engine.physics() returned null");
        return p;
    }

    // Optional: use PHYS.ref if available globally (fast + clean API)
    // Falls back to engine.physics() direct calls.
    bodyRef() {
        const id = this.requireBodyId("bodyRef()");
        if (this._bodyRef && (this._refId | 0) === id) return this._bodyRef;

        if (globalThis.PHYS && typeof globalThis.PHYS.ref === "function") {
            this._bodyRef = globalThis.PHYS.ref(id);
            this._refId = id;
            return this._bodyRef;
        }

        // fallback wrapper bound to engine.physics()
        const phys = this.physApi();
        const self = Object.freeze({
            id: () => id,
            position: (v) => (v === undefined ? phys.position(id) : phys.warp(id, v)),
            warp: (v) => phys.warp(id, v),
            velocity: (v) => (v === undefined ? phys.velocity(id) : phys.velocity(id, v)),
            yaw: (yawRad) => phys.yaw(id, +yawRad || 0),
            applyImpulse: (imp) => phys.applyImpulse(id, imp),
            applyCentralForce: (f) => phys.applyCentralForce(id, f),
            applyTorque: (t) => phys.applyTorque(id, t),
            angularVelocity: (v) => (v === undefined ? phys.angularVelocity(id) : phys.angularVelocity(id, v)),
            clearForces: () => phys.clearForces(id),
            lockRotation: (lock) => phys.lockRotation(id, !!lock),
            collisionGroups: (g, m) => phys.collisionGroups(id, g | 0, m | 0),
            remove: () => phys.remove(id)
        });

        this._bodyRef = self;
        this._refId = id;
        return self;
    }

    // --- transforms ---
    position(v) {
        const id = this.requireBodyId("position()");
        const phys = this.physApi();
        try {
            if (v === undefined) return phys.position(id);
            return phys.warp(id, v);
        } catch (e) {
            throw new Error(_errCtx("[ENT] position failed bodyId=" + id, e));
        }
    }

    warp(pos) {
        const id = this.requireBodyId("warp()");
        const phys = this.physApi();
        try {
            return phys.warp(id, pos);
        } catch (e) {
            throw new Error(_errCtx("[ENT] warp failed bodyId=" + id, e));
        }
    }

    velocity(v) {
        const id = this.requireBodyId("velocity()");
        const phys = this.physApi();
        try {
            if (v === undefined) return phys.velocity(id);
            return phys.velocity(id, v);
        } catch (e) {
            throw new Error(_errCtx("[ENT] velocity failed bodyId=" + id, e));
        }
    }

    yaw(yawRad) {
        const id = this.requireBodyId("yaw()");
        const phys = this.physApi();
        try {
            return phys.yaw(id, +yawRad || 0);
        } catch (e) {
            throw new Error(_errCtx("[ENT] yaw failed bodyId=" + id, e));
        }
    }

    // --- forces ---
    applyImpulse(impulse) {
        const id = this.requireBodyId("applyImpulse()");
        const phys = this.physApi();
        try {
            return phys.applyImpulse(id, impulse);
        } catch (e) {
            throw new Error(_errCtx("[ENT] applyImpulse failed bodyId=" + id, e));
        }
    }

    applyCentralForce(force) {
        const id = this.requireBodyId("applyCentralForce()");
        const phys = this.physApi();
        _req(typeof phys.applyCentralForce === "function", "[ENT] engine.physics().applyCentralForce missing");
        try {
            return phys.applyCentralForce(id, force);
        } catch (e) {
            throw new Error(_errCtx("[ENT] applyCentralForce failed bodyId=" + id, e));
        }
    }

    applyTorque(torque) {
        const id = this.requireBodyId("applyTorque()");
        const phys = this.physApi();
        _req(typeof phys.applyTorque === "function", "[ENT] engine.physics().applyTorque missing");
        try {
            return phys.applyTorque(id, torque);
        } catch (e) {
            throw new Error(_errCtx("[ENT] applyTorque failed bodyId=" + id, e));
        }
    }

    angularVelocity(v) {
        const id = this.requireBodyId("angularVelocity()");
        const phys = this.physApi();
        _req(typeof phys.angularVelocity === "function", "[ENT] engine.physics().angularVelocity missing");
        try {
            if (v === undefined) return phys.angularVelocity(id);
            return phys.angularVelocity(id, v);
        } catch (e) {
            throw new Error(_errCtx("[ENT] angularVelocity failed bodyId=" + id, e));
        }
    }

    clearForces() {
        const id = this.requireBodyId("clearForces()");
        const phys = this.physApi();
        _req(typeof phys.clearForces === "function", "[ENT] engine.physics().clearForces missing");
        try {
            return phys.clearForces(id);
        } catch (e) {
            throw new Error(_errCtx("[ENT] clearForces failed bodyId=" + id, e));
        }
    }

    // --- flags / collision ---
    lockRotation(lock = true) {
        const id = this.requireBodyId("lockRotation()");
        const phys = this.physApi();
        try {
            return phys.lockRotation(id, !!lock);
        } catch (e) {
            throw new Error(_errCtx("[ENT] lockRotation failed bodyId=" + id, e));
        }
    }

    collisionGroups(group, mask) {
        const id = this.requireBodyId("collisionGroups()");
        const phys = this.physApi();
        _req(typeof phys.collisionGroups === "function", "[ENT] engine.physics().collisionGroups missing");
        try {
            return phys.collisionGroups(id, group | 0, mask | 0);
        } catch (e) {
            throw new Error(_errCtx("[ENT] collisionGroups failed bodyId=" + id, e));
        }
    }

    // --- world queries convenience ---
    raycast(cfg) {
        const phys = this.physApi();
        _req(typeof phys.raycast === "function", "[ENT] engine.physics().raycast missing");
        try {
            return phys.raycast(cfg);
        } catch (e) {
            throw new Error(_errCtx("[ENT] raycast failed", e));
        }
    }

    // quick ray straight down from body position
    raycastDown(distance = 2.0, startOffsetY = 0.15) {
        const id = this.requireBodyId("raycastDown()");
        const phys = this.physApi();
        _req(typeof phys.position === "function", "[ENT] engine.physics().position missing");
        _req(typeof phys.raycast === "function", "[ENT] engine.physics().raycast missing");

        const p = phys.position(id);
        _req(p, "[ENT] raycastDown: position() returned null for bodyId=" + id);

        // accept Java vec3 with x/y/z or x()/y()/z()
        const px = (typeof p.x === "function") ? _num(p.x(), 0) : _num(p.x, 0);
        const py = (typeof p.y === "function") ? _num(p.y(), 0) : _num(p.y, 0);
        const pz = (typeof p.z === "function") ? _num(p.z(), 0) : _num(p.z, 0);

        const from = { x: px, y: py + _num(startOffsetY, 0.15), z: pz };
        const to   = { x: px, y: py - _num(distance, 2.0), z: pz };

        try {
            return phys.raycast({ from, to });
        } catch (e) {
            throw new Error(_errCtx("[ENT] raycastDown failed bodyId=" + id, e));
        }
    }

    // ---------------- ECS components ----------------

    component(name, data) {
        const n = String(name || "");
        if (!n) return this;

        const id = (this.entityId | 0);
        _req(id > 0, "[ENT] component(): entityId=0");

        try {
            this._engine.entity().setComponent(id, n, data);
        } catch (e) {
            throw new Error(_errCtx("[ENT] setComponent failed id=" + id + " name=" + n, e));
        }
        return this;
    }

    components(mapOrFn) {
        if (!mapOrFn) return this;

        const id = (this.entityId | 0);
        _req(id > 0, "[ENT] components(): entityId=0");

        let map = mapOrFn;
        if (typeof mapOrFn === "function") {
            map = mapOrFn({
                entityId: id,
                surface: this.surface,
                body: this.body,
                surfaceId: (this.surfaceId | 0),
                bodyId: (this.bodyId | 0)
            });
        }

        if (!map || typeof map !== "object") return this;

        for (const k of Object.keys(map)) {
            const n = String(k || "");
            if (!n) continue;
            try {
                this._engine.entity().setComponent(id, n, map[k]);
            } catch (e) {
                throw new Error(_errCtx("[ENT] setComponent failed id=" + id + " name=" + n, e));
            }
        }

        return this;
    }

    // ---------------- lifecycle ----------------

    destroy() {
        // custom destroyers (reverse order) – errors must be visible
        for (let i = this._destroyers.length - 1; i >= 0; i--) {
            this._destroyers[i]();
        }
        this._destroyers.length = 0;

        // remove body if exists
        const bid = (this.bodyId | 0);
        if (bid > 0) {
            this._engine.physics().remove(bid);
        }

        this.body = null;
        this.surface = null;
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        this._bodyRef = null;
        this._refId = 0;
    }
}

// ------------------------ ENT API ------------------------

class EntApi {
    constructor(engine, K) {
        this.engine = engine;
        this.K = K || (globalThis.__kalitech || Object.create(null));

        _req(engine && engine.entity && engine.mesh && engine.surface && engine.physics, "[ENT] engine missing required subsystems");
        _req(typeof engine.entity === "function" || typeof engine.entity === "object", "[ENT] engine.entity missing");
        _req(typeof engine.physics === "function" || typeof engine.physics === "object", "[ENT] engine.physics missing");

        // presets are mutable configs (user can override via ENT.preset)
        this._presets = Object.create(null);

        this._presets.player = {
            name: "player",
            surface: {
                type: "capsule",
                name: "player.body",
                radius: 0.35,
                height: 1.8,
                pos: [0, 3, 0],
                attach: true,
                physics: { mass: 80, lockRotation: true }
            },
            body: {
                mass: 80,
                friction: 0.9,
                restitution: 0.0,
                damping: { linear: 0.15, angular: 0.95 },
                lockRotation: true,
                collider: { type: "capsule", radius: 0.35, height: 1.8 }
            },
            attachSurface: true,
            debug: true
        };

        this._presets.capsule = {
            name: "entity",
            surface: { type: "capsule", name: "entity.capsule", radius: 0.35, height: 1.8, pos: [0, 3, 0], attach: true },
            attachSurface: true
        };

        this._presets.box = {
            name: "entity",
            surface: { type: "box", name: "entity.box", size: 1, pos: [0, 3, 0], attach: true },
            attachSurface: true
        };

        this._presets.sphere = {
            name: "entity",
            surface: { type: "sphere", name: "entity.sphere", radius: 0.5, pos: [0, 3, 0], attach: true },
            attachSurface: true
        };

        // body defaults
        this._bodyDefaults = {
            mass: 1,
            friction: 0.9,
            restitution: 0.0,
            damping: { linear: 0.15, angular: 0.95 },
            lockRotation: false
        };

        // strict log
        _req(engine && engine.log && typeof engine.log === "function", "[ENT] engine.log() is required");
        this._log = engine.log();
        _req(this._log && this._log.info && this._log.warn && this._log.error, "[ENT] engine.log() must provide info/warn/error");
    }

    // ---------- configuration ----------

    preset(name, cfg) {
        const n = String(name || "");
        if (!n) throw new Error("[ENT] preset(name,cfg): name is required");
        if (!cfg || typeof cfg !== "object") throw new Error("[ENT] preset(name,cfg): cfg object is required");
        this._presets[n] = _deepMerge(_deepMerge({}, this._presets[n] || {}), cfg);
        return this;
    }

    bodyDefaults(cfg) {
        this._bodyDefaults = _deepMerge(_deepMerge({}, this._bodyDefaults), cfg || {});
        return this;
    }

    presets() { return Object.keys(this._presets); }

    // ---------- builder ----------

    $(presetName) { return new EntBuilder(this, presetName ? String(presetName) : ""); }

    player$(cfg) { return this.$("player").merge(cfg); }
    capsule$(cfg) { return this.$("capsule").merge(cfg); }
    box$(cfg) { return this.$("box").merge(cfg); }
    sphere$(cfg) { return this.$("sphere").merge(cfg); }

    // ---------- main: create ----------

    create(cfg) {
        cfg = (cfg && typeof cfg === "object") ? cfg : {};
        const debug = !!cfg.debug;

        const ctx = {
            entityId: 0,
            surface: null,
            body: null,
            surfaceId: 0,
            bodyId: 0,
            _destroyers: []
        };

        // 1) entity
        const name = String(cfg.name || "entity");
        ctx.entityId = this.engine.entity().create(name);

        // 2) surface (optional)
        const surfCfg = cfg.surface || null;
        const bodyCfg = cfg.body || null;
        let surfaceHadPhysics = false;

        if (surfCfg) {
            const sCfg = _deepMerge({}, surfCfg);
            if (sCfg.pos != null) sCfg.pos = _vec3(sCfg.pos, 0, 0, 0);

            // IMPORTANT:
            // - If user provided cfg.body, physics must be created ONLY once (via PhysicsApi.body).
            // - If user provided surface.physics but no cfg.body, we allow Java mesh.create to create the body.
            if (sCfg.physics != null) {
                surfaceHadPhysics = true;
                if (bodyCfg) {
                    // prevent double body creation
                    delete sCfg.physics;
                    surfaceHadPhysics = false;
                }
            }

            ctx.surface = this.engine.mesh().create(sCfg);
            ctx.surfaceId = idOf(ctx.surface, "surface");

            const attachSurface = (cfg.attachSurface != null) ? !!cfg.attachSurface : true;
            if (attachSurface) {
                this.engine.surface().attach(ctx.surface, ctx.entityId);
            }
        }

        // 3) body (optional)
        // Case A: cfg.body exists -> create body here (ONLY here)
        if (bodyCfg) {
            const bCfg = _deepMerge(_deepMerge({}, this._bodyDefaults), bodyCfg);

            if (!bCfg.surface && ctx.surface) bCfg.surface = ctx.surface;

            // Derive collider from surface if missing
            if (!bCfg.collider && surfCfg && surfCfg.type) {
                const t = String(surfCfg.type);
                if (t === "capsule") {
                    bCfg.collider = { type: "capsule", radius: (surfCfg.radius != null) ? surfCfg.radius : 0.35, height: (surfCfg.height != null) ? surfCfg.height : 1.8 };
                } else if (t === "sphere") {
                    bCfg.collider = { type: "sphere", radius: (surfCfg.radius != null) ? surfCfg.radius : 0.5 };
                } else if (t === "box") {
                    bCfg.collider = { type: "box", size: (surfCfg.size != null) ? surfCfg.size : 1 };
                }
            }

            ctx.body = this.engine.physics().body(bCfg);
            ctx.bodyId = idOf(ctx.body, "body");
        } else if (surfaceHadPhysics && ctx.surface) {
            // Case B: body was created by Java mesh.create({ physics: ... })
            // Resolve bodyId and expose it on handle.
            const bid = _resolveBodyIdBySurface(this.engine, ctx.surfaceId || ctx.surface);
            if (bid > 0) {
                ctx.bodyId = bid;
                ctx.body = null; // prefer PHYS.ref via EntityHandle.bodyRef()
            }
        }

        // 4) components (optional)
        const comps = cfg.components;
        if (comps && typeof comps === "object") {
            for (const key of Object.keys(comps)) {
                const v = comps[key];
                let data = v;

                if (typeof v === "function") {
                    data = v({
                        entityId: ctx.entityId,
                        surface: ctx.surface,
                        body: ctx.body,
                        surfaceId: ctx.surfaceId,
                        bodyId: ctx.bodyId,
                        cfg
                    });
                }

                this.engine.entity().setComponent(ctx.entityId, key, data);
            }
        }

        if (debug) {
            this._log.info(
                "[ENT] created name=" + name +
                " entityId=" + (ctx.entityId | 0) +
                " surfaceId=" + (ctx.surfaceId | 0) +
                " bodyId=" + (ctx.bodyId | 0)
            );
        }

        return new EntityHandle(this.engine, ctx);
    }

    // ---------- helpers ----------
    idOf(h, kind) { return idOf(h, kind); }
}

class EntBuilder {
    constructor(ent, presetName) {
        this._ent = ent;
        this._presetName = presetName || "";
        this._cfg = {};
    }

    merge(cfg) { this._cfg = _deepMerge(this._cfg, cfg || {}); return this; }

    name(v) { this._cfg.name = String(v || "entity"); return this; }
    debug(v = true) { this._cfg.debug = !!v; return this; }

    surface(v) { this._cfg.surface = _deepMerge(this._cfg.surface || {}, v || {}); return this; }
    body(v) { this._cfg.body = _deepMerge(this._cfg.body || {}, v || {}); return this; }

    attachSurface(v = true) { this._cfg.attachSurface = !!v; return this; }

    component(name, dataOrFn) {
        const n = String(name || "");
        if (!n) throw new Error("[ENT] builder.component(name,data): name required");
        if (!this._cfg.components) this._cfg.components = Object.create(null);
        this._cfg.components[n] = dataOrFn;
        return this;
    }

    create() {
        let base = {};
        if (this._presetName) {
            const p = this._ent._presets[this._presetName];
            if (p) base = _deepMerge({}, p);
        }
        const finalCfg = _deepMerge(base, this._cfg);
        return this._ent.create(finalCfg);
    }
}

// ------------------- factory(engine,K) => api -------------------

function create(engine, K) {
    _req(engine, "[ENT] engine is required");

    const api = new EntApi(engine, K);

    return Object.freeze({
        // creation
        create: api.create.bind(api),

        // builder
        $: api.$.bind(api),
        player$: api.player$.bind(api),
        capsule$: api.capsule$.bind(api),
        box$: api.box$.bind(api),
        sphere$: api.sphere$.bind(api),

        // config
        preset: api.preset.bind(api),
        bodyDefaults: api.bodyDefaults.bind(api),
        presets: api.presets.bind(api),

        // utility
        idOf: api.idOf.bind(api)
    });
}

create.META = {
    name: "entity",
    globalName: "ENT",
    version: "1.1.0",
    description: "Declarative entity builder (entity + surface + body + components) + physics methods on EntityHandle",
    engineMin: "0.1.0"
};

module.exports = create;