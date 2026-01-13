// FILE: resources/kalitech/builtin/helpers/entity/EntApi.js
"use strict";

const {req, vec3, deepMerge, subsystem} = require("./EntUtil.js");
const {idOf} = require("./IdExtractor.js");
const {PhysicsBinding} = require("./PhysicsBinding.js");
const {EntityHandle} = require("./EntityHandle.js");
const {EntBuilder} = require("./EntBuilder.js");

const {EntityCore} = require("./EntityCore.js");
const {resolveBodyAccess} = require("./BodyAccessResolver.js");

function inferShapeFromCfg(cfg, surfaceCfg, bodyCfg) {
    const out = {mass: 0, radius: 0, height: 0};

    if (bodyCfg && typeof bodyCfg === "object") {
        if (bodyCfg.mass != null) out.mass = +bodyCfg.mass || 0;

        const col = bodyCfg.collider;
        if (col && typeof col === "object") {
            if (col.radius != null) out.radius = +col.radius || 0;
            if (col.height != null) out.height = +col.height || 0;
            if (col.size != null && out.radius === 0) out.radius = +col.size || 0;
        }
    }

    if (surfaceCfg && typeof surfaceCfg === "object") {
        if (out.radius === 0 && surfaceCfg.radius != null) out.radius = +surfaceCfg.radius || 0;
        if (out.height === 0 && surfaceCfg.height != null) out.height = +surfaceCfg.height || 0;
        if (out.radius === 0 && surfaceCfg.size != null) out.radius = +surfaceCfg.size || 0;

        if (out.mass === 0 && surfaceCfg.physics && typeof surfaceCfg.physics === "object") {
            if (surfaceCfg.physics.mass != null) out.mass = +surfaceCfg.physics.mass || 0;
        }
    }

    return out;
}

function attachCoreOrProxy(handle, core) {
    if (handle && Object.isExtensible(handle)) {
        Object.defineProperty(handle, "core", {
            value: core,
            enumerable: true,
            configurable: false,
            writable: false
        });
        return handle;
    }

    return new Proxy(handle, {
        get(t, p) {
            if (p === "core") return core;
            return t[p];
        },
        set() {
            throw new Error("[ENT] EntityHandle is immutable");
        }
    });
}

function isUuidString(s) {
    if (typeof s !== "string") return false;
    const x = s.trim();
    return x.length >= 32 && x.indexOf("-") > 0;
}

function attachSurfaceSmart(surfApi, surfaceHandle, uuid) {
    if (uuid && typeof surfApi.attachEntity === "function") {
        surfApi.attachEntity(surfaceHandle, uuid);
        return;
    }
    throw new Error("[ENT] surface attach missing (attachEntity(uuid) is required)");
}

