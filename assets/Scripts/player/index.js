"use strict";

const U = require("./util.js");
const FrameContext = require("./FrameContext.js");
const CharacterConfig = require("./CharacterConfig.js");

const PlayerController = require("./PlayerController.js");
const PlayerCamera = require("./PlayerCamera.js");
const PlayerUI = require("./PlayerUI.js");
const PlayerEvents = require("./PlayerEvents.js");
const { PlayerEntityFactory } = require("./PlayerEntityFactory.js");

function must(x, msg) {
    if (!x) throw new Error(msg);
    return x;
}

function resolveEngineApi(ctx) {
    if (ctx && ctx.engine && typeof ctx.engine.api === "function") return ctx.engine.api();
    if (ctx && typeof ctx.api === "function") return ctx.api();
    if (ctx && typeof ctx.engineApi === "function") return ctx.engineApi();
    if (typeof engine !== "undefined") return engine;
    return null;
}

function resolveDomains(ctx) {
    const E = resolveEngineApi(ctx);
    if (!E) throw new Error("[player] cannot resolve engine api from ctx");

    const physics = (typeof PHYS !== "undefined" && PHYS) ? PHYS : (typeof E.physics === "function" ? E.physics() : null);
    const input = (typeof INP !== "undefined" && INP) ? INP : (typeof E.input === "function" ? E.input() : null);
    const assets = (typeof E.assets === "function") ? E.assets() : null;
    const camera = (typeof E.camera === "function") ? E.camera() : null;
    const surface = (typeof E.surface === "function") ? E.surface() : null;

    const bus =
        (typeof E.bus === "function") ? E.bus()
            : (typeof E.scriptBus === "function") ? E.scriptBus()
                : null;

    const hud = (typeof HUD !== "undefined" && HUD) ? HUD : null;
    const hudNative = (typeof E.hud === "function") ? E.hud() : null;

    return Object.freeze({
        ctx,
        engine: E,
        physics: must(physics, "[player] engine.physics() required"),
        input: must(input, "[player] engine.input() required"),
        assets,
        camera: must(camera, "[player] engine.camera() required"),
        surface,
        bus,
        hud,
        hudNative,
        log: (typeof LOG !== "undefined" && LOG) ? LOG : null
    });
}

class PlayerDomain {
    constructor(player) {
        this.player = player;
        this.ids = { entityId: 0, surfaceId: 0, bodyId: 0 };

        this.input = {
            ax: 0, az: 0,
            run: false,
            jump: false,
            lmbDown: false,
            lmbJustPressed: false,
            dx: 0, dy: 0, wheel: 0
        };

        this.view = { yaw: 0, pitch: 0, type: "third" };

        this.pose = {
            x: 0, y: 0, z: 0,
            vx: 0, vy: 0, vz: 0,
            grounded: false,
            speed: 0,
            fallSpeed: 0
        };
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

        this.d = null;
        this.alive = false;

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

        this._subFire = 0;
        this._subHit = 0;
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

    getModel() {
        return (this.entity && this.entity.getModel) ? this.entity.getModel() : this.model;
    }

    init() {
        if (this.alive) return this;

        this.d = resolveDomains(this.ctx);

        if (this.d.hudNative && typeof this.d.hudNative.setCursorEnabled === "function") {
            this.d.hudNative.setCursorEnabled(false, true);
        }

        this.cfg = U.deepMerge({
            character: { radius: 0.35, height: 1.80, mass: 80.0, eyeHeight: 1.65 },
            spawn: { pos: { x: 129, y: 3, z: -300 }, radius: 0.35, height: 1.80, mass: 80.0 },
            camera: { type: "first" },
            ui: {},
            events: {enabled: true},
            shoot: {events: {fire: "game.shoot.fire", hit: "game.shoot.hit"}}
        }, this.cfg);

        this.ui.create();

        this.entity = this.factory.create(this.cfg.spawn);

        this.entityId = this.entity.entityId | 0;
        this.surfaceId = this.entity.surfaceId | 0;
        this.bodyId = this.entity.bodyId | 0;

        if (this.bodyId <= 0) throw new Error("[player] invalid bodyId=" + this.bodyId);

        this.body = this.d.physics.ref(this.bodyId);
        if (!this.body) throw new Error("[player] physics.ref(bodyId) returned null bodyId=" + this.bodyId);

        this.dom.syncIds(this);
        this.controller.bind();

        const movCfg = this.controller.getMovementCfg();
        this.characterCfg.loadFrom(this.cfg, movCfg);

        this.camera.attach();

        this.events.reset();
        this.events.onSpawn();

        const bus = this.d.bus;
        if (bus && typeof bus.on === "function") {
            const se = (this.cfg.shoot && this.cfg.shoot.events) ? this.cfg.shoot.events : null;
            const fireTopic = se && se.fire ? String(se.fire) : "game.shoot.fire";
            const hitTopic = se && se.hit ? String(se.hit) : "game.shoot.hit";

            if (LOG && LOG.info) {
                this._subFire = (bus.on(fireTopic, (e) => {
                    LOG.info("[event] FIRE shot=" + e.name + " sid=" + e.surfaceId);
                }) | 0);
                this._subHit = (bus.on(hitTopic, (e) => {
                    LOG.info("[event] HIT shot=" + e.name + " otherSid=" + (e.other ? e.other.surfaceId : 0));
                }) | 0);
            }
        }

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

        return this;
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

    update(tpf) {
        if (!this.alive) return;

        const snap = this.d.input.consumeSnapshot();

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

        if (this.d.input && typeof this.d.input.endFrame === "function") this.d.input.endFrame();
    }

    destroy() {
        if (!this.alive) return;

        if (this.d && this.d.bus && typeof this.d.bus.off === "function") {
            if (this._subFire) {
                try {
                    this.d.bus.off(this._subFire | 0);
                } catch (_) {
                }
            }
            if (this._subHit) {
                try {
                    this.d.bus.off(this._subHit | 0);
                } catch (_) {
                }
            }
        }
        this._subFire = 0;
        this._subHit = 0;

        this.controller.destroy();
        this.camera.destroy();
        this.ui.destroy();

        if (this.entity) this.entity.destroy(this.d.physics);

        this.entity = null;
        this.body = null;

        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        if (this.ctx && typeof this.ctx.state === "function") this.ctx.state().remove("player");

        this.d = null;
        this.alive = false;

        if (LOG && LOG.info) LOG.info("[player] destroy");
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

module.exports._getPlayer = function () { return _player; };
module.exports.Player = Player;