"use strict";

const U = require("./camUtil.js");
const ZC = require("./CameraZonesContract.js");

function clamp01(v) {
    return v < 0 ? 0 : (v > 1 ? 1 : v);
}

function resetState(st) {
    st.id = null;
    st.weight = 0;
    st.priority = -2147483648;
    st.overrides = null;
    return st;
}

function pointInAabb(px, py, pz, a) {
    return px >= a.min.x && px <= a.max.x &&
        py >= a.min.y && py <= a.max.y &&
        pz >= a.min.z && pz <= a.max.z;
}

function aabbWeight(px, py, pz, a, blend) {
    if (!pointInAabb(px, py, pz, a)) return 0;
    if (!(blend > 0)) return 1;

    const dx = Math.min(px - a.min.x, a.max.x - px);
    const dy = Math.min(py - a.min.y, a.max.y - py);
    const dz = Math.min(pz - a.min.z, a.max.z - pz);
    return clamp01(Math.min(dx, dy, dz) / blend);
}

class CameraVolumeZones {
    constructor(player) {
        if (!player) throw new Error("[camera][zones] player is required");
        this.player = player;

        this.enabled = false;
        this._zones = [];
        this.state = resetState({id: null, weight: 0, priority: 0, overrides: null});
    }

    configureFromPlayerCfg() {
        const cam = this.player.cfg && this.player.cfg.camera ? this.player.cfg.camera : null;
        const vz = cam ? cam.volumeZones : null;

        if (vz == null) {
            this.enabled = false;
            this._zones.length = 0;
            resetState(this.state);
            return;
        }

        const v = ZC.validateZonesConfig(vz);
        this.enabled = v.enabled;
        this._zones = v.zones;
        resetState(this.state);
    }

    update(bodyPos) {
        if (!this.enabled) return resetState(this.state);

        const px = U.vx(bodyPos, 0), py = U.vy(bodyPos, 0), pz = U.vz(bodyPos, 0);

        let best = null;
        let bestW = 0;
        let bestPr = -2147483648;

        for (let i = 0; i < this._zones.length; i++) {
            const z = this._zones[i];
            const w = aabbWeight(px, py, pz, z.shape.aabb, z.blend);
            if (!(w > 0)) continue;

            if (z.priority > bestPr || (z.priority === bestPr && w > bestW)) {
                best = z;
                bestW = w;
                bestPr = z.priority;
            }
        }

        if (!best) return resetState(this.state);

        this.state.id = best.id;
        this.state.weight = bestW;
        this.state.priority = bestPr;
        this.state.overrides = best.overrides;
        return this.state;
    }

    blendedOverrides(base) {
        const st = this.state;
        if (!this.enabled || !st.overrides || !(st.weight > 0)) return base || null;

        const w = st.weight;
        const over = st.overrides;

        const out = Object.create(null);
        if (base) for (const k in base) out[k] = base[k];

        for (const k in over) {
            const v = over[k];

            if (v && typeof v === "object" && v.x != null && v.y != null && v.z != null) {
                const b = out[k];
                if (b && typeof b === "object" && b.x != null && b.y != null && b.z != null) {
                    out[k] = {x: b.x + (v.x - b.x) * w, y: b.y + (v.y - b.y) * w, z: b.z + (v.z - b.z) * w};
                } else out[k] = {x: v.x, y: v.y, z: v.z};
                continue;
            }

            if (typeof v === "number") {
                const b = out[k];
                out[k] = (typeof b === "number") ? (b + (v - b) * w) : v;
                continue;
            }

            if (typeof v === "boolean") {
                out[k] = (w >= 0.5) ? v : (out[k] != null ? out[k] : v);
                continue;
            }

            out[k] = v;
        }

        return out;
    }
}

module.exports = CameraVolumeZones;