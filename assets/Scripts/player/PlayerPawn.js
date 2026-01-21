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

        this.handle = null; // EntityHandle (authoritative access)
        this.core = null;   // Optional UI mirror only

        this.alive = false;

        this._groundProbe = null;
    }

    get entity() {
        return this.handle;
    }

    get uuid() {
        return readUuidFromHandle(this.handle);
    }

    get bodyAccess() {
        const h = this.handle;
        if (!h) throw new Error("[PlayerPawn] bodyAccess on null handle");
        if (typeof h.bodyRef !== "function") {
            throw new Error("[PlayerPawn] EntityHandle.bodyRef() required (canonical physics access)");
        }
        return h.bodyRef();
    }

    get bodyId() {
        const h = this.handle;
        if (!h) return 0;
        if (typeof h.requireBodyId === "function") return h.requireBodyId("PlayerPawn.bodyId");
        if (typeof h.hasBody === "function" && h.hasBody() && typeof h.bodyRef === "function") {
            // best-effort: bodyRef exists but no id accessor -> treat as contract violation
            throw new Error("[PlayerPawn] EntityHandle.requireBodyId() required");
        }
        return 0;
    }

    get surfaceId() {
        const h = this.handle;
        if (!h) return 0;
        // If your EntityHandle exposes surfaceId directly, use it.
        if (typeof h.surfaceId === "number") return h.surfaceId | 0;

        // Otherwise derive from snapshot "binding" (recommended).
        if (typeof h.snapshot === "function") {
            const snap = h.snapshot();
            const byName = snap && (snap.componentsByName || snap.components) || null;
            const binding = byName ? byName.binding : null;
            return binding ? (binding.surfaceId | 0) : 0;
        }
        return 0;
    }

    get state() {
        // Optional: if you still need "state", store it in ECS component and read from snapshot.
        const h = this.handle;
        if (!h || typeof h.snapshot !== "function") return null;
        const snap = h.snapshot();
        const byName = snap && (snap.componentsByName || snap.components) || null;
        return byName ? (byName.state || null) : null;
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

        // Optional UI mirror: keep if factory provides it, но НЕ требуем.
        this.core = this.handle && this.handle.core ? this.handle.core : null;
        if (this.core && typeof this.core === "object") {
            this.core.uuid = uuid;
        }

        // Canonical physics access validation
        if (typeof this.handle.bodyRef !== "function") {
            throw new Error("[PlayerPawn] EntityHandle.bodyRef() required");
        }
        if (typeof this.handle.hasBody === "function" && !this.handle.hasBody()) {
            throw new Error("[PlayerPawn] player entity has no physics body");
        }
        // requireBodyId is preferred (deterministic)
        if (typeof this.handle.requireBodyId === "function") {
            const id = this.handle.requireBodyId("PlayerPawn.init");
            if ((id | 0) <= 0) throw new Error("[PlayerPawn] invalid bodyId");
        }

        if (typeof this.frame.probeGroundCapsule !== "function") {
            throw new Error("[PlayerPawn] FrameContext.probeGroundCapsule required");
        }

        this.characterCfg.loadFrom(this.cfg, this.cfg.movement);

        // Ground probe uses canonical bodyAccess/bodyId
        this._groundProbe = () => {
            const probe = this.frame.probeGroundCapsule;
            const ba = this.bodyAccess;
            const bid = this.bodyId;

            return (probe.length >= 3)
                ? probe.call(this.frame, ba, this.characterCfg, bid)
                : probe.call(this.frame, ba, this.characterCfg);
        };

        this.alive = true;
        return this;
    }

    beginFrame(tpf) {
        if (!this.alive) throw new Error("[PlayerPawn] beginFrame on dead pawn");
        if (!Number.isFinite(tpf)) throw new Error("[PlayerPawn] tpf must be finite");

        const snap = this.d.input.consumeSnapshot();
        this.frame.begin(this, tpf, snap);

        this.inputRouter.read(this.frame);

        this.frame.bodyAccess = this.bodyAccess;
        this.frame.bodyId = this.bodyId;
    }

    syncPose() {
        if (!this.alive) throw new Error("[PlayerPawn] syncPose on dead pawn");

        // If FrameContext expects syncPhysics() on core, replace with local sampling:
        // - position/velocity/rotation/angularVelocity from bodyAccess
        // - grounded from ground probe
        const ba = this.bodyAccess;

        const p = ba.position();
        const v = ba.velocity();
        const r = ba.rotation();
        const av = ba.angularVelocity();

        const grounded = this._groundProbe ? !!this._groundProbe() : false;

        const pose = this.frame.pose;

        pose.x = +p.x;
        pose.y = +p.y;
        pose.z = +p.z;
        pose.vx = +v.x;
        pose.vy = +v.y;
        pose.vz = +v.z;
        pose.speed = Math.sqrt(pose.vx * pose.vx + pose.vz * pose.vz);
        pose.fallSpeed = (pose.vy < 0) ? -pose.vy : 0;

        pose.rx = +r.x;
        pose.ry = +r.y;
        pose.rz = +r.z;
        pose.rw = +r.w;
        pose.avx = +av.x;
        pose.avy = +av.y;
        pose.avz = +av.z;

        pose.grounded = grounded;
    }

    endFrame() {
        if (!this.alive) throw new Error("[PlayerPawn] endFrame on dead pawn");
        this.d.input.endFrame();
    }

    destroy() {
        const h = this.handle;
        this.handle = null;
        this.core = null;
        this.alive = false;

        if (h && typeof h.destroy === "function") {
            h.destroy();
        }
    }

    getModel() {
        // Если раньше это было core.model(), теперь либо:
        // 1) хранить модель в ECS/snapshot и читать, либо
        // 2) получать через surfaceId/SurfaceApi.
        if (this.core && typeof this.core.model === "function") return this.core.model();
        return null;
    }

    getBodyId() {
        return this.bodyId;
    }

    getSurfaceId() {
        return this.surfaceId;
    }

    getUuid() {
        return this.uuid;
    }
}

module.exports = {PlayerPawn};