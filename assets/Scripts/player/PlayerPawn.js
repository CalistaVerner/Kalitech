// FILE: Scripts/player/PlayerPawn.js
"use strict";

const U = require("./util.js");
const FrameContext = require("./FrameContext.js");
const CharacterConfig = require("./CharacterConfig.js");
const {PlayerEntityFactory} = require("./PlayerEntityFactory.js");
const InputRouter = require("./systems/InputRouter.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function apiFrom(ctx) {
    if (ctx && typeof ctx.api === "function") return ctx.api();
    if (ctx && ctx.engine && typeof ctx.engine.api === "function") return ctx.engine.api();
    if (ctx && typeof ctx.engineApi === "function") return ctx.engineApi();
    throw new Error("[player] ctx must provide api()");
}

function buildDomains(ctx) {
    const E = apiFrom(ctx);

    const ENGINE = req(globalThis.ENGINE, "[player] globalThis.ENGINE is required");
    const physics = req(ENGINE.physics, "[player] ENGINE.physics is required");

    const input = req(E.input && E.input(), "[player] engine.input() required");
    const camera = req(E.camera && E.camera(), "[player] engine.camera() required");
    const assets = req(E.assets && E.assets(), "[player] engine.assets() required");

    const entity = req(E.entity && E.entity(), "[player] engine.entity() required");
    const mesh = req(E.mesh && E.mesh(), "[player] engine.mesh() required");
    const surface = req(E.surface && E.surface(), "[player] engine.surface() required");

    const hud = req((typeof HUD !== "undefined" && HUD) ? HUD : null, "[player] HUD builtin required");
    const hudNative = (typeof E.hud === "function") ? E.hud() : null;

    const bus = (typeof E.bus === "function") ? E.bus() : null;

    return Object.freeze({
        ctx,
        engine: E,
        physics,
        input,
        camera,
        assets,
        entity,
        mesh,
        surface,
        bus,
        hud,
        hudNative
    });
}

function readUuidFromHandle(h) {
    if (!h) return "";
    if (typeof h.uuidString === "function") return String(h.uuidString() || "");
    if (typeof h.uuid === "function") return String(h.uuid() || "");
    if (typeof h.uuid === "string") return String(h.uuid || "");
    return "";
}

class PlayerPawn {
    constructor(ctx, cfg) {
        this.ctx = req(ctx, "[PlayerPawn] ctx required");
        this.cfg = cfg || Object.create(null);

        this.d = null;

        this.characterCfg = new CharacterConfig();
        this.frame = new FrameContext();
        this.inputRouter = null;

        this.handle = null; // ENT.create(...) result
        this.core = null;   // handle.core (engine-filled)

        this.alive = false;
    }

    get entity() {
        return this.handle;
    }

    get uuid() {
        return readUuidFromHandle(this.handle);
    }

    get bodyAccess() {
        return this.core.bodyAccess;
    }

    get bodyId() {
        return this.core.bodyId | 0;
    }

    get surfaceId() {
        return this.core.surfaceId | 0;
    }

    get state() {
        return this.core.state;
    }

    init() {
        if (this.alive) return this;

        this.cfg = U.deepMerge({
            character: {radius: 0.35, height: 1.80, mass: 80.0, eyeHeight: 1.65},
            spawn: {pos: {x: 135, y: -10, z: -334}, radius: 0.35, height: 1.80, mass: 80.0},
            camera: {type: "third"},
            ui: {},
            events: {enabled: true},
            input: {},
            movement: {},
            shoot: {}
        }, this.cfg);

        this.d = buildDomains(this.ctx);

        this.inputRouter = new InputRouter(this.d.input, this.cfg.input);

        const factory = new PlayerEntityFactory(this);
        this.handle = factory.create(this.cfg.spawn);

        const uuid = this.uuid;
        if (!uuid) throw new Error("[PlayerPawn] player uuid missing (UUID-only)");

        this.core = this.handle.core;
        if (!this.core) throw new Error("[PlayerPawn] ENT.create() must return {core}");
        if (!this.core.bodyAccess) throw new Error("[PlayerPawn] core.bodyAccess missing (engine must fill EntityCore)");
        if ((this.core.bodyId | 0) <= 0) throw new Error("[PlayerPawn] invalid core.bodyId");

        // keep convenience (some controllers may read core.uuid)
        this.core.uuid = uuid;

        if (typeof this.frame.probeGroundCapsule !== "function") {
            throw new Error("[PlayerPawn] FrameContext.probeGroundCapsule required");
        }

        this.characterCfg.loadFrom(this.cfg, this.cfg.movement);

        // Ground probe contract: EntityCore.syncPhysics calls probe(core)
        this.core.setGroundProbe((core) => {
            const probe = this.frame.probeGroundCapsule;
            return (probe.length >= 3)
                ? probe.call(this.frame, core.bodyAccess, this.characterCfg, core.bodyId | 0)
                : probe.call(this.frame, core.bodyAccess, this.characterCfg);
        });
        this.alive = true;
        return this;
    }

    beginFrame(tpf) {
        if (!this.alive) throw new Error("[PlayerPawn] beginFrame on dead pawn");
        if (!Number.isFinite(tpf)) throw new Error("[PlayerPawn] tpf must be finite");

        const snap = this.d.input.consumeSnapshot();
        this.frame.begin(this, tpf, snap);

        this.inputRouter.read(this.frame);

        this.frame.bodyAccess = this.core.bodyAccess;
        this.frame.bodyId = this.core.bodyId | 0;
    }

    syncPose() {
        if (!this.alive) throw new Error("[PlayerPawn] syncPose on dead pawn");

        const s = this.core.syncPhysics();
        const pose = this.frame.pose;

        pose.x = s.x;
        pose.y = s.y;
        pose.z = s.z;
        pose.vx = s.vx;
        pose.vy = s.vy;
        pose.vz = s.vz;
        pose.speed = s.speed;
        pose.fallSpeed = (s.vy < 0) ? -s.vy : 0;

        pose.rx = s.rx;
        pose.ry = s.ry;
        pose.rz = s.rz;
        pose.rw = s.rw;
        pose.avx = s.avx;
        pose.avy = s.avy;
        pose.avz = s.avz;

        pose.grounded = s.grounded;
    }

    endFrame() {
        if (!this.alive) throw new Error("[PlayerPawn] endFrame on dead pawn");
        this.d.input.endFrame();
    }

    destroy() {

    }

    getModel() {
        return this.core.model();
    }

    getBodyId() {
        return this.core.bodyId | 0;
    }

    getSurfaceId() {
        return this.core.surfaceId | 0;
    }

    getUuid() {
        return this.uuid;
    }
}

module.exports = {PlayerPawn};