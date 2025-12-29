// FILE: Scripts/camera/CameraCollisionSolver.js
// Author: Calista Verner (AAA+ collision + ground clamp)
// Purpose: post-pass collision solver for camera location (after mode + dynamics)

"use strict";

function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }
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

function len3(x, y, z) { return Math.hypot(x, y, z) || 0; }
function norm3(x, y, z) {
    const l = len3(x, y, z);
    if (l <= 1e-8) return { x: 0, y: 0, z: 0, l: 0 };
    return { x: x / l, y: y / l, z: z / l, l };
}
function dot3(ax, ay, az, bx, by, bz) { return ax * bx + ay * by + az * bz; }
function expSmooth(cur, target, smooth, dt) {
    const s = Math.max(0, num(smooth, 0));
    if (s <= 0) return target;
    const a = 1 - Math.exp(-s * dt);
    return cur + (target - cur) * a;
}

// Try to read hit position from unknown hit formats.
// Supports typical patterns: hit.point / hit.position / hit.hitPos / hit.pos / hit.contact / hit.hitPoint
function hitPoint(hit) {
    if (!hit || typeof hit !== "object") return null;
    const p = hit.point || hit.position || hit.hitPos || hit.pos || hit.hitPoint || hit.contact || null;
    if (!p) return null;
    const x = vx(p, NaN), y = vy(p, NaN), z = vz(p, NaN);
    if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z)) return null;
    return { x, y, z };
}

function hitNormal(hit) {
    if (!hit || typeof hit !== "object") return null;
    const n = hit.normal || hit.n || hit.hitNormal || null;
    if (!n) return null;
    const x = vx(n, NaN), y = vy(n, NaN), z = vz(n, NaN);
    if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z)) return null;
    const nn = norm3(x, y, z);
    return nn.l > 0 ? { x: nn.x, y: nn.y, z: nn.z } : null;
}

// best-effort "distance along ray" if engine provides it
function hitFraction(hit) {
    if (!hit || typeof hit !== "object") return NaN;
    const f = hit.fraction ?? hit.t ?? hit.alpha ?? hit.hitFraction;
    const n = +f;
    return Number.isFinite(n) ? n : NaN;
}

class CameraCollisionSolver {
    constructor() {
        this.enabled = true;

        // Core geometry
        this.radius = 0.22;            // "camera sphere radius" (if raycastEx supports it)
        this.pad = 0.18;               // stand-off from obstacle
        this.minTargetDist = 0.28;     // avoid entering target (player pivot)
        this.minY = -1e9;              // optional clamp; keep very low to effectively disable

        // Multisample quality
        this.quality = "high";         // "low" | "high" | "ultra"
        this.ringScale = 1.05;         // ring radius multiplier
        this.verticalSamples = true;   // add up/down samples for edges/stairs
        this.predictive = true;        // predictive sample using velocity

        // Temporal stability
        this.wallSmooth = 32.0;
        this.freeSmooth = 16.0;
        this.maxPullPerSec = 42.0;     // clamp move to prevent pops
        this.popSuppression = 0.75;    // 0..1 resist sudden outward pop

        // Wall slide (reduces jitter on corners)
        this.slide = {
            enabled: true,
            strength: 0.9,             // 0..1
            minNormalDot: 0.08         // ignore tiny normal components
        };

        // Ground clamp (prevents looking under terrain)
        this.ground = {
            enabled: true,
            clearance: 0.22,           // camera must stay above ground hit + clearance
            probeUp: 1.0,              // cast start above camera
            probeDown: 10.0,           // cast down length
            smooth: 24.0,
            maxRisePerSec: 30.0,
            minNormalY: 0.25           // treat hit as "ground" if normal.y >= this
        };

        // Debug
        this.debug = { enabled: false, everyFrames: 120, _f: 0 };

        // Internal state
        this._hasLast = false;
        this._lx = 0; this._ly = 0; this._lz = 0;

        this._hasVel = false;
        this._pvx = 0; this._pvy = 0; this._pvz = 0;
    }

