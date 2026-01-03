"use strict";

function num(v, fb) {
    const n = +v;
    return Number.isFinite(n) ? n : (fb || 0);
}

function vx(v, fb) {
    if (!v) return fb || 0;
    try {
        const x = v.x;
        if (typeof x === "function") return num(x.call(v), fb);
        if (typeof x === "number") return x;
    } catch (_) {}
    return fb || 0;
}
function vy(v, fb) {
    if (!v) return fb || 0;
    try {
        const y = v.y;
        if (typeof y === "function") return num(y.call(v), fb);
        if (typeof y === "number") return y;
    } catch (_) {}
    return fb || 0;
}
function vz(v, fb) {
    if (!v) return fb || 0;
    try {
        const z = v.z;
        if (typeof z === "function") return num(z.call(v), fb);
        if (typeof z === "number") return z;
    } catch (_) {}
    return fb || 0;
}

class FirstPersonCameraMode {
    constructor() {
        this.id = "first";

        // ✅ meta tags — orchestrator читает их автоматически
        this.meta = {
            supportsZoom: false,
            hasCollision: false,
            numRays: 0
        };

        this.headOffset = { x: 0.0, y: 1.65, z: 0.0 };
    }

    getPivot(ctx) {
        const p = ctx && ctx.bodyPos;
        if (!p) return { x: 0, y: 0, z: 0 };

        return {
            x: vx(p, 0),
            y: vy(p, 0) + this.headOffset.y,
            z: vz(p, 0)
        };
    }

    update(ctx) {
        const cam = ctx && ctx.cam;
        const p = ctx && ctx.bodyPos;
        if (!cam || !p) return;

        const x = vx(p, 0) + this.headOffset.x;
        const y = vy(p, 0) + this.headOffset.y;
        const z = vz(p, 0) + this.headOffset.z;

        cam.setLocation(num(x, 0), num(y, 0), num(z, 0));

        // ✅ always expose target (used by transitions / consistency)
        ctx.target = {
            x: vx(p, 0),
            y: vy(p, 0) + this.headOffset.y,
            z: vz(p, 0)
        };
    }
}

module.exports = FirstPersonCameraMode;