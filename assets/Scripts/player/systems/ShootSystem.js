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
    debug: { logShots: false }
});

function mergeCfg(src) {
    src = (src && typeof src === "object") ? src : Object.create(null);
    const b = DEFAULT_CFG;
    const d = (src.debug && typeof src.debug === "object") ? src.debug : Object.create(null);

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
        debug: { logShots: (d.logShots !== undefined) ? !!d.logShots : !!(b.debug && b.debug.logShots) }
    };
}

class ShootSystem {
    constructor(rootCfg) {
        rootCfg = rootCfg || Object.create(null);
        this.cfg = mergeCfg(rootCfg.shoot);
        this._shotId = 0;

        this._dir = { x: 0, y: 0, z: 1 };
        this._origin = { x: 0, y: 0, z: 0 };
        this._spawn = { x: 0, y: 0, z: 0 };
        this._vel = { x: 0, y: 0, z: 0 };
    }

    configure(rootCfg) {
        rootCfg = rootCfg || Object.create(null);
        if (rootCfg.shoot) this.cfg = mergeCfg(Object.assign({}, this.cfg, rootCfg.shoot));
        return this;
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
        outOrigin.y = U.num(frame.pose.y, 0) + (frame.character ? U.num(frame.character.eyeHeight, 1.55) : 1.55);
        outOrigin.z = U.num(frame.pose.z, 0);
        return outOrigin;
    }

    _fire(frame, bodyId) {
        const c = this.cfg;
        if (!c.enabled || !bodyId) return;

        const yaw = frame.view ? frame.view.yaw : 0;
        const pitch = frame.view ? frame.view.pitch : 0;

        this._readOrigin_into(frame, this._origin);
        this._dirFromYawPitch_into(yaw, pitch, this._dir);

        const off = c.spawnOffset;
        this._spawn.x = this._origin.x + this._dir.x * off;
        this._spawn.y = this._origin.y + this._dir.y * off;
        this._spawn.z = this._origin.z + this._dir.z * off;

        const name = "shot-" + ((++this._shotId) | 0);

        const g = MSH.loadModel(c.model, {
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

        if (g && typeof g.velocity === "function") g.velocity(this._vel);

        SND.create({soundFile: "Sounds/hit.ogg", volume: 1.0, pitch: 1.0, looping: false}).play();

        if (c.debug && c.debug.logShots && LOG && LOG.info) {
            LOG.info("[shoot] " + name + " yaw=" + yaw + " pitch=" + pitch);
        }
    }

    update(frame, bodyId) {
        if (!this.cfg.enabled) return;
        if (!frame || !frame.input) return;
        if (!frame.input.lmbJustPressed) return;
        this._fire(frame, bodyId | 0);
    }
}

module.exports = ShootSystem;