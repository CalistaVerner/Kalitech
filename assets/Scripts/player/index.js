"use strict";

const U = require("./util.js");
const FrameContext = require("./FrameContext.js");
const CharacterConfig = require("./CharacterConfig.js");

const PlayerController = require("./PlayerController.js");
const PlayerCamera = require("./PlayerCamera.js");
const PlayerUI = require("./PlayerUI.js");
const PlayerEvents = require("./PlayerEvents.js");
const { PlayerEntityFactory } = require("./PlayerEntityFactory.js");

function must(obj, name) {
    if (!obj) throw new Error(name + " is required");
    return obj;
}

function resolveEngineApi(ctx) {
    if (ctx && ctx.engine && typeof ctx.engine.api === "function") return ctx.engine.api();
    if (ctx && typeof ctx.api === "function") return ctx.api();
    if (ctx && typeof ctx.engineApi === "function") return ctx.engineApi();
    if (typeof engine !== "undefined") return engine;
    return null;
}

function resolveDomains(ctx, engineApi) {
    const E = engineApi || resolveEngineApi(ctx);
    if (!E) throw new Error("[player] cannot resolve engine api from ctx");

    const PH = (typeof PHYS !== "undefined" && PHYS) ? PHYS : (typeof E.physics === "function" ? E.physics() : null);
    const IN = (typeof INP !== "undefined" && INP) ? INP : (typeof E.input === "function" ? E.input() : null);
    const HUD_NATIVE = (typeof E.hud === "function") ? E.hud() : null;

    return {E, PH, IN, HUD_NATIVE};
}

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

        this.engine = null;
        this.PHYS = null;
        this.INP = null;

        this.HUD_NATIVE = null;
        this.HUD = null;
        this.d = null;

        this.entity = null;
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        this.body = null;

        this.model = null;
        this._model = null;

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
        this.model = modelHandle || null;
        this._model = this.model;
        return this;
    }

    init() {
        if (this.alive) return;

        const r = resolveDomains(this.ctx, null);
        this.engine = r.E;
        this.PHYS = must(r.PH, "[player] PHYS domain");
        this.INP = must(r.IN, "[player] INP domain");
        this.HUD_NATIVE = r.HUD_NATIVE;

        this.HUD = (typeof HUD !== "undefined" && HUD) ? HUD : null;

        // ✅ Domains container for all subsystems (camera expects this)
        this.d = Object.freeze({
            ctx: this.ctx,
            engine: this.engine,
            physics: this.PHYS,
            input: this.INP,
            camera: (this.engine && typeof this.engine.camera === "function") ? this.engine.camera() : null,
            assets: (this.engine && typeof this.engine.assets === "function") ? this.engine.assets() : null,
            hud: this.HUD,
            hudNative: this.HUD_NATIVE,
            bus: (this.engine && typeof this.engine.bus === "function") ? this.engine.bus() : null,
            surface: (this.engine && typeof this.engine.surface === "function") ? this.engine.surface() : null,
            log: (typeof LOG !== "undefined" && LOG) ? LOG : null
        });

        if (!this.d.camera) throw new Error("[player] engine.camera() required");


        if (typeof this.PHYS.ref !== "function") throw new Error("[player] PHYS.ref required");
        if (typeof this.INP.consumeSnapshot !== "function") throw new Error("[player] INP.consumeSnapshot required");

        if (this.HUD_NATIVE && typeof this.HUD_NATIVE.setCursorEnabled === "function") {
            this.HUD_NATIVE.setCursorEnabled(false, true);
        }

        this.cfg = U.deepMerge({
            character: { radius: 0.35, height: 1.80, mass: 80.0, eyeHeight: 1.65 },
            spawn: { pos: { x: 129, y: 3, z: -300 }, radius: 0.35, height: 1.80, mass: 80.0 },
            camera: { type: "first" },
            ui: {},
            events: { enabled: true }
        }, this.cfg);

        this.ui.create();

        this.entity = this.factory.create(this.cfg.spawn);

        this.entityId = this.entity.entityId | 0;
        this.surfaceId = this.entity.surfaceId | 0;
        this.bodyId = this.entity.bodyId | 0;

        if (this.bodyId <= 0) throw new Error("[player] invalid bodyId=" + this.bodyId);

        this.body = this.PHYS.ref(this.bodyId);
        if (!this.body) throw new Error("[player] PHYS.ref(bodyId) returned null bodyId=" + this.bodyId);

        this.dom.syncIds(this);

        this.controller.bind();

        const movCfg = this.controller.getMovementCfg();
        this.characterCfg.loadFrom(this.cfg, movCfg);

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

    setModelVisible(visible) {
        const m = this.getModel();
        if (!m) return;
        if (typeof m.setVisible !== "function") throw new Error("[player] model must implement setVisible(boolean)");
        m.setVisible(!!visible);
    }

    update(tpf) {
        if (!this.alive) return;

        const snap = this.INP.consumeSnapshot();

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

        this.ui.refresh();

        if (this.INP && typeof this.INP.endFrame === "function") this.INP.endFrame();
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

        this.engine = null;
        this.PHYS = null;
        this.INP = null;
        this.HUD_NATIVE = null;
        this.HUD = null;
        this.d = null;

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