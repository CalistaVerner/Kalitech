"use strict";

const U = require("./util.js");
const FrameContext = require("./FrameContext.js");
const CharacterConfig = require("./CharacterConfig.js");

const PlayerController = require("./PlayerController.js");
const PlayerCamera = require("./PlayerCamera.js");
const PlayerUI = require("./PlayerUI.js");
const PlayerEvents = require("./PlayerEvents.js");
const { PlayerEntityFactory } = require("./PlayerEntityFactory.js");

function engineApiFrom(ctx) {
    if (ctx && typeof ctx.api === "function") return ctx.api();
    if (ctx && ctx.engine && typeof ctx.engine.api === "function") return ctx.engine.api();
    if (ctx && typeof ctx.engineApi === "function") return ctx.engineApi();
    throw new Error("[player] ctx must provide api()");
}

function must(x, msg) {
    if (!x) throw new Error(msg);
    return x;
}

function resolveDomains(ctx) {
    const E = engineApiFrom(ctx);

    const physics = (typeof PHYS !== "undefined" && PHYS) ? PHYS : null;
    if (!physics) throw new Error("[player] PHYS builtin required (API3)");

    return Object.freeze({
        ctx,
        engine: E,
        physics,          // <- теперь это PHYS (API3)
        input: must(E.input(), "[player] engine.input() required"),
        camera: must(E.camera(), "[player] engine.camera() required"),
        assets: must(E.assets(), "[player] engine.assets() required"),
        entity: must(E.entity && E.entity(), "[player] engine.entity() required"),
        mesh: must(E.mesh && E.mesh(), "[player] engine.mesh() required"),
        surface: must(E.surface && E.surface(), "[player] engine.surface() required"),
        bus: (typeof E.bus === "function") ? E.bus() : null,
        hud: must((typeof HUD !== "undefined" && HUD) ? HUD : null, "[player] HUD builtin required"),
        hudNative: (typeof E.hud === "function") ? E.hud() : null
    });
}


function pickFirst(obj, names) {
    for (let i = 0; i < names.length; i++) {
        const n = names[i];
        if (obj && typeof obj[n] === "function") return n;
    }
    return "";
}

function resolveBodyAccess(physics, bodyHandle, bodyId) {
    if (!physics) throw new Error("[player] physics missing");
    bodyId = bodyId | 0;
    if (bodyId <= 0) throw new Error("[player] invalid bodyId=" + bodyId);

    const PHYS_POS = pickFirst(physics, ["position", "location", "getPosition", "getLocation", "bodyPosition"]);
    const PHYS_VGET = pickFirst(physics, ["velocity", "linearVelocity", "getVelocity", "getLinearVelocity", "vel", "getVel"]);
    const PHYS_VSET = pickFirst(physics, ["setVelocity", "setLinearVelocity", "velocitySet", "linearVelocitySet", "setVel"]);
    const PHYS_YAW = pickFirst(physics, ["yaw", "setYaw", "bodyYaw"]);
    const PHYS_TP = pickFirst(physics, ["teleport", "warp", "setPosition", "setLocation", "bodyTeleport"]);

    const BODY_POS = bodyHandle && typeof bodyHandle.position === "function" ? "position" : "";
    const BODY_VGET = bodyHandle ? pickFirst(bodyHandle, ["velocity", "linearVelocity", "getVelocity", "getLinearVelocity", "vel", "getVel"]) : "";
    const BODY_VSET = bodyHandle ? pickFirst(bodyHandle, ["setVelocity", "setLinearVelocity", "velocity", "linearVelocity", "setVel"]) : "";
    const BODY_YAW = bodyHandle ? pickFirst(bodyHandle, ["yaw", "setYaw"]) : "";
    const BODY_TP = bodyHandle ? pickFirst(bodyHandle, ["teleport", "warp", "setPosition", "position"]) : "";
    const BODY_IMP = bodyHandle ? pickFirst(bodyHandle, ["applyCentralImpulse", "applyImpulse", "impulse", "applyForce"]) : "";

    const position = BODY_POS
        ? () => bodyHandle.position()
        : PHYS_POS
            ? () => physics[PHYS_POS](bodyId)
            : null;

    if (!position) throw new Error("[player] cannot resolve position getter (body.position or physics.position/location)");

    const getVel = BODY_VGET
        ? () => bodyHandle[BODY_VGET]()
        : PHYS_VGET
            ? () => physics[PHYS_VGET](bodyId)
            : null;

    if (!getVel) throw new Error("[player] cannot resolve velocity getter (body or physics)");

    const setVel = BODY_VSET
        ? (v) => bodyHandle[BODY_VSET](v)
        : PHYS_VSET
            ? (v) => physics[PHYS_VSET](bodyId, v)
            : null;

    const applyImpulse = BODY_IMP
        ? (ix, iy, iz) => bodyHandle[BODY_IMP]({x: ix, y: iy, z: iz})
        : null;

    const mode = setVel ? "SET_VEL" : (applyImpulse ? "IMPULSE" : "");
    if (!mode) throw new Error("[player] cannot resolve velocity setter nor impulse");

    const setYaw = BODY_YAW
        ? (yaw) => bodyHandle[BODY_YAW](+yaw || 0)
        : PHYS_YAW
            ? (yaw) => physics[PHYS_YAW](bodyId, +yaw || 0)
            : null;

    const teleport = BODY_TP
        ? (x, y, z) => bodyHandle[BODY_TP]({x, y, z})
        : PHYS_TP
            ? (x, y, z) => physics[PHYS_TP](bodyId, {x, y, z})
            : null;

    return Object.freeze({
        bodyId,
        mode,
        position,
        getVel,
        setVel: setVel || null,
        applyImpulse: applyImpulse || null,
        setYaw: setYaw || null,
        teleport: teleport || null
    });
}

