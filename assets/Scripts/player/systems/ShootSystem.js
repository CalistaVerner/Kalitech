"use strict";

const U = require("../util.js");

function normalize3_into(x, y, z, out) {
    const l2 = x * x + y * y + z * z;
    if (l2 < 1e-12) { out.x = 0; out.y = 0; out.z = 1; return out; }
    const inv = 1.0 / Math.sqrt(l2);
    out.x = x * inv; out.y = y * inv; out.z = z * inv;
    return out;
}
function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }

const DEFAULT_CFG = Object.freeze({
    enabled: true,
    model: "Models/sharp-boulder-layered.obj",
    scale: 0.5,
    mass: 600.0,
    lockRotation: false,
    materialId: "unshaded.grass",
    spawnOffset: 0.0,
    speed: 20.0,
    invertPitch: false,
    debug: {logShots: false},
    events: {
        fire: "game.shoot.fire",
        hit: "game.shoot.hit"
    }
});

function mergeCfg(rootCfg) {
    const src = (rootCfg && rootCfg.shoot && typeof rootCfg.shoot === "object") ? rootCfg.shoot : Object.create(null);
    const b = DEFAULT_CFG;
    const d = (src.debug && typeof src.debug === "object") ? src.debug : Object.create(null);
    const e = (src.events && typeof src.events === "object") ? src.events : Object.create(null);

    return {
        enabled: (src.enabled !== undefined) ? !!src.enabled : b.enabled,
        model: (src.model !== undefined) ? String(src.model) : b.model,
        scale: (src.scale !== undefined) ? U.num(src.scale, b.scale) : b.scale,
        mass: (src.mass !== undefined) ? U.num(src.mass, b.mass) : b.mass,
        lockRotation: (src.lockRotation !== undefined) ? !!src.lockRotation : b.lockRotation,
        materialId: (src.materialId !== undefined) ? String(src.materialId) : b.materialId,
        spawnOffset: (src.spawnOffset !== undefined) ? U.num(src.spawnOffset, b.spawnOffset) : b.spawnOffset,
        speed: (src.speed !== undefined) ? U.num(src.speed, b.speed) : b.speed,
        invertPitch: (src.invertPitch !== undefined) ? !!src.invertPitch : b.invertPitch,
        debug: {logShots: (d.logShots !== undefined) ? !!d.logShots : !!(b.debug && b.debug.logShots)},
        events: {
            fire: (e.fire !== undefined) ? String(e.fire) : b.events.fire,
            hit: (e.hit !== undefined) ? String(e.hit) : b.events.hit
        }
    };
}

function idOfSurfaceHandle(g) {
    if (!g) return 0;
    if (typeof g.surfaceId === "number") return g.surfaceId | 0;
    if (typeof g.id === "number") return g.id | 0;
    if (typeof g.id === "function") return (g.id() | 0) || 0;
    if (typeof g.getId === "function") return (g.getId() | 0) || 0;
    if (typeof g.handle === "object" && g.handle && typeof g.handle.id === "number") return g.handle.id | 0;
    return 0;
}

function idOfBodyHandle(g) {
    if (!g) return 0;
    if (typeof g.bodyId === "number") return g.bodyId | 0;
    if (typeof g.physicsBodyId === "number") return g.physicsBodyId | 0;
    if (typeof g.physicsId === "number") return g.physicsId | 0;
    if (typeof g.getBodyId === "function") return (g.getBodyId() | 0) || 0;
    return 0;
}

class ShootSystem {
    constructor(player) {
        this.player = player;
        this.cfg = mergeCfg(player.cfg);

        this._shotId = 0;
        this._dir = { x: 0, y: 0, z: 1 };
        this._origin = { x: 0, y: 0, z: 0 };
        this._spawn = { x: 0, y: 0, z: 0 };
        this._vel = { x: 0, y: 0, z: 0 };

        this._shotsBySurface = Object.create(null);
        this._shotsByBody = Object.create(null);

        this._subCollision = 0;

        if (this.cfg.enabled) {
            const bus = this.player.d.bus;
            if (!bus) throw new Error("[shoot] enabled but domains.bus missing");
        }
    }

    configure(cfg) {
        if (!cfg) return this;
        this.player.cfg = cfg;
        this.cfg = mergeCfg(cfg);
        return this;
    }

    _bus() {
        return this.player.d.bus;
    }

    _bindCollision() {
        if (this._subCollision) return;

        const bus = this._bus();
        if (!bus) throw new Error("[shoot] bus missing");

        this._subCollision = (bus.on("engine.physics.collision.begin", (payload) => {
            this._onCollisionBegin(payload);
        }) | 0);

        if (!this._subCollision) throw new Error("[shoot] bus.on(...) returned 0");
    }

    _emit(topic, payload) {
        this._bus().emit(topic, payload);
    }

    _dirFromYawPitch_into(yaw, pitch, outDir) {
        const c = this.cfg;
        yaw = U.num(yaw, 0);
        pitch = U.num(pitch, 0);

        const LIM = (Math.PI * 0.5) - 1e-4;
        pitch = clamp(pitch, -LIM, LIM);
        if (c.invertPitch) pitch = -pitch;

        const sy = Math.sin(yaw), cy = Math.cos(yaw);
        const sp = Math.sin(pitch), cp = Math.cos(pitch);
        return normalize3_into(sy * cp, sp, cy * cp, outDir);
    }