    configure(cfg) {
        if (!cfg) return;

        if (typeof cfg.enabled === "boolean") this.enabled = cfg.enabled;

        if (typeof cfg.radius === "number") this.radius = cfg.radius;
        if (typeof cfg.pad === "number") this.pad = cfg.pad;
        if (typeof cfg.minTargetDist === "number") this.minTargetDist = cfg.minTargetDist;
        if (typeof cfg.minY === "number") this.minY = cfg.minY;

        if (typeof cfg.quality === "string") {
            const q = cfg.quality.trim().toLowerCase();
            if (q === "low" || q === "high" || q === "ultra") this.quality = q;
        }
        if (typeof cfg.ringScale === "number") this.ringScale = clamp(cfg.ringScale, 0.25, 2.0);
        if (typeof cfg.verticalSamples === "boolean") this.verticalSamples = cfg.verticalSamples;
        if (typeof cfg.predictive === "boolean") this.predictive = cfg.predictive;

        if (typeof cfg.wallSmooth === "number") this.wallSmooth = cfg.wallSmooth;
        if (typeof cfg.freeSmooth === "number") this.freeSmooth = cfg.freeSmooth;
        if (typeof cfg.maxPullPerSec === "number") this.maxPullPerSec = cfg.maxPullPerSec;
        if (typeof cfg.popSuppression === "number") this.popSuppression = clamp(cfg.popSuppression, 0, 1);

        if (cfg.slide) {
            if (typeof cfg.slide.enabled === "boolean") this.slide.enabled = cfg.slide.enabled;
            if (typeof cfg.slide.strength === "number") this.slide.strength = clamp(cfg.slide.strength, 0, 1);
            if (typeof cfg.slide.minNormalDot === "number") this.slide.minNormalDot = clamp(cfg.slide.minNormalDot, 0, 1);
        }

        if (cfg.ground) {
            const g = cfg.ground;
            if (typeof g.enabled === "boolean") this.ground.enabled = g.enabled;
            if (typeof g.clearance === "number") this.ground.clearance = g.clearance;
            if (typeof g.probeUp === "number") this.ground.probeUp = g.probeUp;
            if (typeof g.probeDown === "number") this.ground.probeDown = g.probeDown;
            if (typeof g.smooth === "number") this.ground.smooth = g.smooth;
            if (typeof g.maxRisePerSec === "number") this.ground.maxRisePerSec = g.maxRisePerSec;
            if (typeof g.minNormalY === "number") this.ground.minNormalY = g.minNormalY;
        }

        if (cfg.debug) {
            if (typeof cfg.debug.enabled === "boolean") this.debug.enabled = cfg.debug.enabled;
            if (typeof cfg.debug.everyFrames === "number") this.debug.everyFrames = Math.max(1, cfg.debug.everyFrames | 0);
        }

        // sanitize
        this.radius = Math.max(0, this.radius);
        this.pad = Math.max(0, this.pad);
        this.minTargetDist = Math.max(0, this.minTargetDist);
        this.ringScale = clamp(this.ringScale, 0.25, 2.0);
    }

    reset() {
        this._hasLast = false;
        this._hasVel = false;
    }