class Player {
    constructor(ctx, cfg) {
        this.ctx = ctx;
        this.cfg = cfg || Object.create(null);

        this.d = null;
        this.alive = false;

        this.entity = null;
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        this.body = null;            // может быть null (ENT.create не обязан отдавать handle)
        this.bodyAccess = null;      // ВСЕГДА валидный контракт (physics/id + optional handle)

        this.characterCfg = new CharacterConfig();
        this.frame = new FrameContext();

        this.factory = new PlayerEntityFactory(this);

        this.controller = new PlayerController(this);
        this.camera = new PlayerCamera(this);
        this.ui = new PlayerUI(this);
        this.events = new PlayerEvents(this);
    }

    getBodyId() {
        return this.bodyId | 0;
    }

    getEntityId() {
        return this.entityId | 0;
    }

    getSurfaceId() {
        return this.surfaceId | 0;
    }

    getModel() {
        return (this.entity && this.entity.getModel) ? this.entity.getModel() : null;
    }

    init() {
        if (this.alive) return this;

        this.d = resolveDomains(this.ctx);

        this.cfg = U.deepMerge({
            character: { radius: 0.35, height: 1.80, mass: 80.0, eyeHeight: 1.65 },
            spawn: { pos: { x: 129, y: 3, z: -300 }, radius: 0.35, height: 1.80, mass: 80.0 },
            camera: {type: "third"},
            ui: {},
            events: {enabled: true}
        }, this.cfg);

        if (this.d.hudNative && typeof this.d.hudNative.setCursorEnabled === "function") {
            this.d.hudNative.setCursorEnabled(false, true);
        }

        this.ui.create();

        this.entity = this.factory.create(this.cfg.spawn);
        this.entityId = this.entity.entityId | 0;
        this.surfaceId = this.entity.surfaceId | 0;
        this.bodyId = this.entity.bodyId | 0;

        if (this.bodyId <= 0) throw new Error("[player] invalid bodyId=" + this.bodyId);

        this.body = this.entity.body || null;
        this.bodyAccess = resolveBodyAccess(this.d.physics, this.body, this.bodyId);

        const movCfg = this.controller.getMovementCfg();
        this.characterCfg.loadFrom(this.cfg, movCfg);

        this.camera.attach();

        this.events.emit("player.spawn", {entityId: this.entityId, bodyId: this.bodyId});

        if (this.ctx && typeof this.ctx.state === "function") {
            this.ctx.state().set("player", {
                alive: true,
                entityId: this.entityId,
                surfaceId: this.surfaceId,
                bodyId: this.bodyId
            });
        }

        this.alive = true;
        return this;
    }

    _syncPose(frame) {
        const p = this.bodyAccess.position();
        frame.pose.x = U.vx(p);
        frame.pose.y = U.vy(p);
        frame.pose.z = U.vz(p);

        const v = this.bodyAccess.getVel();
        const vx = U.vx(v);
        const vy = U.vy(v);
        const vz = U.vz(v);

        frame.pose.vx = vx;
        frame.pose.vy = vy;
        frame.pose.vz = vz;

        frame.pose.speed = Math.hypot(vx, vy, vz);
        frame.pose.fallSpeed = (vy < 0) ? -vy : 0;

        const probe = frame.probeGroundCapsule;
        if (typeof probe === "function") {
            if (probe.length >= 3) frame.pose.grounded = probe.call(frame, this.bodyAccess, this.characterCfg, this.bodyId | 0);
            else frame.pose.grounded = probe.call(frame, this.bodyAccess, this.characterCfg);
        } else {
            frame.pose.grounded = false;
        }
    }

    _syncView(frame) {
        frame.view.yaw = this.camera.getYaw();
        frame.view.pitch = this.camera.getPitch();
        frame.view.type = this.camera.getType();
    }

    update(tpf) {
        if (!this.alive) return;

        const snap = this.d.input.consumeSnapshot();
        this.frame.begin(this, tpf, snap);

        this.frame.bodyAccess = this.bodyAccess;
        this.frame.bodyId = this.bodyId | 0;

        this._syncPose(this.frame);

        this.camera.update(this.frame);
        this._syncView(this.frame);

        this.controller.update(this.frame);

        this.events.tick(this.frame);
        this.ui.refresh();

        if (typeof this.d.input.endFrame === "function") this.d.input.endFrame();
    }

    destroy() {
        if (!this.alive) return;

        this.events.destroy();
        this.controller.destroy();
        this.camera.destroy();
        this.ui.destroy();

        if (this.entity) this.entity.destroy(this.d.physics);

        this.entity = null;
        this.body = null;
        this.bodyAccess = null;

        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        if (this.ctx && typeof this.ctx.state === "function") this.ctx.state().remove("player");

        this.d = null;
        this.alive = false;
    }
}

let _player = null;

module.exports.create = function create(ctx, cfg) {
    const p = new Player(ctx, cfg || null);
    p.init();
    return p;
};

module.exports.init = function init(ctx, cfg) {
    if (_player && _player.alive) return _player;
    _player = new Player(ctx, cfg || null);
    _player.init();
    return _player;
};

module.exports.update = function update(ctx, tpf) {
    if (_player) _player.update(tpf);
};
module.exports.destroy = function destroy(ctx) {
    if (_player) _player.destroy();
    _player = null;
};

module.exports.Player = Player;