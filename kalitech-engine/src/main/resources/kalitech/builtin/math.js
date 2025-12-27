// FILE: resources/kalitech/builtin/math.js
// Author: Calista Verner
//
// Builtin Math utilities (pure functions, deterministic, no engine access).
// Goal: self-contained "standard library" for scripts.
//
// Conventions:
// - Numbers in, numbers out. Vectors are plain objects: {x,y} / {x,y,z}
// - Functions never mutate inputs (return new objects), unless name ends with "Into" (none here).
// - All functions are safe for NaN/Infinity only insofar as JS allows (no heavy guarding).

"use strict";

const TAU = Math.PI * 2;
const EPS = 1e-6;

// ---------- scalars ----------
function isFiniteNumber(v) { return typeof v === "number" && Number.isFinite(v); }

function clamp(v, min, max) { return v < min ? min : (v > max ? max : v); }
function clamp01(v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
function saturate(v) { return clamp01(v); }

function lerp(a, b, t) { return a + (b - a) * t; }
function invLerp(a, b, v) { return a === b ? 0 : (v - a) / (b - a); }
function remap(inMin, inMax, outMin, outMax, v) {
    const t = invLerp(inMin, inMax, v);
    return lerp(outMin, outMax, t);
}

function abs(v) { return Math.abs(v); }
function sign(v) { return v < 0 ? -1 : (v > 0 ? 1 : 0); }
function sqr(v) { return v * v; }

function min(a, b) { return a < b ? a : b; }
function max(a, b) { return a > b ? a : b; }
function min3(a, b, c) { return min(a, min(b, c)); }
function max3(a, b, c) { return max(a, max(b, c)); }

function approx(a, b, eps) {
    const e = eps == null ? EPS : eps;
    return Math.abs(a - b) <= e;
}

function snap(v, step) {
    if (step === 0) return v;
    return Math.round(v / step) * step;
}

function fract(v) { return v - Math.floor(v); }
function mod(v, m) { return ((v % m) + m) % m; } // positive modulo
function wrap(v, minV, maxV) {
    const r = maxV - minV;
    if (r === 0) return minV;
    return mod(v - minV, r) + minV;
}
function pingPong(t, length) {
    const l = length == null ? 1 : length;
    if (l === 0) return 0;
    const tt = mod(t, l * 2);
    return l - Math.abs(tt - l);
}

function smoothstep(edge0, edge1, x) {
    if (edge0 === edge1) return x < edge0 ? 0 : 1;
    const t = clamp01((x - edge0) / (edge1 - edge0));
    return t * t * (3 - 2 * t);
}
function smootherstep(edge0, edge1, x) {
    if (edge0 === edge1) return x < edge0 ? 0 : 1;
    const t = clamp01((x - edge0) / (edge1 - edge0));
    return t * t * t * (t * (t * 6 - 15) + 10);
}

function degToRad(deg) { return deg * (Math.PI / 180); }
function radToDeg(rad) { return rad * (180 / Math.PI); }

function angleWrapRad(rad) { return wrap(rad, -Math.PI, Math.PI); }
function angleWrapDeg(deg) { return wrap(deg, -180, 180); }

function deltaAngleRad(a, b) { return angleWrapRad(b - a); }
function deltaAngleDeg(a, b) { return angleWrapDeg(b - a); }

function lerpAngleRad(a, b, t) { return a + deltaAngleRad(a, b) * t; }
function lerpAngleDeg(a, b, t) { return a + deltaAngleDeg(a, b) * t; }

function moveTowards(current, target, maxDelta) {
    const d = target - current;
    if (Math.abs(d) <= maxDelta) return target;
    return current + sign(d) * maxDelta;
}
function moveTowardsAngleRad(current, target, maxDelta) {
    const d = deltaAngleRad(current, target);
    if (Math.abs(d) <= maxDelta) return target;
    return current + sign(d) * maxDelta;
}
function moveTowardsAngleDeg(current, target, maxDelta) {
    const d = deltaAngleDeg(current, target);
    if (Math.abs(d) <= maxDelta) return target;
    return current + sign(d) * maxDelta;
}

// Exponential smoothing: stable across fps (Unity-like)
function damp(current, target, lambda, dt) {
    // lambda: bigger -> faster
    return lerp(current, target, 1 - Math.exp(-lambda * dt));
}

function randNum(min, max) {
    min = +min;
    max = +max;
    if (!Number.isFinite(min) || !Number.isFinite(max)) {
        throw new Error("randNum(min, max): min/max must be numbers");
    }
    if (max < min) {
        const t = min; min = max; max = t;
    }
    return min + Math.random() * (max - min);
}


// ---------- deterministic hashes (no randomness) ----------
function hash1i(x) {
    // 32-bit integer hash -> [0..1)
    let v = x | 0;
    v ^= v >>> 16;
    v = Math.imul(v, 0x7feb352d);
    v ^= v >>> 15;
    v = Math.imul(v, 0x846ca68b);
    v ^= v >>> 16;
    return (v >>> 0) / 4294967296;
}
function hash2i(x, y) {
    return hash1i((Math.imul(x | 0, 374761393) ^ Math.imul(y | 0, 668265263)) | 0);
}
function hash3i(x, y, z) {
    const h = (Math.imul(x | 0, 374761393) ^ Math.imul(y | 0, 668265263) ^ Math.imul(z | 0, 2147483647)) | 0;
    return hash1i(h);
}

// ---------- vec2 ----------
function v2(x, y) { return { x: x || 0, y: y || 0 }; }
function v2add(a, b) { return { x: a.x + b.x, y: a.y + b.y }; }
function v2sub(a, b) { return { x: a.x - b.x, y: a.y - b.y }; }
function v2mul(a, s) { return { x: a.x * s, y: a.y * s }; }
function v2div(a, s) { return { x: a.x / s, y: a.y / s }; }
function v2dot(a, b) { return a.x * b.x + a.y * b.y; }
function v2perp(a) { return { x: -a.y, y: a.x }; }
function v2lenSq(a) { return a.x * a.x + a.y * a.y; }
function v2len(a) { return Math.sqrt(v2lenSq(a)); }
function v2distSq(a, b) { return sqr(a.x - b.x) + sqr(a.y - b.y); }
function v2dist(a, b) { return Math.sqrt(v2distSq(a, b)); }
function v2norm(a) {
    const l = v2len(a);
    if (l <= EPS) return { x: 0, y: 0 };
    return { x: a.x / l, y: a.y / l };
}
function v2clampLen(a, maxLen) {
    const l = v2len(a);
    if (l <= maxLen) return { x: a.x, y: a.y };
    if (l <= EPS) return { x: 0, y: 0 };
    const k = maxLen / l;
    return { x: a.x * k, y: a.y * k };
}
function v2lerp(a, b, t) { return { x: lerp(a.x, b.x, t), y: lerp(a.y, b.y, t) }; }
function v2rotate(a, rad) {
    const c = Math.cos(rad), s = Math.sin(rad);
    return { x: a.x * c - a.y * s, y: a.x * s + a.y * c };
}
function v2angle(a) { return Math.atan2(a.y, a.x); }
function v2fromAngle(rad, len) {
    const l = len == null ? 1 : len;
    return { x: Math.cos(rad) * l, y: Math.sin(rad) * l };
}

// ---------- vec3 ----------
function v3(x, y, z) { return { x: x || 0, y: y || 0, z: z || 0 }; }
function v3add(a, b) { return { x: a.x + b.x, y: a.y + b.y, z: a.z + b.z }; }
function v3sub(a, b) { return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z }; }
function v3mul(a, s) { return { x: a.x * s, y: a.y * s, z: a.z * s }; }
function v3div(a, s) { return { x: a.x / s, y: a.y / s, z: a.z / s }; }
function v3dot(a, b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
function v3cross(a, b) {
    return {
        x: a.y * b.z - a.z * b.y,
        y: a.z * b.x - a.x * b.z,
        z: a.x * b.y - a.y * b.x
    };
}
function v3lenSq(a) { return a.x * a.x + a.y * a.y + a.z * a.z; }
function v3len(a) { return Math.sqrt(v3lenSq(a)); }
function v3distSq(a, b) { return sqr(a.x - b.x) + sqr(a.y - b.y) + sqr(a.z - b.z); }
function v3dist(a, b) { return Math.sqrt(v3distSq(a, b)); }
function v3norm(a) {
    const l = v3len(a);
    if (l <= EPS) return { x: 0, y: 0, z: 0 };
    return { x: a.x / l, y: a.y / l, z: a.z / l };
}
function v3clampLen(a, maxLen) {
    const l = v3len(a);
    if (l <= maxLen) return { x: a.x, y: a.y, z: a.z };
    if (l <= EPS) return { x: 0, y: 0, z: 0 };
    const k = maxLen / l;
    return { x: a.x * k, y: a.y * k, z: a.z * k };
}
function v3lerp(a, b, t) { return { x: lerp(a.x, b.x, t), y: lerp(a.y, b.y, t), z: lerp(a.z, b.z, t) }; }

// ---------- geometry ----------
function pointInRect(px, py, rx, ry, rw, rh) {
    return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
}
function aabbIntersects(ax, ay, aw, ah, bx, by, bw, bh) {
    return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
}
function aabbIntersection(ax, ay, aw, ah, bx, by, bw, bh) {
    if (!aabbIntersects(ax, ay, aw, ah, bx, by, bw, bh)) return null;
    const x0 = max(ax, bx);
    const y0 = max(ay, by);
    const x1 = min(ax + aw, bx + bw);
    const y1 = min(ay + ah, by + bh);
    return { x: x0, y: y0, w: x1 - x0, h: y1 - y0 };
}

// Ray-plane intersection (plane by point+normal). Returns t (distance along ray) or null.
function rayPlane(ro, rd, p0, n) {
    const denom = v3dot(rd, n);
    if (Math.abs(denom) <= EPS) return null;
    const t = v3dot(v3sub(p0, ro), n) / denom;
    return t >= 0 ? t : null;
}

// ---------- easing (small but useful) ----------
function easeInQuad(t) { t = clamp01(t); return t * t; }
function easeOutQuad(t) { t = clamp01(t); return t * (2 - t); }
function easeInOutQuad(t) { t = clamp01(t); return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t; }

function easeInCubic(t) { t = clamp01(t); return t * t * t; }
function easeOutCubic(t) { t = clamp01(t); t -= 1; return t * t * t + 1; }
function easeInOutCubic(t) {
    t = clamp01(t);
    return t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;
}

// ---------- export ----------
module.exports = {
    // constants
    TAU,
    EPS,

    // scalar
    isFiniteNumber,
    clamp,
    clamp01,
    saturate,
    lerp,
    invLerp,
    remap,
    abs,
    sign,
    sqr,
    min,
    max,
    min3,
    max3,
    approx,
    snap,
    fract,
    mod,
    wrap,
    pingPong,
    smoothstep,
    smootherstep,
    degToRad,
    radToDeg,
    angleWrapRad,
    angleWrapDeg,
    deltaAngleRad,
    deltaAngleDeg,
    lerpAngleRad,
    lerpAngleDeg,
    moveTowards,
    moveTowardsAngleRad,
    moveTowardsAngleDeg,
    damp,

    // hashes
    hash1i,
    hash2i,
    hash3i,

    // vec2
    v2,
    v2add,
    v2sub,
    v2mul,
    v2div,
    v2dot,
    v2perp,
    v2lenSq,
    v2len,
    v2distSq,
    v2dist,
    v2norm,
    v2clampLen,
    v2lerp,
    v2rotate,
    v2angle,
    v2fromAngle,

    // vec3
    v3,
    v3add,
    v3sub,
    v3mul,
    v3div,
    v3dot,
    v3cross,
    v3lenSq,
    v3len,
    v3distSq,
    v3dist,
    v3norm,
    v3clampLen,
    v3lerp,

    // geometry
    pointInRect,
    aabbIntersects,
    aabbIntersection,
    rayPlane,

    // easing
    easeInQuad,
    easeOutQuad,
    easeInOutQuad,
    easeInCubic,
    easeOutCubic,
    easeInOutCubic
};