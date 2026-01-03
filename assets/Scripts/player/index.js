// FILE: Scripts/player/index.js
"use strict";

const U = require("./util.js");
const FrameContext = require("./FrameContext.js");
const CharacterConfig = require("./CharacterConfig.js");

const PlayerController = require("./PlayerController.js");
const PlayerCamera = require("./PlayerCamera.js");
const PlayerUI = require("./PlayerUI.js");
const PlayerEvents = require("./PlayerEvents.js");
const { PlayerEntityFactory } = require("./PlayerEntityFactory.js");

class PlayerDomain {
    constructor(player) {
        this.player = player;
        this.ids = { entityId: 0, surfaceId: 0, bodyId: 0 };
        this.input = { ax: 0, az: 0, run: false, jump: false, lmbDown: false, lmbJustPressed: false };
        this.view = { yaw: 0, pitch: 0, type: "third" };
        this.pose = { x: 0, y: 0, z: 0, vx: 0, vy: 0, vz: 0, grounded: false, speed: 0, fallSpeed: 0 };
    }

    syncIds(p) {
        this.ids.entityId = p.entityId | 0;
        this.ids.surfaceId = p.surfaceId | 0;
        this.ids.bodyId = p.bodyId | 0;
    }
}

class Player {
    constructor(ctx, cfg) {
        this.ctx = ctx;
        this.cfg = cfg || Object.create(null);

        this.alive = false;

        this.entity = null;
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        this.body = null;

        // IMPORTANT: single source for "model handle" provided by scene/builder
        // PlayerEntityFactory will pick this.model (or cfg.model) and store into entity.model.
        this.model = null;
        this._model = null; // legacy alias (keep to avoid breaking old scripts)

        this.dom = new PlayerDomain(this);
        this.frame = new FrameContext();
        this.characterCfg = new CharacterConfig();

        this.factory = new PlayerEntityFactory(this);
        this.controller = new PlayerController(this);
        this.camera = new PlayerCamera(this);
        this.ui = new PlayerUI(this);
        this.events = new PlayerEvents(this);
    }


    getBodyId() { return this.bodyId | 0; }

    // Strict contract:
    // - Prefer entity.model (what the factory stored, authoritative for visibility toggles)
    // - Else fallback to player.model (pre-spawn / debug)
    getModel() {
        const e = this.entity;
        if (e && e.model) return e.model;
        return this.model;
    }

    withConfig(cfg) {
        this.cfg = cfg || Object.create(null);
        return this;
    }

    withModel(modelHandle) {
        // single write point
        this.model = modelHandle || null;
        this._model = this.model; // legacy compatibility
        return this;
    }

    init() {
        if (this.alive) return;
        if (!PHYS || typeof PHYS.ref !== "function") throw new Error("[player] PHYS.ref required");
        if (!INP || typeof INP.consumeSnapshot !== "function") throw new Error("[player] INP.consumeSnapshot required");

        engine.hud().setCursorEnabled(false, true);
        this.cfg = U.deepMerge({
            character: { radius: 0.35, height: 1.80, mass: 80.0, eyeHeight: 1.65 },
            spawn: { pos: { x: 129, y: 3, z: -300 }, radius: 0.35, height: 1.80, mass: 80.0 },
            camera: { type: "third" },
            ui: {},
            events: { enabled: true }
        }, this.cfg);

        this.ui.create();

        // create entity (factory will pick model from cfg.model or player.model)
        this.entity = this.factory.create(this.cfg.spawn);

        this.entityId = this.entity.entityId | 0;
        this.surfaceId = this.entity.surfaceId | 0;
        this.bodyId = this.entity.bodyId | 0;

        if (this.bodyId <= 0) throw new Error("[player] invalid bodyId=" + this.bodyId);

        this.body = PHYS.ref(this.bodyId);
        if (!this.body) throw new Error("[player] PHYS.ref(bodyId) returned null bodyId=" + this.bodyId);

        this.dom.syncIds(this);

        this.controller.bind();

        const movCfg = this.controller.getMovementCfg();
        this.characterCfg.loadFrom(this.cfg, movCfg);

        // IMPORTANT: camera must attach AFTER entity/model exists
        // (PlayerCamera is lazy; this avoids CameraOrchestrator seeing null model on startup)
        this.camera.attach();

        this.events.reset();
        this.events.onSpawn();

        if (this.ctx && typeof this.ctx.state === "function") {
            this.ctx.state().set("player", {
                alive: true,
                entityId: this.entityId,
                surfaceId: this.surfaceId,
                bodyId: this.bodyId
            });
        }

        this.alive = true;
        if (LOG && LOG.info) LOG.info("[player] init ok entity=" + this.entityId + " bodyId=" + this.bodyId);
    }

