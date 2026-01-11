"use strict";

const U = require("./camUtil.js");

function lerp(a, b, t) {
    return a + (b - a) * t;
}
function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

class CameraCollisionSolver {
    constructor() {
        this.enabled = true;

        // base tuning (не "fallback": это базовые свойства solver'а)
        this.radius = 0.25;
        this.surfacePadding = 0.08;
        this.floorPadding = 0.20;
        this.maxRayLenDown = 3.0;

        this.smooth = 18.0;
    }

    /**
     * ctx:
     *  - physics.raycast(ox,oy,oz, dx,dy,dz, len) -> {hit:boolean, x,y,z, normal:{x,y,z}} (contract outside)
     *  - target: pivot point
     *  - outPos: desired camera pos (modified in-place)
     *  - dt
     *  - zoneOverrides (optional): camRadius, surfacePadding, floorPadding, collisionEnabled
     */
    solve(ctx) {
        if (!this.enabled) return;

        const phys = ctx.physics;
        if (!phys || typeof phys.raycast !== "function") {
            throw new Error("[camera][collision] physics.raycast() is required");
        }

        const zo = ctx.zoneOverrides || null;
        const collisionEnabled = (zo && zo.collisionEnabled != null) ? !!zo.collisionEnabled : true;
        if (!collisionEnabled) return;

        const radius = (zo && zo.camRadius != null) ? +zo.camRadius : this.radius;
        const pad = (zo && zo.surfacePadding != null) ? +zo.surfacePadding : this.surfacePadding;
        const floorPad = (zo && zo.floorPadding != null) ? +zo.floorPadding : this.floorPadding;

        const from = ctx.target;
        const to = ctx.outPos;

        const dx = to.x - from.x;
        const dy = to.y - from.y;
        const dz = to.z - from.z;

        const len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) return;

        const inv = 1.0 / len;
        const dirx = dx * inv;
        const diry = dy * inv;
        const dirz = dz * inv;

        // 1) sweep pivot -> camera
        const hit = phys.raycast(from.x, from.y, from.z, dirx, diry, dirz, len + radius);

        if (hit && hit.hit) {
            if (!hit.normal) throw new Error("[camera][collision] raycast hit must provide normal");

            const nx = +hit.normal.x;
            const ny = +hit.normal.y;
            const nz = +hit.normal.z;

            // project desired movement onto surface plane (slide)
            const dot = dx * nx + dy * ny + dz * nz;
            const sx = dx - nx * dot;
            const sy = dy - ny * dot;
            const sz = dz - nz * dot;

            // place near contact point with padding
            const cx = (+hit.x) - nx * pad;
            const cy = (+hit.y) - ny * pad;
            const cz = (+hit.z) - nz * pad;

            // small slide (prevents "stuck", feels AAA)
            to.x = cx + sx * 0.25;
            to.y = cy + sy * 0.25;
            to.z = cz + sz * 0.25;
        }

        // 2) terrain/floor clamp (down ray from camera)
        const down = phys.raycast(to.x, to.y + 0.5, to.z, 0, -1, 0, this.maxRayLenDown);
        if (down && down.hit) {
            const minY = (+down.y) + floorPad;
            if (to.y < minY) {
                // smooth lift (no jitter)
                const dt = clamp(U.num(ctx.dt, 1 / 60), 0, 0.05);
                const a = 1 - Math.exp(-(this.smooth > 0 ? this.smooth : 0) * dt);
                to.y = lerp(to.y, minY, a);
            }
        }
    }
}

module.exports = CameraCollisionSolver;