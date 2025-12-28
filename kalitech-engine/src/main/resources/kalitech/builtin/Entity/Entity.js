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
 *  - safe id extraction for graal handles
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
 */
function idOf(h, kind /* "body"|"surface"|"entity" */) {
    if (h == null) return 0;
    if (typeof h === "number") return h | 0;

    try {
        if (typeof h.valueOf === "function") {
            const v = h.valueOf();
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
    } catch (_) {}

    try {
        if (typeof h.id === "number") return h.id | 0;
        if (typeof h.bodyId === "number") return h.bodyId | 0;
        if (typeof h.surfaceId === "number") return h.surfaceId | 0;
        if (typeof h.entityId === "number") return h.entityId | 0;
    } catch (_) {}

    const bodyFns = ["id", "getId", "bodyId", "getBodyId", "handle"];
    const surfFns = ["id", "getId", "surfaceId", "getSurfaceId", "handle"];
    const entFns = ["id", "getId", "entityId", "getEntityId"];

    const fnNames = kind === "body" ? bodyFns : (kind === "surface" ? surfFns : entFns);

    for (let i = 0; i < fnNames.length; i++) {
        const n = fnNames[i];
        try {
            const fn = h[n];
            if (typeof fn === "function") {
                const v = fn.call(h);
                if (typeof v === "number" && isFinite(v)) return v | 0;
            }
        } catch (_) {}
    }

    return 0;
}

function _logInfo(engine, msg) { try { engine && engine.log && engine.log().info(msg); } catch (_) {} }
function _logWarn(engine, msg) { try { engine && engine.log && engine.log().warn(msg); } catch (_) {} }
function _logErr(engine, msg) { try { engine && engine.log && engine.log().error(msg); } catch (_) {} }

class EntityHandle {
    constructor(engine, ctx) {
        this._engine = engine;

        // store ONLY primitives in ids (avoid lossy coercion issues)
        this.entityId = (ctx.entityId | 0);
        this.surface = ctx.surface || null;
        this.body = ctx.body || null;

        this.surfaceId = (ctx.surfaceId | 0);
        this.bodyId = (ctx.bodyId | 0);

        this._destroyers = Array.isArray(ctx._destroyers) ? ctx._destroyers : [];
    }

    // --- explicit numeric id getter (best practice for Java interop) ---
    id() { return (this.entityId | 0); }
    surfaceHandleId() { return (this.surfaceId | 0); }
    bodyHandleId() { return (this.bodyId | 0); }

    // --- best-effort JS coercion helpers (handy in JS; Graal may still prefer explicit id()) ---
    valueOf() { return (this.entityId | 0); }
    toString() { return String(this.entityId | 0); }
    [Symbol.toPrimitive](hint) {
        if (hint === "number") return (this.entityId | 0);
        return String(this.entityId | 0);
    }

    warp(pos) {
        if (!pos) return;
        const p = pos;

        const h = this.body;
        if (h && typeof h.teleport === "function") {
            try { h.teleport(p); }
            catch (e) { _logErr(this._engine, "[ENT] warp.teleport failed: " + e); }
            return;
        }

        const bodyId = (this.bodyId | 0);
        if (!bodyId) return;

        try { this._engine.physics().position(bodyId, p); }
        catch (e) { _logErr(this._engine, "[ENT] warp.physics.position failed: " + e); }
    }

    velocity(v) {
        const h = this.body;
        if (h && typeof h.velocity === "function") {
            try {
                if (arguments.length === 0) return h.velocity();
                return h.velocity(v);
            } catch (_) {}
        }

        const id = (this.bodyId | 0);
        if (!id) return undefined;

        try {
            if (arguments.length === 0) return this._engine.physics().velocity(id);
            return this._engine.physics().velocity(id, v);
        } catch (_) {}

        return undefined;
    }

    component(name, data) {
        const n = String(name || "");
        if (!n) return this;

        // IMPORTANT: always pass an int to Java
        const id = (this.entityId | 0);

        try { this._engine.entity().setComponent(id, n, data); }
        catch (e) { _logErr(this._engine, "[ENT] setComponent failed id=" + id + " name=" + n + " err=" + e); }

        return this;
    }

    // convenience: set multiple components at once
    components(mapOrFn) {
        if (!mapOrFn) return this;
        const id = (this.entityId | 0);
        if (!id) return this;

        let map = mapOrFn;
        if (typeof mapOrFn === "function") {
            try {
                map = mapOrFn({
                    entityId: id,
                    surface: this.surface,
                    body: this.body,
                    surfaceId: (this.surfaceId | 0),
                    bodyId: (this.bodyId | 0)
                });
            } catch (e) {
                _logErr(this._engine, "[ENT] components(builder) failed: " + e);
                return this;
            }
        }

        if (!map || typeof map !== "object") return this;

        for (const k of Object.keys(map)) {
            const n = String(k || "");
            if (!n) continue;
            try { this._engine.entity().setComponent(id, n, map[k]); }
            catch (e) { _logErr(this._engine, "[ENT] setComponent failed id=" + id + " name=" + n + " err=" + e); }
        }

        return this;
    }

    destroy() {
        // custom destroyers (reverse order)
        for (let i = this._destroyers.length - 1; i >= 0; i--) {
            try { this._destroyers[i](); } catch (_) {}
        }
        this._destroyers.length = 0;

        try { if ((this.bodyId | 0) > 0) this._engine.physics().remove(this.bodyId | 0); } catch (_) {}

        this.body = null;
        this.surface = null;

        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;
    }
}


class EntApi {
    constructor(engine, K) {
        this.engine = engine;
        this.K = K || (globalThis.__kalitech || Object.create(null));

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
            surface: {
                type: "capsule",
                name: "entity.capsule",
                radius: 0.35,
                height: 1.8,
                pos: [0, 3, 0],
                attach: true
            },
            attachSurface: true
        };

        this._presets.box = {
            name: "entity",
            surface: {
                type: "box",
                name: "entity.box",
                size: 1,
                pos: [0, 3, 0],
                attach: true
            },
            attachSurface: true
        };

        this._presets.sphere = {
            name: "entity",
            surface: {
                type: "sphere",
                name: "entity.sphere",
                radius: 0.5,
                pos: [0, 3, 0],
                attach: true
            },
            attachSurface: true
        };

        // body defaults (used when cfg.body exists and doesn't specify values)
        this._bodyDefaults = {
            mass: 1,
            friction: 0.9,
            restitution: 0.0,
            damping: { linear: 0.15, angular: 0.95 },
            lockRotation: false
        };
    }

    // ---------- configuration ----------

    preset(name, cfg) {
        const n = String(name || "");
        if (!n) return this;
        if (!cfg || typeof cfg !== "object") return this;
        this._presets[n] = _deepMerge(_deepMerge({}, this._presets[n] || {}), cfg);
        return this;
    }

    bodyDefaults(cfg) {
        this._bodyDefaults = _deepMerge(_deepMerge({}, this._bodyDefaults), cfg || {});
        return this;
    }

    presets() {
        return Object.keys(this._presets);
    }

    // ---------- builder ----------

    $(presetName) {
        return new EntBuilder(this, presetName ? String(presetName) : "");
    }

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
        if (surfCfg) {
            const sCfg = _deepMerge({}, surfCfg);

            // normalize pos
            if (sCfg.pos != null) sCfg.pos = _vec3(sCfg.pos, 0, 0, 0);

            ctx.surface = this.engine.mesh().create(sCfg);
            ctx.surfaceId = idOf(ctx.surface, "surface");

            // attach surface -> entity
            const attachSurface = (cfg.attachSurface != null) ? !!cfg.attachSurface : true;
            if (attachSurface) {
                try { this.engine.surface().attach(ctx.surface, ctx.entityId); }
                catch (e) { _logWarn(this.engine, "[ENT] surface.attach failed: " + e); }
            }
        }

        // 3) body (optional)
        const bodyCfg = cfg.body || null;
        if (bodyCfg) {
            const bCfg = _deepMerge(_deepMerge({}, this._bodyDefaults), bodyCfg);

            if (!bCfg.surface && ctx.surface) bCfg.surface = ctx.surface;

            // Derive collider from surface if missing
            if (!bCfg.collider && surfCfg && surfCfg.type) {
                const t = String(surfCfg.type);
                if (t === "capsule") {
                    bCfg.collider = {
                        type: "capsule",
                        radius: (surfCfg.radius != null) ? surfCfg.radius : 0.35,
                        height: (surfCfg.height != null) ? surfCfg.height : 1.8
                    };
                } else if (t === "sphere") {
                    bCfg.collider = { type: "sphere", radius: (surfCfg.radius != null) ? surfCfg.radius : 0.5 };
                } else if (t === "box") {
                    // engine collider box may be size or halfExtents; keep your style
                    bCfg.collider = { type: "box", size: (surfCfg.size != null) ? surfCfg.size : 1 };
                }
            }

            ctx.body = this.engine.physics().body(bCfg);
            ctx.bodyId = idOf(ctx.body, "body");
        }

        // 4) components (optional)
        const comps = cfg.components;
        if (comps && typeof comps === "object") {
            for (const key of Object.keys(comps)) {
                const v = comps[key];
                let data = v;

                if (typeof v === "function") {
                    try {
                        data = v({
                            entityId: ctx.entityId,
                            surface: ctx.surface,
                            body: ctx.body,
                            surfaceId: ctx.surfaceId,
                            bodyId: ctx.bodyId,
                            cfg: cfg
                        });
                    } catch (e) {
                        _logErr(this.engine, "[ENT] component builder failed: " + key + " err=" + e);
                        continue;
                    }
                }

                try { this.engine.entity().setComponent(ctx.entityId, key, data); }
                catch (e) { _logErr(this.engine, "[ENT] setComponent failed: " + key + " err=" + e); }
            }
        }

        if (debug) {
            _logInfo(
                this.engine,
                "[ENT] created name=" + name +
                " entityId=" + (ctx.entityId | 0) +
                " surfaceId=" + (ctx.surfaceId | 0) +
                " bodyId=" + (ctx.bodyId | 0)
            );
        }

        return new EntityHandle(this.engine, ctx);
    }

    // ---------- helpers for scripts ----------

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
        if (!n) return this;
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
    if (!engine) throw new Error("[ENT] engine is required");

    const api = new EntApi(engine, K);

    // freeze like other builtins
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

// META (adult contract)
create.META = {
    name: "entity",
    globalName: "ENT",
    version: "1.0.0",
    description: "Declarative entity builder (entity + surface + body + components) with builder presets",
    engineMin: "0.1.0"
};

module.exports = create;