class EntApi {
    constructor(engine, K) {
        this.engine = engine;
        this.K = K || (globalThis.__kalitech || Object.create(null));

        req(engine, "[ENT] engine is required");
        subsystem(engine, "entity");
        subsystem(engine, "mesh");
        subsystem(engine, "surface");
        subsystem(engine, "physics");

        req(engine.log && typeof engine.log === "function", "[ENT] engine.log() is required");
        this._log = engine.log();
        req(this._log && this._log.info && this._log.warn && this._log.error, "[ENT] engine.log() must provide info/warn/error");

        this._physBind = new PhysicsBinding(engine);

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
                physics: {mass: 80, lockRotation: true}
            },
            body: {
                mass: 80,
                friction: 0.9,
                restitution: 0.0,
                damping: {linear: 0.15, angular: 0.95},
                lockRotation: true,
                collider: {type: "capsule", radius: 0.35, height: 1.8}
            },
            attachSurface: true,
            debug: true
        };

        this._presets.capsule = {
            name: "entity",
            surface: {type: "capsule", name: "entity.capsule", radius: 0.35, height: 1.8, pos: [0, 3, 0], attach: true},
            attachSurface: true
        };
        this._presets.box = {
            name: "entity",
            surface: {type: "box", name: "entity.box", size: 1, pos: [0, 3, 0], attach: true},
            attachSurface: true
        };
        this._presets.sphere = {
            name: "entity",
            surface: {type: "sphere", name: "entity.sphere", radius: 0.5, pos: [0, 3, 0], attach: true},
            attachSurface: true
        };

        this._bodyDefaults = {
            mass: 1,
            friction: 0.9,
            restitution: 0.0,
            damping: {linear: 0.15, angular: 0.95},
            lockRotation: false
        };
    }

    preset(name, cfg) {
        const n = String(name || "");
        if (!n) throw new Error("[ENT] preset(name,cfg): name is required");
        if (!cfg || typeof cfg !== "object") throw new Error("[ENT] preset(name,cfg): cfg object is required");
        this._presets[n] = deepMerge(deepMerge({}, this._presets[n] || {}), cfg);
        return this;
    }

    bodyDefaults(cfg) {
        this._bodyDefaults = deepMerge(deepMerge({}, this._bodyDefaults), cfg || {});
        return this;
    }

    presets() {
        return Object.keys(this._presets);
    }

    $(presetName) {
        return new EntBuilder(this, presetName ? String(presetName) : "");
    }

    player$(cfg) {
        return this.$("player").merge(cfg);
    }

    capsule$(cfg) {
        return this.$("capsule").merge(cfg);
    }

    box$(cfg) {
        return this.$("box").merge(cfg);
    }

    sphere$(cfg) {
        return this.$("sphere").merge(cfg);
    }

    create(cfg) {
        cfg = (cfg && typeof cfg === "object") ? cfg : {};
        const debug = !!cfg.debug;

        const ctx = {
            uuid: "",         // ✅ primary
            surface: null,
            body: null,
            surfaceId: 0,
            bodyId: 0,
            _destroyers: []
        };

        const ent = subsystem(this.engine, "entity");
        const mesh = subsystem(this.engine, "mesh");
        const surfApi = subsystem(this.engine, "surface");
        const phys = subsystem(this.engine, "physics");

        // 1) entity (UUID-only)
        const name = String(cfg.name || "entity");
        const created = ent.create(name);

        if (typeof created !== "string" || !isUuidString(created)) {
            throw new Error("[ENT] engine.entity().create() must return UUID string, got: " + String(created));
        }

        ctx.uuid = created.trim();

        // 2) surface (optional)
        const surfCfg = cfg.surface || null;
        const bodyCfg = cfg.body || null;

        let surfaceHadPhysics = false;

        if (surfCfg) {
            const sCfg = deepMerge({}, surfCfg);

            if (sCfg.pos != null) sCfg.pos = vec3(sCfg.pos, 0, 0, 0);

            // IMPORTANT: no double body creation
            if (sCfg.physics != null) {
                surfaceHadPhysics = true;
                if (bodyCfg) {
                    delete sCfg.physics;
                    surfaceHadPhysics = false;
                }
            }

            ctx.surface = mesh.create(sCfg);
            ctx.surfaceId = idOf(ctx.surface, "surface");

            const attachSurface = (cfg.attachSurface != null) ? !!cfg.attachSurface : true;
            if (attachSurface) {
                attachSurfaceSmart(surfApi, ctx.surface, ctx.uuid);
            }
        }

        // 3) body (optional)
        if (bodyCfg) {
            const made = this._physBind.createBody(this._bodyDefaults, bodyCfg, ctx.surface, surfCfg);
            ctx.body = made.body;
            ctx.bodyId = made.bodyId;
        } else if (surfaceHadPhysics && ctx.surface) {
            const bid = this._physBind.resolveBodyIdBySurface(ctx.surfaceId || ctx.surface);
            if (bid > 0) {
                ctx.bodyId = bid;
                ctx.body = null;
            }
        }

        // 4) components (optional) — UUID-first setComponent
        const comps = cfg.components;
        if (comps && typeof comps === "object") {
            for (const key of Object.keys(comps)) {
                const v = comps[key];
                let data = v;

                if (typeof v === "function") {
                    data = v({
                        uuid: ctx.uuid,
                        surface: ctx.surface,
                        body: ctx.body,
                        surfaceId: ctx.surfaceId,
                        bodyId: ctx.bodyId,
                        cfg
                    });
                }

                // ✅ preferred: setComponent(uuid,type,value)
                if (typeof ent.setComponent === "function") {
                    // try uuid signature first (Java side should have it)
                    ent.setComponent(ctx.uuid, key, data);
                } else {
                    throw new Error("[ENT] engine.entity().setComponent(uuid,type,value) missing");
                }
            }
        }

        if (debug) {
            this._log.info(
                "[ENT] created name=" + name +
                " uuid=" + ctx.uuid +
                " surfaceId=" + (ctx.surfaceId | 0) +
                " bodyId=" + (ctx.bodyId | 0)
            );
        }

        // 5) handle
        const handle = new EntityHandle(this.engine, ctx);

        // 6) core (embedded)
        if ((ctx.bodyId | 0) > 0) {
            const bodyAccess = resolveBodyAccess(phys, ctx.body, ctx.bodyId | 0);

            const sh = inferShapeFromCfg(cfg, surfCfg, bodyCfg);
            const core = new EntityCore()
                .configureShape(sh.mass, sh.radius, sh.height)
                .attach(handle, ctx.body, bodyAccess);

            return attachCoreOrProxy(handle, core);
        }

        return handle;
    }

    idOf(h, kind) {
        return idOf(h, kind);
    }

    uuidOf(ref) {
        if (ref == null) return "";

        if (typeof ref === "string") return isUuidString(ref) ? ref.trim() : "";

        if (typeof ref === "object") {
            if (typeof ref.uuidString === "function") return String(ref.uuidString() || "");
            if (typeof ref.uuid === "function") return String(ref.uuid() || "");
            if (typeof ref.uuid === "string") return String(ref.uuid || "");
        }

        return "";
    }
}

module.exports = {EntApi};
