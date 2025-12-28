// FILE: Scripts/player/systems/ShootSystem.js
// Author: Calista Verner
"use strict";

/**
 * ShootSystem:
 * - Fires on real LMB edge: engine.input().mouseDown(0) 0->1
 * - Spawn origin = player physics position + eyeHeight (stable)
 * - Aim direction = authoritative view angles from PlayerDomain (yaw/pitch)
 *   => exactly "center of screen" as seen by the player now
 * - Projectile velocity = dir * speed
 *
 * Requires:
 *  - global primitives: primitives.create(...) returns g with g.velocity(vec3)
 *  - global M: M.getMaterial(id)
 *
 * Input contract:
 *  dom.view.yaw   (radians)
 *  dom.view.pitch (radians)
 */

function num(v, fb) { const n = +v; return Number.isFinite(n) ? n : (fb || 0); }

function readComp(v, key, fb) {
    if (!v) return fb || 0;
    try {
        const m = v[key];
        if (typeof m === "function") return num(m.call(v), fb);
        if (typeof m === "number") return m;
        if (typeof m === "string") return num(m, fb);
    } catch (_) {}
    return fb || 0;
}
function vx(v, fb) { return readComp(v, "x", fb); }
function vy(v, fb) { return readComp(v, "y", fb); }
function vz(v, fb) { return readComp(v, "z", fb); }

function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }

function normalize3_into(x, y, z, out) {
    const l2 = x * x + y * y + z * z;
    if (l2 < 1e-12) { out.x = 0; out.y = 0; out.z = 1; return out; }
    const inv = 1.0 / Math.sqrt(l2);
    out.x = x * inv; out.y = y * inv; out.z = z * inv;
    return out;
}

const DEFAULT_CFG = Object.freeze({
    enabled: true,

    // projectile
    type: "sphere",
    radius: 0.5,
    mass: 800.0,
    lockRotation: false,
    materialId: "grass.debug",

    // spawn + flight
    spawnOffset: 2.0,
    eyeHeight: 1.55,
    speed: 8.0,

    // input conventions
    // In your current setup pitch grows when looking DOWN, so we invert it for math.
    invertPitch: true
});

function mergeCfg(src) {
    src = (src && typeof src === "object") ? src : {};
    const b = DEFAULT_CFG;
    return {
        enabled: (src.enabled !== undefined) ? !!src.enabled : b.enabled,

        type: (src.type !== undefined) ? String(src.type) : b.type,
        radius: (src.radius !== undefined) ? num(src.radius, b.radius) : b.radius,
        mass: (src.mass !== undefined) ? num(src.mass, b.mass) : b.mass,
        lockRotation: (src.lockRotation !== undefined) ? !!src.lockRotation : b.lockRotation,
        materialId: (src.materialId !== undefined) ? String(src.materialId) : b.materialId,

        spawnOffset: (src.spawnOffset !== undefined) ? num(src.spawnOffset, b.spawnOffset) : b.spawnOffset,
        eyeHeight: (src.eyeHeight !== undefined) ? num(src.eyeHeight, b.eyeHeight) : b.eyeHeight,
        speed: (src.speed !== undefined) ? num(src.speed, b.speed) : b.speed,

        invertPitch: (src.invertPitch !== undefined) ? !!src.invertPitch : b.invertPitch
    };
}

class ShootSystem {
    constructor(cfg) {
        cfg = cfg || {};
        this.cfg = mergeCfg(cfg.shoot);

        this._shotId = 0;
        this._prevLmbDown = false;

        // temps (no allocations in update loop)
        this._dir = { x: 0, y: 0, z: 1 };
        this._origin = { x: 0, y: 0, z: 0 };
        this._spawn = { x: 0, y: 0, z: 0 };
    }

    configure(cfg) {
        cfg = cfg || {};
        if (cfg.shoot) this.cfg = mergeCfg(Object.assign({}, this.cfg, cfg.shoot));
        return this;
    }

    _lmbJustPressed() {
        let down = false;
        try { down = !!engine.input().mouseDown(0); } catch (_) { down = false; }
        const jp = down && !this._prevLmbDown;
        this._prevLmbDown = down;
        return jp;
    }

    _readOriginFromPlayer_into(bodyId, outOrigin) {
        const p = PHYS.position(bodyId);
        outOrigin.x = vx(p, 0);
        outOrigin.y = vy(p, 0) + this.cfg.eyeHeight;
        outOrigin.z = vz(p, 0);
        return outOrigin;
    }

    _dirFromYawPitch_into(yaw, pitch, outDir) {
        // Convention:
        // yaw=0 -> +Z
        // pitch positive usually means "look up" for math,
        // but in your camera system pitch grows when looking DOWN -> invertPitch fixes that.
        const c = this.cfg;

        yaw = num(yaw, 0);
        pitch = num(pitch, 0);

        // avoid reaching exactly +/- 90deg (numerical stability)
        const LIM = (Math.PI * 0.5) - 1e-4;
        pitch = clamp(pitch, -LIM, LIM);

        if (c.invertPitch) pitch = -pitch;

        const sy = Math.sin(yaw), cy = Math.cos(yaw);
        const sp = Math.sin(pitch), cp = Math.cos(pitch);

        // forward direction
        return normalize3_into(sy * cp, sp, cy * cp, outDir);
    }

    _fire(bodyId, yaw, pitch) {
        const c = this.cfg;
        if (!c.enabled) return;

        // origin: stable from player
        this._readOriginFromPlayer_into(bodyId, this._origin);

        // direction: EXACT current look from PlayerDomain (center of screen)
        this._dirFromYawPitch_into(yaw, pitch, this._dir);

        // spawn in front of origin
        const off = c.spawnOffset;
        this._spawn.x = this._origin.x + this._dir.x * off;
        this._spawn.y = this._origin.y + this._dir.y * off;
        this._spawn.z = this._origin.z + this._dir.z * off;

        const name = "shot-" + ((++this._shotId) | 0);

        const g = MSH.loadModel("Models/sharp-boulder-layered.obj", {
            scale: c.radius,
            name: name,
            pos: [this._spawn.x, this._spawn.y, this._spawn.z],
            physics: { mass: c.mass, lockRotation: c.lockRotation }
        });


        g.setMaterial(MAT.getMaterial(c.materialId));


        const speed = c.speed;
        if (g.velocity) {
            g.velocity({
                x: this._dir.x * speed,
                y: this._dir.y * speed,
                z: this._dir.z * speed
            });
        }
        SND.create({
            soundFile: "Sounds/notify.ogg",
            volume: 1.0,
            pitch: 1.0,
            looping: false
        }).play();
    }

    /**
     * @param dom PlayerDomain (preferred)
     * @param state legacy InputRouter state (fallback only)
     */
    update(tpf, bodyId, dom, state) {
        if (!this.cfg.enabled) return;
        if (!bodyId) return;

        if (!this._lmbJustPressed()) return;

        let yaw = 0, pitch = 0;
        if (dom && dom.view) {
            yaw = dom.view.yaw;
            pitch = dom.view.pitch;
        } else if (state) {
            yaw = state.yaw;
            pitch = state.pitch;
        }

        this._fire(bodyId, yaw, pitch);
    }
}

module.exports = ShootSystem;