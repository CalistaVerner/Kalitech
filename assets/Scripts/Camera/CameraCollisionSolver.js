// FILE: Scripts/camera/CameraCollisionSolver.js
"use strict";

function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }
function vx(v, fb) { const m = v && v.x; const n = (typeof m === "function") ? +m.call(v) : +m; return Number.isFinite(n) ? n : (fb || 0); }
function vy(v, fb) { const m = v && v.y; const n = (typeof m === "function") ? +m.call(v) : +m; return Number.isFinite(n) ? n : (fb || 0); }
function vz(v, fb) { const m = v && v.z; const n = (typeof m === "function") ? +m.call(v) : +m; return Number.isFinite(n) ? n : (fb || 0); }

function len3(x, y, z) { return Math.sqrt(x * x + y * y + z * z); }
function norm3(x, y, z) {
    const l = len3(x, y, z);
    if (l <= 1e-8) return { x: 0, y: 0, z: 0, l: 0 };
    const il = 1 / l;
    return { x: x * il, y: y * il, z: z * il, l };
}

function hitPoint(hit) {
    if (!hit || typeof hit !== "object") return null;
    const p = hit.point || hit.pos || hit.position || hit.hitPoint || hit.contactPoint;
    if (!p) return null;
    const x = vx(p, NaN), y = vy(p, NaN), z = vz(p, NaN);
    return (Number.isFinite(x) && Number.isFinite(y) && Number.isFinite(z)) ? { x, y, z } : null;
}

function hitFraction(hit) {
    if (!hit || typeof hit !== "object") return NaN;
    const f = hit.fraction ?? hit.frac ?? hit.hitFraction;
    const n = +f;
    return Number.isFinite(n) ? n : NaN;
}

function hitAnyId(hit) {
    if (!hit || typeof hit !== "object") return NaN;
    const v =
        hit.bodyId ?? hit.body ?? hit.rigidBodyId ?? hit.rbId ??
        hit.colliderBodyId ?? hit.objectId ?? hit.objId ??
        hit.entityId ?? hit.surfaceId ?? hit.id ?? hit.hitBodyId;
    const n = +v;
    return Number.isFinite(n) ? (n | 0) : NaN;
}

function isSelfHit(hit, ignoreBodyId) {
    const iid = ignoreBodyId | 0;
    if (!hit || iid <= 0) return false;
    const hid = hitAnyId(hit);
    return Number.isFinite(hid) && ((hid | 0) === iid);
}

// ✅ Resolve PhysicsApi without relying on global `engine`.
// Priority:
//  1) ctx.physics (passed by orchestrator)
//  2) ctx.ph / ctx.PHYS (aliases)
//  3) ctx.engine.physics() (if engine passed)
//  4) legacy global `physics`
//  5) legacy global `PHYS`
function resolvePhysics(ctx) {
    if (ctx) {
        if (ctx.physics) return ctx.physics;
        if (ctx.ph) return ctx.ph;
        if (ctx.PHYS) return ctx.PHYS;

        const eng = ctx.engine;
        if (eng && typeof eng.physics === "function") {
            try {
                return eng.physics();
            } catch (_) {
            }
        }
    }
    try {
        if (typeof physics !== "undefined") return physics;
    } catch (_) {
    }
    try {
        if (typeof PHYS !== "undefined") return PHYS;
    } catch (_) {
    }
    return null;
}

class CameraCollisionSolver {
    constructor() {
        this.enabled = true;
        this.rayStartOffset = 0.95;

        this.pad = 0.22;
        this.minTargetDist = 6.55;

        this.radius = 0.22;

        this.approachSmooth = 24;
        this.returnSmooth = 10;

        this._dist = 0;
        this._hasDist = false;

        this.maxDistSpeed = 50.0;
        this.nearTargetIgnore = 1.25;
    }

    reset() {
        this._hasDist = false;
        this._dist = 0;
    }

    configure(opts) {
        if (!opts || typeof opts !== "object") return;
        if (Number.isFinite(+opts.pad)) this.pad = +opts.pad;
        if (Number.isFinite(+opts.minTargetDist)) this.minTargetDist = +opts.minTargetDist;
        if (Number.isFinite(+opts.radius)) this.radius = +opts.radius;
    }

    solve(ctx) {
        if (!this.enabled) return;

        const cam = ctx && ctx.cam;
        if (!cam) return;

        const dt = Math.max(0, +((ctx && ctx.dt) || 0) || 0);

        const PH = resolvePhysics(ctx);
        if (!PH) return;

        const t = (ctx && ctx.target) || {x: 0, y: 0, z: 0};
        const tx = +t.x || 0, ty = +t.y || 0, tz = +t.z || 0;

        const loc = cam.location();
        const dx = vx(loc, 0) - tx;
        const dy = vy(loc, 0) - ty;
        const dz = vz(loc, 0) - tz;

        const dir = norm3(dx, dy, dz);
        if (dir.l <= 1e-6) return;

        const desiredDist = dir.l;

        if (!this._hasDist) {
            this._hasDist = true;
            this._dist = desiredDist;
        }

        const start = clamp(
            Number.isFinite(this.rayStartOffset) ? this.rayStartOffset : 0.95,
            0.0,
            Math.max(0.01, desiredDist - 0.01)
        );

        const from = [tx + dir.x * start, ty + dir.y * start, tz + dir.z * start];
        const to   = [tx + dir.x * desiredDist, ty + dir.y * desiredDist, tz + dir.z * desiredDist];
        const segLen = desiredDist - start;

        const ignoreBodyId = (ctx && ctx.bodyId) | 0;
        const useEx = (typeof PH.raycastEx === "function");

        let hit = null;
        try {
            if (useEx) hit = PH.raycastEx({ from, to, radius: this.radius, ignoreBodyId });
            else hit = PH.raycast({ from, to, ignoreBodyId });
        } catch (_) { hit = null; }

        if (hit && isSelfHit(hit, ignoreBodyId)) hit = null;

        if (hit) {
            const hp = hitPoint(hit);
            if (hp) {
                const ht = len3(hp.x - tx, hp.y - ty, hp.z - tz);
                if (ht < this.nearTargetIgnore) hit = null;
            }
        }

        let allowed = desiredDist;

        if (hit) {
            let f = hitFraction(hit);

            if (!Number.isFinite(f)) {
                const hp = hitPoint(hit);
                if (hp) {
                    const hh = len3(hp.x - from[0], hp.y - from[1], hp.z - from[2]);
                    f = segLen > 1e-6 ? clamp(hh / segLen, 0, 1) : 0;
                } else {
                    f = 0;
                }
            }

            allowed = start + segLen * clamp(f, 0, 1) - this.pad;
        }

        allowed = clamp(allowed, this.minTargetDist, desiredDist);

        const goingCloser = allowed < this._dist;
        const k = goingCloser ? this.approachSmooth : this.returnSmooth;
        const a = 1 - Math.exp(-Math.max(0, k) * dt);

        let nextDist = this._dist + (allowed - this._dist) * a;

        const maxStep = Math.max(0.01, this.maxDistSpeed * dt);
        const delta = nextDist - this._dist;
        if (delta > maxStep) nextDist = this._dist + maxStep;
        else if (delta < -maxStep) nextDist = this._dist - maxStep;

        this._dist = nextDist;

        cam.setLocation(
            tx + dir.x * nextDist,
            ty + dir.y * nextDist,
            tz + dir.z * nextDist
        );
    }
}

module.exports = CameraCollisionSolver;