    solve(ctx) {
        if (!this.enabled) return;

        const cam = ctx && ctx.cam;
        const target = ctx && ctx.target;
        if (!cam || !target) return;

        const dt = clamp(num(ctx.dt, 1 / 60), 1 / 240, 1 / 12);

        let cp;
        try { cp = cam.location(); } catch (_) { cp = null; }
        if (!cp) return;

        let cx = vx(cp, 0), cy = vy(cp, 0), cz = vz(cp, 0);
        if (cy < this.minY) cy = this.minY;

        const tx = num(target.x, 0), ty = num(target.y, 0), tz = num(target.z, 0);

        // Keep camera away from target
        {
            const d = norm3(cx - tx, cy - ty, cz - tz);
            if (d.l > 1e-6 && d.l < this.minTargetDist) {
                cx = tx + d.x * this.minTargetDist;
                cy = ty + d.y * this.minTargetDist;
                cz = tz + d.z * this.minTargetDist;
            }
        }

        const PH = (ctx.physics || (typeof PHYS !== "undefined" ? PHYS : null));
        if (!PH) {
            this._commit(cam, cx, cy, cz);
            return;
        }

        // estimate velocity for predictive sampling
        let pvx = 0, pvy = 0, pvz = 0;
        if (this._hasLast) {
            pvx = (cx - this._lx) / Math.max(1e-4, dt);
            pvy = (cy - this._ly) / Math.max(1e-4, dt);
            pvz = (cz - this._lz) / Math.max(1e-4, dt);
            this._hasVel = true;
            this._pvx = pvx; this._pvy = pvy; this._pvz = pvz;
        } else if (this._hasVel) {
            pvx = this._pvx; pvy = this._pvy; pvz = this._pvz;
        }

        const desired = { x: cx, y: cy, z: cz };
        const baseDir = norm3(desired.x - tx, desired.y - ty, desired.z - tz);
        if (baseDir.l <= 1e-6) {
            this._commit(cam, cx, cy, cz);
            return;
        }

        // Orthonormal basis around ray (world up then corrected)
        let up = { x: 0, y: 1, z: 0 };
        let rx = up.y * baseDir.z - up.z * baseDir.y;
        let ry = up.z * baseDir.x - up.x * baseDir.z;
        let rz = up.x * baseDir.y - up.y * baseDir.x;
        let rN = norm3(rx, ry, rz);
        if (rN.l <= 1e-6) {
            up = { x: 0, y: 0, z: 1 };
            rx = up.y * baseDir.z - up.z * baseDir.y;
            ry = up.z * baseDir.x - up.x * baseDir.z;
            rz = up.x * baseDir.y - up.y * baseDir.x;
            rN = norm3(rx, ry, rz);
        }
        const right = { x: rN.x, y: rN.y, z: rN.z };
        const up2 = {
            x: baseDir.y * right.z - baseDir.z * right.y,
            y: baseDir.z * right.x - baseDir.x * right.z,
            z: baseDir.x * right.y - baseDir.y * right.x
        };
        const uN = norm3(up2.x, up2.y, up2.z);
        const upOrtho = { x: uN.x, y: uN.y, z: uN.z };

        const ring = this.radius * this.ringScale;
        const samples = [];

        // central
        samples.push({ x: desired.x, y: desired.y, z: desired.z, w: 1.00 });

        // ring count by quality
        const q = this.quality;
        const ringCount = (q === "low") ? 4 : (q === "high") ? 6 : 8;
        for (let i = 0; i < ringCount; i++) {
            const a = (i / ringCount) * Math.PI * 2;
            const ca = Math.cos(a), sa = Math.sin(a);
            const ox = right.x * ca + upOrtho.x * sa;
            const oy = right.y * ca + upOrtho.y * sa;
            const oz = right.z * ca + upOrtho.z * sa;
            samples.push({ x: desired.x + ox * ring, y: desired.y + oy * ring, z: desired.z + oz * ring, w: 0.88 });
        }

        if (this.verticalSamples && q !== "low") {
            samples.push({ x: desired.x + upOrtho.x * ring, y: desired.y + upOrtho.y * ring, z: desired.z + upOrtho.z * ring, w: 0.82 });
            samples.push({ x: desired.x - upOrtho.x * ring, y: desired.y - upOrtho.y * ring, z: desired.z - upOrtho.z * ring, w: 0.82 });
        }

        if (this.predictive && this._hasVel) {
            const speed = len3(pvx, pvy, pvz);
            if (speed > 0.2) {
                const vN = norm3(pvx, pvy, pvz);
                const lead = clamp(speed * 0.045, 0.02, 0.28);
                samples.push({ x: desired.x + vN.x * lead, y: desired.y + vN.y * lead, z: desired.z + vN.z * lead, w: 0.90 });
            }
        }

        const ignoreBodyId = ctx.bodyId | 0;
        const useEx = (typeof PH.raycastEx === "function");

        let best = null;
        let bestScore = -1e9;

        let bestBlocked = null;
        let bestBlockedScore = -1e9;

        for (let i = 0; i < samples.length; i++) {
            const s = samples[i];

            // enforce minTargetDist on sample
            const sd = norm3(s.x - tx, s.y - ty, s.z - tz);
            if (sd.l > 1e-6 && sd.l < this.minTargetDist) {
                s.x = tx + sd.x * this.minTargetDist;
                s.y = ty + sd.y * this.minTargetDist;
                s.z = tz + sd.z * this.minTargetDist;
            }

            const from = [tx, ty, tz];
            const to = [s.x, s.y, s.z];

            let hit = null;
            try {
                if (useEx) {
                    hit = PH.raycastEx({ from, to, radius: this.radius, ignoreBodyId });
                } else {
                    hit = PH.raycast({ from, to, ignoreBodyId });
                }
            } catch (_) { hit = null; }

            const blocked = !!hit;
            if (!blocked) {
                const dx = s.x - desired.x, dy = s.y - desired.y, dz = s.z - desired.z;
                const dist = Math.hypot(dx, dy, dz);
                const score = s.w * 1000 - dist * 10;
                if (score > bestScore) { bestScore = score; best = { x: s.x, y: s.y, z: s.z, hit: null, w: s.w }; }
            } else {
                const frac = hitFraction(hit);
                let f = Number.isFinite(frac) ? clamp(frac, 0, 1) : NaN;

                if (!Number.isFinite(f)) {
                    const hp = hitPoint(hit);
                    if (hp) {
                        const seg = len3(to[0] - from[0], to[1] - from[1], to[2] - from[2]);
                        const hh = len3(hp.x - from[0], hp.y - from[1], hp.z - from[2]);
                        f = seg > 1e-6 ? clamp(hh / seg, 0, 1) : 0;
                    } else {
                        f = 0;
                    }
                }

                const score = s.w * 1000 + f * 500;
                if (score > bestBlockedScore) {
                    bestBlockedScore = score;
                    bestBlocked = { x: s.x, y: s.y, z: s.z, hit, w: s.w, f };
                }
            }
        }

        let out = null;
        let hadHit = false;

        if (best) {
            out = { x: best.x, y: Math.max(this.minY, best.y), z: best.z };
            hadHit = false;
        } else if (bestBlocked) {
            hadHit = true;

            const h = bestBlocked.hit;
            const hp = hitPoint(h);

            if (hp) {
                let nx = baseDir.x, ny = baseDir.y, nz = baseDir.z;
                const hn = hitNormal(h);
                if (hn) { nx = hn.x; ny = hn.y; nz = hn.z; }

                // push out from wall
                let px = hp.x + nx * this.pad;
                let py = hp.y + ny * this.pad;
                let pz = hp.z + nz * this.pad;

                // optional wall slide
                if (this.slide.enabled && hn) {
                    // desired move from pushed point -> desired
                    const mvx = desired.x - px;
                    const mvy = desired.y - py;
                    const mvz = desired.z - pz;

                    const into = dot3(mvx, mvy, mvz, hn.x, hn.y, hn.z);
                    if (Math.abs(into) > this.slide.minNormalDot) {
                        const cx2 = hn.x * (into * this.slide.strength);
                        const cy2 = hn.y * (into * this.slide.strength);
                        const cz2 = hn.z * (into * this.slide.strength);

                        const candX = px + (mvx - cx2);
                        const candY = py + (mvy - cy2);
                        const candZ = pz + (mvz - cz2);

                        // validate slide candidate
                        try {
                            const from2 = [tx, ty, tz];
                            const to2 = [candX, candY, candZ];
                            let h2 = null;
                            if (useEx) h2 = PH.raycastEx({ from: from2, to: to2, radius: this.radius, ignoreBodyId });
                            else h2 = PH.raycast({ from: from2, to: to2, ignoreBodyId });

                            if (!h2) {
                                px = candX; py = candY; pz = candZ;
                            }
                        } catch (_) {}
                    }
                }

                // keep away from target again
                const d2 = norm3(px - tx, py - ty, pz - tz);
                if (d2.l > 1e-6 && d2.l < this.minTargetDist) {
                    px = tx + d2.x * this.minTargetDist;
                    py = ty + d2.y * this.minTargetDist;
                    pz = tz + d2.z * this.minTargetDist;
                }

                out = { x: px, y: Math.max(this.minY, py), z: pz };
            } else {
                const d = norm3(desired.x - tx, desired.y - ty, desired.z - tz);
                out = { x: tx + d.x * this.minTargetDist, y: Math.max(this.minY, ty + d.y * this.minTargetDist), z: tz + d.z * this.minTargetDist };
            }
        } else {
            out = { x: desired.x, y: desired.y, z: desired.z };
        }

        // Resist sudden outward pop
        if (this._hasLast && this.popSuppression > 0) {
            const lastToTarget = len3(this._lx - tx, this._ly - ty, this._lz - tz);
            const outToTarget = len3(out.x - tx, out.y - ty, out.z - tz);

            const maxGrow = Math.max(0.06, lastToTarget * (1 - this.popSuppression) + 0.12);
            if (outToTarget > lastToTarget + maxGrow) {
                const dir = norm3(out.x - tx, out.y - ty, out.z - tz);
                out.x = tx + dir.x * (lastToTarget + maxGrow);
                out.y = Math.max(this.minY, ty + dir.y * (lastToTarget + maxGrow));
                out.z = tz + dir.z * (lastToTarget + maxGrow);
            }
        }

        // Smooth + speed clamp
        let fx = out.x, fy = out.y, fz = out.z;

        if (!this._hasLast) {
            this._hasLast = true;
            this._lx = fx; this._ly = fy; this._lz = fz;
        } else {
            const smooth = hadHit ? this.wallSmooth : this.freeSmooth;

            fx = expSmooth(this._lx, fx, smooth, dt);
            fy = expSmooth(this._ly, fy, smooth, dt);
            fz = expSmooth(this._lz, fz, smooth, dt);

            const dx = fx - this._lx, dy = fy - this._ly, dz = fz - this._lz;
            const step = len3(dx, dy, dz);
            const maxStep = Math.max(0.01, this.maxPullPerSec * dt);

            if (step > maxStep) {
                const n = norm3(dx, dy, dz);
                fx = this._lx + n.x * maxStep;
                fy = this._ly + n.y * maxStep;
                fz = this._lz + n.z * maxStep;
            }

            this._lx = fx; this._ly = fy; this._lz = fz;
        }

        // --- AAA: Ground Clamp (prevents looking under terrain)
        if (this.ground && this.ground.enabled) {
            try {
                const g = this.ground;

                const fromG = [fx, fy + g.probeUp, fz];
                const toG   = [fx, fy - g.probeDown, fz];

                let gh = null;
                if (useEx) {
                    gh = PH.raycastEx({ from: fromG, to: toG, radius: 0, ignoreBodyId });
                } else {
                    gh = PH.raycast({ from: fromG, to: toG, ignoreBodyId });
                }

                if (gh) {
                    const hp = hitPoint(gh);
                    const hn = hitNormal(gh);
                    const okN = !hn || (typeof hn.y === "number" ? (hn.y >= g.minNormalY) : true);

                    if (hp && okN) {
                        const minGroundY = hp.y + g.clearance;

                        if (fy < minGroundY) {
                            let targetY = minGroundY;

                            const sm = Math.max(0, num(g.smooth, 0));
                            if (sm > 0) {
                                const aG = 1 - Math.exp(-sm * dt);
                                targetY = fy + (minGroundY - fy) * aG;
                            }

                            const maxUp = Math.max(0.02, num(g.maxRisePerSec, 0) * dt);
                            const dyUp = targetY - fy;
                            if (dyUp > maxUp) targetY = fy + maxUp;

                            fy = targetY;

                            // keep last state consistent with clamped Y to reduce oscillation
                            this._ly = fy;
                        }
                    }
                }
            } catch (_) {}
        }

        this._commit(cam, fx, fy, fz);

        if (this.debug.enabled) {
            this.debug._f++;
            if ((this.debug._f % this.debug.everyFrames) === 0) {
                try {
                    LOG.info(
                        "cam.collision AAA+ q=" + this.quality +
                        " samples=" + (0 + 1 + ((this.quality === "low") ? 4 : (this.quality === "high") ? 6 : 8) + (this.verticalSamples && this.quality !== "low" ? 2 : 0) + (this.predictive && this._hasVel ? 1 : 0)) +
                        " hit=" + (hadHit ? 1 : 0) +
                        " cam=(" + fx.toFixed(2) + "," + fy.toFixed(2) + "," + fz.toFixed(2) + ")"
                    );
                } catch (_) {}
            }
        }
    }

    _commit(cam, x, y, z) {
        try { cam.setLocation(x, y, z); } catch (_) {}
    }
}

module.exports = CameraCollisionSolver;