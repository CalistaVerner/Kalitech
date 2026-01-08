"use strict";

const U = require("./util.js");
const FrameContext = require("./FrameContext.js");
const CharacterConfig = require("./CharacterConfig.js");
const {PlayerEntityFactory} = require("./PlayerEntityFactory.js");
const {resolveDomains} = require("./PlayerDomains.js");
const {resolveBodyAccess} = require("./PlayerBodyAccess.js");
const InputRouter = require("./systems/InputRouter.js");

const {EntityCore} = require("../core/entity/EntityCore.js");

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

class PlayerPawn {
    constructor(ctx, cfg) {
        this.ctx = req(ctx, "[PlayerPawn] ctx is required");
        this.cfg = cfg || Object.create(null);

        this.d = null;

        this.core = null;

        this.characterCfg = new CharacterConfig();
        this.frame = new FrameContext();
        this.inputRouter = null;

        this.alive = false;
    }

    get entity() {
        return this.core.entity;
    }

    get bodyAccess() {
        return this.core.bodyAccess;
    }

    get bodyId() {
        return this.core.bodyId | 0;
    }

    get entityId() {
        return this.core.entityId | 0;
    }

    get surfaceId() {
        return this.core.surfaceId | 0;
    }

    get state() {
        return this.core.state;
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
        const ent = factory.create(this.cfg.spawn);

        const body = ent.body || null;
        const bodyAccess = resolveBodyAccess(this.d.physics, body, ent.bodyId | 0);

        // FrameContext ground probe is REQUIRED for player (RED жесткость)
        if (typeof this.frame.probeGroundCapsule !== "function") {
            throw new Error("[PlayerPawn] FrameContext.probeGroundCapsule is required");
        }

        this.core = new EntityCore(this.ctx, this.d, this.cfg);

        const ch = this.cfg.character;
        this.core
            .configureShape(ch.mass, ch.radius, ch.height)
            .attach(ent, body, bodyAccess)
            // grounded is now core responsibility
            .setGroundProbe((core) => {
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

        // keep frame.pose in sync for existing systems
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
        if (typeof this.d.input.endFrame !== "function") throw new Error("[PlayerPawn] input.endFrame() is required");
        this.d.input.endFrame();
    }

    destroy() {
        if (!this.alive) return;

        this.core.destroy();
        this.core = null;

        this.inputRouter = null;
        this.d = null;

        this.alive = false;
    }

    //LEGACY
    getModel() {
        return this.core.model();
    }

    getBodyId() {
        return this.core.bodyId | 0;
    }

    getEntityId() {
        return this.core.entityId | 0;
    }

    getSurfaceId() {
        return this.core.surfaceId | 0;
    }


}

module.exports = {PlayerPawn};