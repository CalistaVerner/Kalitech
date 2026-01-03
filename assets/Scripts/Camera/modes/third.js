// FILE: Scripts/Camera/modes/third.js
"use strict";

function num(v, fb) { const n = +v; return Number.isFinite(n) ? n : (fb || 0); }
function vx(v, fb) {
    if (!v) return fb || 0;
    try { const x = v.x; if (typeof x === "function") return num(x.call(v), fb); if (typeof x === "number") return x; } catch (_) {}
    return fb || 0;
}
function vy(v, fb) {
    if (!v) return fb || 0;
    try { const y = v.y; if (typeof y === "function") return num(y.call(v), fb); if (typeof y === "number") return y; } catch (_) {}
    return fb || 0;
}
function vz(v, fb) {
    if (!v) return fb || 0;
    try { const z = v.z; if (typeof z === "function") return num(z.call(v), fb); if (typeof z === "number") return z; } catch (_) {}
    return fb || 0;
}

class ThirdPersonCameraMode {
    constructor() {
        this.id = "third";

        // ✅ meta tags (capabilities + hints)
        this.meta = {
            supportsZoom: true,
            hasCollision: true,
            numRays: 6,       // hint for collision quality
        };

        this.pivotOffset = { x: 0.0, y: 1.2, z: 0.0 };
        this.height = 0.4;
    }

    getPivot(ctx) {
        const p = ctx && ctx.bodyPos;
        if (!p) return { x: 0, y: 0, z: 0 };
        return {
            x: vx(p, 0) + this.pivotOffset.x,
            y: vy(p, 0) + this.pivotOffset.y,
            z: vz(p, 0) + this.pivotOffset.z
        };
    }

    update(ctx) {
        const cam = ctx && ctx.cam;
        const p = ctx && ctx.bodyPos;
        if (!cam || !p) return;

        const pivot = this.getPivot(ctx);

        const yaw = ctx.look ? num(ctx.look.yaw, 0) : 0;
        const sin = Math.sin(yaw);
        const cos = Math.cos(yaw);

        // ✅ zoom distance comes from orchestrator (ctx.zoom)
        const dist = (ctx.zoom && typeof ctx.zoom.value === "function") ? num(ctx.zoom.value(), 8) : 8;

        const x = pivot.x - sin * dist;
        const y = pivot.y + this.height;
        const z = pivot.z - cos * dist;

        cam.setLocation(num(x, 0), num(y, 0), num(z, 0));

        // ✅ expose pivot to orchestrator/collision solver
        ctx.target = pivot;
    }
}

module.exports = ThirdPersonCameraMode;