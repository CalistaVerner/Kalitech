"use strict";

const U = require("./camUtil.js");

function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

class CameraCollisionSolver {
    constructor() {
        this.enabled = true;

        this.radius = 0.25;
        this.surfacePadding = 0.08;
        this.floorPadding = 0.20;
        this.maxRayLenDown = 3.0;

        this.smooth = 18.0;
    }

    solve(ctx) {
        if (!this.enabled) return;

        const phys = ctx && ctx.physics;
        if (!phys || typeof phys.raycast !== "function") {
            throw new Error("[camera][collision] ctx.physics.raycast(ox,oy,oz, dx,dy,dz, len) is required");
        }

        const zo = ctx.zoneOverrides;
        if (zo && zo.collisionEnabled === false) return;

        const radius = (zo && zo.camRadius != null) ? +zo.camRadius : this.radius;
        const pad = (zo && zo.surfacePadding != null) ? +zo.surfacePadding : this.surfacePadding;
        const floorPad = (zo && zo.floorPadding != null) ? +zo.floorPadding : this.floorPadding;

        const from = ctx.target;
        const to = ctx.outPos;
        if (!from || !to) throw new Error("[camera][collision] ctx.target and ctx.outPos are required");

        const dx = to.x - from.x;
        const dy = to.y - from.y;
        const dz = to.z - from.z;

        const len2 = dx * dx + dy * dy + dz * dz;
        if (len2 < 1e-12) return;

        const len = Math.sqrt(len2);
        const inv = 1.0 / len;

        const hit = phys.raycast(from.x, from.y, from.z, dx * inv, dy * inv, dz * inv, len + radius);

        if (hit && hit.hit) {
            const n = hit.normal;
            if (!n) throw new Error("[camera][collision] raycast hit must provide normal:{x,y,z}");

            const nx = +n.x, ny = +n.y, nz = +n.z;
            if (!Number.isFinite(nx) || !Number.isFinite(ny) || !Number.isFinite(nz)) {
                throw new Error("[camera][collision] raycast normal must be finite");
            }

            const dot = dx * nx + dy * ny + dz * nz;
            const sx = dx - nx * dot;
            const sy = dy - ny * dot;
            const sz = dz - nz * dot;

            const cx = (+hit.x) - nx * pad;
            const cy = (+hit.y) - ny * pad;
            const cz = (+hit.z) - nz * pad;

            to.x = cx + sx * 0.25;
            to.y = cy + sy * 0.25;
            to.z = cz + sz * 0.25;
        }

        const down = phys.raycast(to.x, to.y + 0.5, to.z, 0, -1, 0, this.maxRayLenDown);
        if (down && down.hit) {
            const minY = (+down.y) + floorPad;
            if (to.y < minY) {
                const dt = clamp(U.num(ctx.dt, 1 / 60), 0, 0.05);
                const s = this.smooth > 0 ? this.smooth : 0;
                const a = (s === 0) ? 1 : (1 - Math.exp(-s * dt));
                to.y = to.y + (minY - to.y) * a;
            }
        }
    }
}

module.exports = CameraCollisionSolver;