    _syncPose(frame) {
        const p = this.body.position();
        frame.pose.x = U.vx(p);
        frame.pose.y = U.vy(p);
        frame.pose.z = U.vz(p);

        const v = this.body.velocity();
        const vx = U.vx(v);
        const vy = U.vy(v);
        const vz = U.vz(v);

        frame.pose.vx = vx;
        frame.pose.vy = vy;
        frame.pose.vz = vz;

        frame.pose.speed = Math.hypot(vx, vy, vz);
        frame.pose.fallSpeed = (vy < 0) ? -vy : 0;

        frame.pose.grounded = frame.probeGroundCapsule(this.body, this.characterCfg);
    }

    _syncDomain(frame) {
        const fp = frame.pose;
        const dp = this.dom.pose;

        dp.x = fp.x; dp.y = fp.y; dp.z = fp.z;
        dp.vx = fp.vx; dp.vy = fp.vy; dp.vz = fp.vz;
        dp.speed = fp.speed;
        dp.fallSpeed = fp.fallSpeed;
        dp.grounded = fp.grounded;
    }

    _syncView(frame) {
        const yaw = this.camera.getYaw();
        const pitch = this.camera.getPitch();
        const type = this.camera.getType();

        this.dom.view.yaw = yaw;
        this.dom.view.pitch = pitch;
        this.dom.view.type = type;

        frame.view.yaw = yaw;
        frame.view.pitch = pitch;
        frame.view.type = type;
    }

    // Strict: used by camera/orchestrator if it wants to toggle visibility through the player.
    // No fallbacks besides entity.model and player.model; no setHidden, no guessing.
    setModelVisible(visible) {
        const m = this.getModel();
        if (!m) return;

        if (typeof m.setVisible !== "function") {
            throw new Error("[player] model must implement setVisible(boolean)");
        }

        m.setVisible(!!visible);
    }

    update(tpf) {
        if (!this.alive) return;

        const snap = INP.consumeSnapshot();

        this.frame.begin(this, tpf, snap);
        this.dom.syncIds(this);

        this._syncPose(this.frame);
        this._syncDomain(this.frame);

        this.camera.update(this.frame);
        this._syncView(this.frame);

        this.controller.update(this.frame);

        this.events.onState({
            grounded: this.frame.pose.grounded,
            jump: this.dom.input.jump,
            fallSpeed: this.frame.pose.fallSpeed
        });
        this.ui.refresh(true);
        this.frame.pose.grounded = this.frame.probeGroundCapsule(this.body, this.characterCfg);

        if (typeof INP.endFrame === "function") INP.endFrame();
    }

    destroy() {
        if (!this.alive) return;

        this.camera.destroy();
        this.ui.destroy();

        if (this.entity) this.entity.destroy();

        this.entity = null;
        this.body = null;
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        if (this.ctx && typeof this.ctx.state === "function") this.ctx.state().remove("player");

        this.alive = false;
        if (LOG && LOG.info) LOG.info("[player] destroy");
    }
}

let _player = null;

module.exports.init = function init(ctx) {
    if (_player && _player.alive) return;
    _player = new Player(ctx, null);
    _player.init();
};

module.exports.update = function update(ctx, tpf) {
    if (_player) _player.update(tpf);
};

module.exports.destroy = function destroy(ctx) {
    if (_player) _player.destroy();
    _player = null;
};

module.exports._getPlayer = function () { return _player; };
module.exports.Player = Player;