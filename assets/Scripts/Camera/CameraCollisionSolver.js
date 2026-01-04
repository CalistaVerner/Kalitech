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

// Максимально “грязный”, но практичный extractor id (в разных движках/бриджах поля разные)
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

class CameraCollisionSolver {
    constructor() {
        this.enabled = true;
        this.rayStartOffset = 0.95;

        // Stand-off from obstacle
        this.pad = 0.22;

        // Minimum distance from target (avoid entering head)
        this.minTargetDist = 6.55;

        // Optional sphere radius for raycastEx (if supported)
        this.radius = 0.22;

        // Smoothing (critically important to kill jitter)
        this.approachSmooth = 24; // when we need to come closer
        this.returnSmooth = 10;   // when we can go back

        // Distance filter state
        this._dist = 0;       // current filtered distance
        this._hasDist = false;

        // Anti-jitter: how fast distance can change per second
        this.maxDistSpeed = 50.0;

        // Ignore near-target hits (self/inside capsule issues)
        this.nearTargetIgnore = 1.25; // meters (if hit point is within this radius of target => ignore)
    }

    reset() {
        this._hasDist = false;
        this._dist = 0;
    }

    configure(opts) {
        if (!opts || typeof opts !== "object") return;
        // keep compatibility: quality ignored in this stable solver, but accept it.
        if (Number.isFinite(+opts.pad)) this.pad = +opts.pad;
        if (Number.isFinite(+opts.minTargetDist)) this.minTargetDist = +opts.minTargetDist;
        if (Number.isFinite(+opts.radius)) this.radius = +opts.radius;
    }

    solve(ctx) {
        if (!this.enabled) return;

        const cam = ctx.cam;
        const dt = Math.max(0, +ctx.dt || 0);

        const PH = (typeof physics !== "undefined") ? physics : (engine && engine.physics && engine.physics());
        if (!PH) return;

        const t = ctx.target || { x: 0, y: 0, z: 0 };
        const tx = +t.x || 0, ty = +t.y || 0, tz = +t.z || 0;

        // desired camera position already set by mode before calling solve()
        const loc = cam.location();
        const dx = vx(loc, 0) - tx;
        const dy = vy(loc, 0) - ty;
        const dz = vz(loc, 0) - tz;

        const dir = norm3(dx, dy, dz);
        if (dir.l <= 1e-6) return;

        const desiredDist = dir.l;

        // Init filtered distance
        if (!this._hasDist) {
            this._hasDist = true;
            this._dist = desiredDist;
        }

        // ✅ Raycast along target -> desired, but start OUTSIDE the player's body
        // This prevents "inside capsule/mesh" self-hits that clamp camera to minTargetDist (head).
        const start = clamp(
            Number.isFinite(this.rayStartOffset) ? this.rayStartOffset : 0.95,
            0.0,
            Math.max(0.01, desiredDist - 0.01)
        );

        const from = [tx + dir.x * start, ty + dir.y * start, tz + dir.z * start];
        const to   = [tx + dir.x * desiredDist, ty + dir.y * desiredDist, tz + dir.z * desiredDist];
        const segLen = desiredDist - start;

        const ignoreBodyId = ctx.bodyId | 0;
        const useEx = (typeof PH.raycastEx === "function");

        let hit = null;
        try {
            if (useEx) hit = PH.raycastEx({ from, to, radius: this.radius, ignoreBodyId });
            else hit = PH.raycast({ from, to, ignoreBodyId });
        } catch (_) { hit = null; }

        // Self-hit filter by id (если движок не уважает ignoreBodyId)
        if (hit && isSelfHit(hit, ignoreBodyId)) hit = null;

        // Extra: ignore hits too close to target (часто это капсула игрока/поверхностный меш)
        if (hit) {
            const hp = hitPoint(hit);
            if (hp) {
                const ht = len3(hp.x - tx, hp.y - ty, hp.z - tz);
                if (ht < this.nearTargetIgnore) hit = null;
            }
        }

        // Compute allowed distance
        let allowed = desiredDist;

        if (hit) {
            let f = hitFraction(hit);

            if (!Number.isFinite(f)) {
                const hp = hitPoint(hit);
                if (hp) {
                    // fraction along segment (from->to), not from target->to
                    const hh = len3(hp.x - from[0], hp.y - from[1], hp.z - from[2]);
                    f = segLen > 1e-6 ? clamp(hh / segLen, 0, 1) : 0;
                } else {
                    f = 0;
                }
            }

            // allowed = start + hitAlongSegment - pad
            allowed = start + segLen * clamp(f, 0, 1) - this.pad;
        }

        // Enforce bounds
        allowed = clamp(allowed, this.minTargetDist, desiredDist);

        // Smooth distance with different rates
        const goingCloser = allowed < this._dist;
        const k = goingCloser ? this.approachSmooth : this.returnSmooth;
        const a = 1 - Math.exp(-Math.max(0, k) * dt);

        let nextDist = this._dist + (allowed - this._dist) * a;

        // Hard rate limit to kill remaining oscillations
        const maxStep = Math.max(0.01, this.maxDistSpeed * dt);
        const delta = nextDist - this._dist;
        if (delta > maxStep) nextDist = this._dist + maxStep;
        else if (delta < -maxStep) nextDist = this._dist - maxStep;

        this._dist = nextDist;

        // Apply final camera pos
        const nx = tx + dir.x * nextDist;
        const ny = ty + dir.y * nextDist;
        const nz = tz + dir.z * nextDist;

        cam.setLocation(nx, ny, nz);
    }

}

module.exports = CameraCollisionSolver;