    _readOrigin_into(frame, outOrigin) {
        outOrigin.x = U.num(frame.pose.x, 0);
        outOrigin.y = U.num(frame.pose.y, 0) + U.num(frame.character.eyeHeight, 1.55);
        outOrigin.z = U.num(frame.pose.z, 0);
        return outOrigin;
    }

    _registerShot(g, meta) {
        const surfaceId = idOfSurfaceHandle(g);
        const bodyId = idOfBodyHandle(g);

        if (surfaceId > 0) this._shotsBySurface[surfaceId] = meta;
        if (bodyId > 0 && surfaceId > 0) this._shotsByBody[bodyId] = surfaceId;

        meta.surfaceId = surfaceId | 0;
        meta.bodyId = bodyId | 0;
        return meta;
    }

    _fire(frame, ownerBodyId) {
        const c = this.cfg;
        if (!c.enabled || !ownerBodyId) return;

        this._bindCollision();

        const yaw = frame.view.yaw;
        const pitch = frame.view.pitch;

        this._readOrigin_into(frame, this._origin);
        this._dirFromYawPitch_into(yaw, pitch, this._dir);

        const off = c.spawnOffset;
        this._spawn.x = this._origin.x + this._dir.x * off;
        this._spawn.y = this._origin.y + this._dir.y * off;
        this._spawn.z = this._origin.z + this._dir.z * off;

        const shotIndex = (++this._shotId) | 0;
        const name = "shot-" + shotIndex;

        if (!ENGINE || !ENGINE.mesh || typeof ENGINE.mesh.loadModel !== "function") throw new Error("[shoot] ENGINE.mesh.loadModel required");
        if (!MAT || typeof MAT.getMaterial !== "function") throw new Error("[shoot] MAT.getMaterial required");

        const g = ENGINE.mesh.loadModel(c.model, {
            scale: c.scale,
            name,
            pos: [this._spawn.x, this._spawn.y, this._spawn.z],
            physics: {
                mass: c.mass,
                lockRotation: c.lockRotation,
                collider: { type: "dynamicMesh", halfExtents: [1.2, 0.6, 2.4] }
            }
        });

        if (g && typeof g.setMaterial === "function") g.setMaterial(MAT.getMaterial(c.materialId));

        const speed = c.speed;
        this._vel.x = this._dir.x * speed;
        this._vel.y = this._dir.y * speed;
        this._vel.z = this._dir.z * speed;

        if (!g || typeof g.velocity !== "function") throw new Error("[shoot] projectile handle must support velocity(v)");
        g.velocity(this._vel);

        const meta = this._registerShot(g, {
            shotIndex,
            name,
            spawn: {x: this._spawn.x, y: this._spawn.y, z: this._spawn.z},
            dir: {x: this._dir.x, y: this._dir.y, z: this._dir.z},
            vel: {x: this._vel.x, y: this._vel.y, z: this._vel.z},
            yaw: +yaw || 0,
            pitch: +pitch || 0,
            ownerBodyId: ownerBodyId | 0
        });

        this._emit(c.events.fire, {
            shotIndex: meta.shotIndex,
            name: meta.name,
            surfaceId: meta.surfaceId,
            bodyId: meta.bodyId,
            ownerBodyId: meta.ownerBodyId,
            spawn: meta.spawn,
            dir: meta.dir,
            vel: meta.vel,
            yaw: meta.yaw,
            pitch: meta.pitch
        });

        if (c.debug && c.debug.logShots && ENGINE.log && ENGINE.log.info) {
            ENGINE.log.info("[shoot] " + name + " sid=" + meta.surfaceId + " bid=" + meta.bodyId);
        }
    }

    _onCollisionBegin(payload) {
        const c = this.cfg;
        if (!c.enabled) return;

        if (!payload || !payload.a || !payload.b) throw new Error("[shoot] collision payload invalid");

        const a = payload.a;
        const b = payload.b;

        const aSid = (a.surfaceId | 0) || 0;
        const bSid = (b.surfaceId | 0) || 0;

        let shotSid = 0;
        let otherSid = 0;

        if (aSid > 0 && this._shotsBySurface[aSid]) {
            shotSid = aSid;
            otherSid = bSid;
        } else if (bSid > 0 && this._shotsBySurface[bSid]) {
            shotSid = bSid;
            otherSid = aSid;
        } else return;

        const shotMeta = this._shotsBySurface[shotSid];
        if (!shotMeta) throw new Error("[shoot] shot meta missing for sid=" + shotSid);

        this._emit(c.events.hit, {
            shotIndex: shotMeta.shotIndex,
            name: shotMeta.name,
            shot: {surfaceId: shotSid, bodyId: shotMeta.bodyId | 0},
            other: {surfaceId: otherSid | 0, bodyId: (shotSid === aSid ? (b.bodyId | 0) : (a.bodyId | 0))},
            step: payload.step | 0,
            dt: +payload.dt || 0,
            contact: payload.contact || null
        });

        delete this._shotsBySurface[shotSid];
        if (shotMeta.bodyId > 0) delete this._shotsByBody[shotMeta.bodyId];
    }

    update(frame, ownerBodyId) {
        if (!this.cfg.enabled) return;
        if (!frame.input.lmbJustPressed) return;
        this._fire(frame, ownerBodyId | 0);
    }

    destroy() {
        if (!this.cfg.enabled) return;
        const bus = this._bus();
        if (typeof bus.off !== "function") throw new Error("[shoot] bus.off(id) required");
        if (this._subCollision) bus.off(this._subCollision | 0);

        this._subCollision = 0;
        this._shotsBySurface = Object.create(null);
        this._shotsByBody = Object.create(null);
    }
}

module.exports = ShootSystem;