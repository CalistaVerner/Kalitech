// FILE: Scripts/player/index.js
// Author: Calista Verner
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
    syncIdsFromPlayer() {
        const p = this.player;
        this.ids.entityId = p.entityId | 0;
        this.ids.surfaceId = p.surfaceId | 0;
        this.ids.bodyId = p.bodyId | 0;
        return this;
    }
}

class Player {
    constructor(ctx, cfg) {
        this.ctx = ctx;
        this.cfg = cfg || Object.create(null);

        this.alive = false;

        // optional builder model handle (factory may override/replace with entity.model)
        this.model = null;

        this.entity = null;
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;
        this.body = null;

        this.dom = new PlayerDomain(this);
        this.frame = new FrameContext();

        this.characterCfg = new CharacterConfig();

        this.factory = new PlayerEntityFactory(this);
        this.movement = new PlayerController(this);
        this.camera = new PlayerCamera(this);
        this.ui = new PlayerUI(this);
        this.events = new PlayerEvents(this);

        // cache movement cfg once; do NOT reload every frame
        this._movementCfgCached = null;
        this._charCfgLoaded = false;
    }

    getBodyId() { return this.bodyId | 0; }

    // ✅ single source: spawned entity model
    getModel() {
        if (this.entity && this.entity.model) return this.entity.model;
        return this.model || null;
    }

    withConfig(cfg) { this.cfg = cfg || Object.create(null); return this; }
    withModel(modelHandle) { this.model = modelHandle || null; return this; }

    withCamera(type) {
        if (!this.cfg) this.cfg = Object.create(null);
        if (!this.cfg.camera) this.cfg.camera = Object.create(null);
        this.cfg.camera.type = String(type || "third").toLowerCase();
        return this;
    }

    create(ctx) { if (ctx) this.ctx = ctx; this.init(); return this; }

    init() {
        if (this.alive) return;

        if (!PHYS) throw new Error("[player] PHYS missing");
        if (typeof PHYS.ref !== "function") throw new Error("[player] PHYS.ref missing");
        if (!INP || typeof INP.consumeSnapshot !== "function") throw new Error("[player] INP.consumeSnapshot missing");

        this.cfg = U.deepMerge({
            character: { radius: 0.35, height: 1.80, mass: 80.0, eyeHeight: 1.65 },
            spawn: { pos: { x: 129, y: 3, z: -300 }, radius: 0.35, height: 1.80, mass: 80.0 },
            camera: { type: "third" },
            ui: { crosshair: { size: 22, color: { r: 0.2, g: 1.0, b: 0.4, a: 1.0 } } },
            events: { enabled: true, throttleMs: 250 }
        }, this.cfg || Object.create(null));

        this.ui.create();

        this.entity = this.factory.create(this.cfg.spawn);
        this.entityId = this.entity.entityId | 0;
        this.surfaceId = this.entity.surfaceId | 0;
        this.bodyId = this.entity.bodyId | 0;

        if ((this.bodyId | 0) <= 0) throw new Error("[player] bodyId is 0");

        this.body = PHYS.ref(this.bodyId | 0);
        if (!this.body) throw new Error("[player] PHYS.ref(bodyId) returned null bodyId=" + (this.bodyId | 0));

        this.dom.syncIdsFromPlayer();
        this.movement.bind();

        // ✅ IMPORTANT:
        // Player does NOT control camera mode. CameraOrchestrator decides.
        // If you want a default, set it inside PlayerCamera/CameraOrchestrator constructor.
        // (cfg.camera.type is only a hint during construction, not enforced every frame.)

        this.events.reset();
        this.events.onSpawn();

        if (this.ctx && typeof this.ctx.state === "function") {
            this.ctx.state().set("player", {
                alive: true,
                entityId: this.entityId | 0,
                surfaceId: this.surfaceId | 0,
                bodyId: this.bodyId | 0
            });
        }

        // cache movement config once and load character config once
        this._movementCfgCached = this.movement.getMovementCfg();
        this.characterCfg.loadFrom(this.cfg, this._movementCfgCached);
        this._charCfgLoaded = true;

        this.alive = true;
        LOG.info("[player] init ok entity=" + (this.entityId | 0) + " bodyId=" + (this.bodyId | 0));
    }

