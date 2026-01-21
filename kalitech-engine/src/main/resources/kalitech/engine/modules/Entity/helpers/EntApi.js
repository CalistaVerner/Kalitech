"use strict";

const {req, vec3, deepMerge, subsystem} = require("./EntUtil.js");
const {idOf} = require("./IdExtractor.js");
const {PhysicsBinding} = require("./PhysicsBinding.js");
const {EntityHandle} = require("./EntityHandle.js");
const {EntBuilder} = require("./EntBuilder.js");
const {EntityCore} = require("./EntityCore.js");
const {resolveBodyAccess} = require("./BodyAccessResolver.js");

function isUuidString(s) {
    if (typeof s !== "string") return false;
    const x = s.trim();
    return x.length >= 32 && x.indexOf("-") > 0;
}

function safeCall(fn) {
    try {
        fn();
    } catch (_ignored) {
    }
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

        const engine = this.engine;
        const ent = subsystem(engine, "entity");
        const mesh = subsystem(engine, "mesh");
        const surfApi = subsystem(engine, "surface");
        const phys = subsystem(engine, "physics");

        const ctx = {
            uuid: "",
            surface: null,
            body: null,
            surfaceId: 0,
            bodyId: 0,
            _destroyers: []
        };

        let createdUuid = "";
        let createdSurfaceId = 0;
        let createdBodyId = 0;

        try {
            const name = String(cfg.name || "entity");

            const created = ent.create(name);
            if (typeof created !== "string" || !isUuidString(created)) {
                throw new Error("[ENT] engine.entity().create() must return UUID string, got: " + String(created));
            }
            createdUuid = created.trim();
            ctx.uuid = createdUuid;

            const surfCfg = cfg.surface || null;
            const bodyCfg = cfg.body || null;

            let surfaceHadPhysics = false;

            if (surfCfg) {
                const sCfg = deepMerge({}, surfCfg);
                if (sCfg.pos != null) sCfg.pos = vec3(sCfg.pos, 0, 0, 0);

                if (sCfg.physics != null) {
                    surfaceHadPhysics = true;
                    if (bodyCfg) {
                        delete sCfg.physics;
                        surfaceHadPhysics = false;
                    }
                }

                ctx.surface = mesh.create(sCfg);
                ctx.surfaceId = (idOf(ctx.surface, "surface") | 0);
                createdSurfaceId = ctx.surfaceId | 0;

                const attachSurface = (cfg.attachSurface != null) ? !!cfg.attachSurface : true;
                if (attachSurface) {
                    if (typeof surfApi.attachEntity !== "function") {
                        throw new Error("[ENT] surface attach missing: engine.surface().attachEntity(surfaceHandle, uuid)");
                    }
                    surfApi.attachEntity(ctx.surface, ctx.uuid);
                }
            }

            if (bodyCfg) {
                const made = this._physBind.createBody(this._bodyDefaults, bodyCfg, ctx.surface, surfCfg);
                ctx.body = made.body || null;
                ctx.bodyId = (made.bodyId | 0);
                createdBodyId = ctx.bodyId | 0;
            } else if (surfaceHadPhysics && ctx.surface) {
                const bid = this._physBind.resolveBodyIdBySurface(ctx.surfaceId || ctx.surface);
                if ((bid | 0) > 0) {
                    ctx.bodyId = bid | 0;
                    ctx.body = null;
                    createdBodyId = ctx.bodyId | 0;
                }
            }

            const requireCore = (cfg.requireCore !== false);
            if (requireCore && (ctx.bodyId | 0) <= 0) {
                if (!ctx.surface) {
                    throw new Error("[ENT] core requires bodyId>0. Provide cfg.body or cfg.surface with collider. uuid=" + ctx.uuid);
                }
                const made = this._physBind.createBody(this._bodyDefaults, {}, ctx.surface, surfCfg);
                ctx.body = made.body || null;
                ctx.bodyId = (made.bodyId | 0);
                createdBodyId = ctx.bodyId | 0;

                if ((ctx.bodyId | 0) <= 0) {
                    throw new Error("[ENT] core auto-body failed (physics.body returned invalid id). uuid=" + ctx.uuid);
                }
            }

            const comps = cfg.components;
            if (comps && typeof comps === "object") {
                if (typeof ent.setComponent !== "function") {
                    throw new Error("[ENT] engine.entity().setComponent(uuid,type,value) missing");
                }
                for (const key of Object.keys(comps)) {
                    const v = comps[key];
                    const data = (typeof v === "function")
                        ? v({
                            uuid: ctx.uuid,
                            surface: ctx.surface,
                            body: ctx.body,
                            surfaceId: ctx.surfaceId,
                            bodyId: ctx.bodyId,
                            cfg
                        })
                        : v;

                    ent.setComponent(ctx.uuid, String(key), data);
                }
            }

            const handle = new EntityHandle(engine, ctx);

            let core = null;
            if (requireCore) {
                const bodyAccess = resolveBodyAccess(phys, ctx.body, ctx.bodyId | 0);
                core = new EntityCore().attach(handle, ctx.body, bodyAccess);

                // Hard contract for player: identity must be available immediately
                core.uuid = ctx.uuid;
                core.bodyId = ctx.bodyId | 0;
                core.surfaceId = ctx.surfaceId | 0;
                if (core.state && typeof core.state === "object") {
                    core.state.uuid = core.uuid;
                }

                // Optional: hydrate from snapshot if supported
                if (typeof core.hydrate === "function" && typeof ent.snapshot === "function") {
                    const snap = ent.snapshot(ctx.uuid);
                    core.hydrate(snap);
                }

                if (cfg.shape) {
                    const sh = cfg.shape || {};
                    core.configureShape(sh.mass, sh.radius, sh.height);
                }
                if (typeof cfg.groundProbe === "function") {
                    core.setGroundProbe(cfg.groundProbe);
                }
            }

            handle.core = core;

            if (debug) {
                this._log.info(
                    "[ENT] created name=" + name +
                    " uuid=" + ctx.uuid +
                    " surfaceId=" + (ctx.surfaceId | 0) +
                    " bodyId=" + (ctx.bodyId | 0) +
                    " core=" + (core ? "yes" : "no")
                );
            }

            return Object.freeze({
                core,
                handle,
                uuid: handle.uuidString(),
                surfaceId: handle.surfaceHandleId(),
                bodyId: handle.bodyHandleId()
            });

        } catch (e) {
            safeCall(() => {
                if ((createdBodyId | 0) > 0 && typeof phys.remove === "function") phys.remove(createdBodyId | 0);
            });
            safeCall(() => {
                if ((createdSurfaceId | 0) > 0 && typeof surfApi.drop === "function") surfApi.drop(createdSurfaceId | 0, true);
            });
            safeCall(() => {
                if (createdUuid && typeof ent.destroy === "function") ent.destroy(createdUuid);
            });
            throw e;
        }
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