"use strict";

function num(v, fb) { v = +v; return Number.isFinite(v) ? v : fb; }

function vget(v, k, fb) {
    if (!v) return fb;
    const m = v[k];
    return (typeof m === "function") ? num(m.call(v), fb) : num(m, fb);
}

function vx(v, fb = 0) { return vget(v, "x", fb); }
function vy(v, fb = 0) { return vget(v, "y", fb); }
function vz(v, fb = 0) { return vget(v, "z", fb); }

function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

function expSmooth(cur, target, smooth, dt) {
    const s = smooth > 0 ? smooth : 0;
    if (s === 0) return target;
    const a = 1 - Math.exp(-s * dt);
    return cur + (target - cur) * a;
}

module.exports = { num, vx, vy, vz, clamp, expSmooth };