    _ensureBody() {
        if (this.body) return;

        this.body = PHYS.ref(this.bodyId | 0);
        if (!this.body) {
            throw new Error("[player] PHYS.ref returned null while reacquiring body wrapper; bodyId=" + (this.bodyId | 0));
        }

        // 🚫 NEVER call camera.setType here.
        // Reacquiring physics wrapper must not mutate camera state.
    }

    _syncPose(frame) {
        const b = this.body;

        const p = b.position();
        frame.pose.x = U.vx(p, 0);
        frame.pose.y = U.vy(p, 0);
        frame.pose.z = U.vz(p, 0);

        const v = b.velocity();
        const vx = U.vx(v, 0);
        const vy = U.vy(v, 0);
        const vz = U.vz(v, 0);

        frame.pose.vx = vx;
        frame.pose.vy = vy;
        frame.pose.vz = vz;

        frame.pose.speed = Math.hypot(vx, vy, vz);
        frame.pose.fallSpeed = vy < 0 ? -vy : 0;

        frame.pose.grounded = frame.probeGroundCapsule(b, this.characterCfg);
    }

    _syncDomainFromFrame() {
        const fp = this.frame.pose;
        const dp = this.dom.pose;

        dp.x = fp.x; dp.y = fp.y; dp.z = fp.z;
        dp.vx = fp.vx; dp.vy = fp.vy; dp.vz = fp.vz;
        dp.speed = fp.speed;
        dp.fallSpeed = fp.fallSpeed;
        dp.grounded = fp.grounded;
    }

    _syncView() {
        const yaw = this.camera.getYaw();
        const pitch = this.camera.getPitch();
        const type = this.camera.getType();

        this.dom.view.yaw = yaw;
        this.dom.view.pitch = pitch;
        this.dom.view.type = type;

        if (this.frame && this.frame.view) {
            this.frame.view.yaw = yaw;
            this.frame.view.pitch = pitch;
            this.frame.view.type = type;
        }
    }

    update(tpf) {
        if (!this.alive) return;

        const snap = INP.consumeSnapshot();

        this.frame.begin(this, tpf, snap);
        this.dom.syncIdsFromPlayer();

        this._ensureBody();

        // if cfg can hot-reload, you can re-load characterCfg only when cfg object identity changes;
        // but by default, DO NOT rebuild it every frame.
        if (!this._charCfgLoaded) {
            this._movementCfgCached = this.movement.getMovementCfg();
            this.characterCfg.loadFrom(this.cfg, this._movementCfgCached);
            this._charCfgLoaded = true;
        }

        this._syncPose(this.frame);
        this._syncDomainFromFrame();

        this.camera.update(this.frame);
        this._syncView();

        this.movement.update(this.frame);

        this.events.onState({
            grounded: !!this.frame.pose.grounded,
            bodyId: this.bodyId | 0,
            jump: !!(this.dom && this.dom.input && this.dom.input.jump),
            fallSpeed: this.frame.pose.fallSpeed
        });

        if (INP.endFrame) INP.endFrame();
    }

    destroy() {
        if (!this.alive) return;

        this.camera.destroy();
        this.ui.destroy();
        if (this.entity) this.entity.destroy();

        this.entity = null;
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;
        this.body = null;

        if (this.ctx && typeof this.ctx.state === "function") {
            this.ctx.state().remove("player");
        }

        this.alive = false;
        LOG.info("[player] destroy");
    }
}

let _player = null;

module.exports.init = function init(ctx) {
    if (_player && _player.alive) return;
    _player = new Player(ctx, null);
    _player.init();
};

module.exports.update = function update(ctx, tpf) {
    if (!_player) return;
    _player.update(tpf);
};

module.exports.destroy = function destroy(ctx) {
    if (!_player) return;
    _player.destroy();
    _player = null;
};

module.exports._getPlayer = function () { return _player; };
module.exports.Player = Player;