// FILE: Scripts/player/PlayerPawn.js
"use strict";

const U = require("./util.js");
const FrameContext = require("./FrameContext.js");
const CharacterConfig = require("./CharacterConfig.js");
const {PlayerEntityFactory} = require("./PlayerEntityFactory.js");
const {resolveDomains} = require("./PlayerDomains.js");
const {resolveBodyAccess} = require("./PlayerBodyAccess.js");
const InputRouter = require("./systems/InputRouter.js");

class PlayerPawn {
    constructor(ctx, cfg) {
        this.ctx = ctx;
        this.cfg = cfg || Object.create(null);

        this.d = null;

        this.entity = null;
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        this.body = null;
        this.bodyAccess = null;

        this.characterCfg = new CharacterConfig();
        this.frame = new FrameContext();

        this.inputRouter = null;

        this.alive = false;
    }

    init() {
        if (this.alive) return this;

        this.d = resolveDomains(this.ctx);

        this.cfg = U.deepMerge({
            character: {radius: 0.35, height: 1.80, mass: 80.0, eyeHeight: 1.65},
            spawn: {pos: {x: 129, y: 3, z: -300}, radius: 0.35, height: 1.80, mass: 80.0},
            camera: {type: "third"},
            ui: {},
            events: {enabled: true},
            input: {},
            movement: {},
            shoot: {}
        }, this.cfg);

        this.inputRouter = new InputRouter(this.d.input, this.cfg.input);

        const factory = new PlayerEntityFactory(this);
        this.entity = factory.create(this.cfg.spawn);

        this.entityId = this.entity.entityId | 0;
        this.surfaceId = this.entity.surfaceId | 0;
        this.bodyId = this.entity.bodyId | 0;

        if (this.bodyId <= 0) throw new Error("[player] invalid bodyId=" + this.bodyId);

        this.body = this.entity.body || null;
        this.bodyAccess = resolveBodyAccess(this.d.physics, this.body, this.bodyId);

        this.alive = true;
        return this;
    }

    beginFrame(tpf) {
        const snap = this.d.input.consumeSnapshot();
        this.frame.begin(this, tpf, snap);

        // Authoritative input state for gameplay systems:
        // fills frame.input.{ax,az,run,jump,lmbDown,lmbJustPressed,dx,dy,wheel}
        this.inputRouter.read(this.frame);

        // Stable references used by gameplay systems
        this.frame.bodyAccess = this.bodyAccess;
        this.frame.bodyId = this.bodyId | 0;
    }

    syncPose() {
        const frame = this.frame;
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
            frame.pose.grounded = (probe.length >= 3)
                ? probe.call(frame, this.bodyAccess, this.characterCfg, this.bodyId | 0)
                : probe.call(frame, this.bodyAccess, this.characterCfg);
        } else {
            frame.pose.grounded = false;
        }
    }

    endFrame() {
        if (typeof this.d.input.endFrame === "function") this.d.input.endFrame();
    }

    /* LEGACY TODO*/
    getModel() {
        const e = this.entity;
        if (!e) return null;

        if (typeof e.getModel === "function") return e.getModel();
        if (e.model !== undefined) return e.model;

        return null;
    }

    // (не обязательно, но полезно для других систем/камеры)
    getBodyId() {
        return this.bodyId | 0;
    }

    getSurfaceId() {
        return this.surfaceId | 0;
    }

    getEntityId() {
        return this.entityId | 0;
    }

    destroy() {
        if (!this.alive) return;

        if (this.entity) this.entity.destroy(this.d.physics);

        this.entity = null;
        this.body = null;
        this.bodyAccess = null;

        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        this.inputRouter = null;

        this.d = null;
        this.alive = false;
    }
}

module.exports = {PlayerPawn};