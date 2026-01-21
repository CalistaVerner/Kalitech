// FILE: resources/kalitech/builtin/helpers/entity/EntApi.js
"use strict";

const {req, vec3, deepMerge, subsystem} = require("./EntUtil.js");
const {idOf} = require("./IdExtractor.js");
const {EntityHandle} = require("./EntityHandle.js");
const {EntBuilder} = require("./EntBuilder.js");
const {EntityCore} = require("./EntityCore.js");

function isObj(v) {
    return !!v && typeof v === "object";
}

function isUuidString(s) {
    if (typeof s !== "string") return false;
    const x = s.trim();
    return x.length >= 32 && x.indexOf("-") > 0;
}

class EntApi {
    constructor(engine) {
        this.engine = engine;

        req(engine, "[ENT] engine is required");
        subsystem(engine, "entity");
        subsystem(engine, "mesh");
        subsystem(engine, "surface");
        subsystem(engine, "physics");
        subsystem(engine, "log");

        this._log = engine.log();

        this._presets = Object.create(null);
        this._presets.capsule = {
            name: "entity",
            surface: {type: "capsule", name: "entity.capsule", radius: 0.35, height: 1.8, pos: [0, 3, 0], attach: true},
            body: {mass: 1},
            attachSurface: true
        };
        this._presets.box = {
            name: "entity",
            surface: {type: "box", name: "entity.box", size: 1, pos: [0, 3, 0], attach: true},
            body: {mass: 1},
            attachSurface: true
        };
        this._presets.sphere = {
            name: "entity",
            surface: {type: "sphere", name: "entity.sphere", radius: 0.5, pos: [0, 3, 0], attach: true},
            body: {mass: 1},
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
        req(isObj(cfg), "[ENT] preset(name,cfg): cfg object is required");
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

    capsule$(cfg) {
        return this.$("capsule").merge(cfg);
    }

    box$(cfg) {
        return this.$("box").merge(cfg);
    }

    sphere$(cfg) {
        return this.$("sphere").merge(cfg);
    }

    /**
     * Deterministic entity creation.
     * - JS creates surface/body (if requested)
     * - JS writes "binding" component into ECS (authoritative for UI)
     * - No auto-body, no surface.physics magic
     */
    create(cfg) {
        cfg = isObj(cfg) ? cfg : {};
        const debug = !!cfg.debug;

        const engine = this.engine;
        const ent = subsystem(engine, "entity");
        const mesh = subsystem(engine, "mesh");
        const surf = subsystem(engine, "surface");
        const phys = subsystem(engine, "physics");

        const name = String(cfg.name || "entity");
        const uuid = ent.create(name);
        if (!isUuidString(uuid)) throw new Error("[ENT] engine.entity().create(name) must return UUID string");

        let surfaceHandle = null;
        let surfaceId = 0;

        let bodyHandle = null;
        let bodyId = 0;

        try {
            if (cfg.surface) {
                const sCfg = deepMerge({}, cfg.surface);
                if (sCfg.pos != null) sCfg.pos = vec3(sCfg.pos, 0, 0, 0);

                surfaceHandle = mesh.create(sCfg);
                surfaceId = idOf(surfaceHandle, "surface") | 0;

                const attachSurface = (cfg.attachSurface != null) ? !!cfg.attachSurface : true;
                if (attachSurface) {
                    req(typeof surf.attachEntity === "function",
                        "[ENT] engine.surface().attachEntity(surfaceHandle, uuid) missing");
                    surf.attachEntity(surfaceHandle, uuid);
                }
            }

            if (cfg.body) {
                const bCfg = deepMerge(deepMerge({}, this._bodyDefaults), cfg.body);
                if (!bCfg.surface && surfaceHandle) bCfg.surface = surfaceHandle;

                bodyHandle = phys.body(bCfg);
                bodyId = idOf(bodyHandle, "body") | 0;
                if (bodyId <= 0) throw new Error("[ENT] engine.physics().body(cfg) returned invalid bodyId=" + bodyId);
            }

            // Authoritative binding for UI/editor
            if (typeof ent.setComponent === "function") {
                ent.setComponent(uuid, "binding", {surfaceId: surfaceId | 0, bodyId: bodyId | 0});
                ent.setComponent(uuid, "name", {value: name});
            }

            const handle = new EntityHandle(engine, {
                uuid,
                surfaceId: surfaceId | 0,
                bodyId: bodyId | 0,
                _destroyers: []
            });

            const core = new EntityCore(uuid, surfaceId | 0, bodyId | 0);
            handle.core = core;

            // Optional mirror hydrate (for UI)
            if (typeof ent.snapshot === "function") {
                const snap = ent.snapshot(uuid);
                if (snap) core.hydrate(snap);
            }

            if (debug) {
                this._log.info("[ENT] created name=" + name + " uuid=" + uuid +
                    " surfaceId=" + (surfaceId | 0) + " bodyId=" + (bodyId | 0));
            }

            return handle;

        } catch (e) {
            // Fail loudly; cleanup via Java destroy (which should cleanup attached resources)
            try {
                ent.destroy(uuid);
            } catch (_ignored) {
            }
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