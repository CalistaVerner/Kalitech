// FILE: resources/kalitech/builtin/helpers/entity/EntApi.js
"use strict";

const {req, isObj, vec3, deepMerge, subsystem} = require("./EntUtil.js");
const {idOf} = require("./IdExtractor.js");
const {PhysicsBinding} = require("./PhysicsBinding.js");
const {EntityHandle} = require("./EntityHandle.js");
const {EntBuilder} = require("./EntBuilder.js");

class EntApi {
    constructor(engine, K) {
        this.engine = engine;
        this.K = K || (globalThis.__kalitech || Object.create(null));

        // strict dependencies
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
            entityId: 0,
            surface: null,
            body: null,
            surfaceId: 0,
            bodyId: 0,
            _destroyers: []
        };

        const ent = subsystem(this.engine, "entity");
        const mesh = subsystem(this.engine, "mesh");
        const surfApi = subsystem(this.engine, "surface");

        // 1) entity
        const name = String(cfg.name || "entity");
        ctx.entityId = ent.create(name);

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
                surfApi.attach(ctx.surface, ctx.entityId);
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

                ent.setComponent(ctx.entityId, key, data);
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

    idOf(h, kind) {
        return idOf(h, kind);
    }
}

module.exports = {EntApi};