// FILE: Scripts/lib/math.js
// Author: Calista Verner
//
// Shared math utilities for systems and behaviors.
// Pure functions only. No engine access.

function clamp(v, min, max) {
    return v < min ? min : (v > max ? max : v);
}

function lerp(a, b, t) {
    return a + (b - a) * t;
}

function invLerp(a, b, v) {
    if (a === b) return 0;
    return (v - a) / (b - a);
}

function smoothstep(edge0, edge1, x) {
    const t = clamp((x - edge0) / (edge1 - edge0), 0, 1);
    return t * t * (3 - 2 * t);
}

function degToRad(deg) {
    return deg * (Math.PI / 180);
}

function radToDeg(rad) {
    return rad * (180 / Math.PI);
}

function wrap(v, min, max) {
    const r = max - min;
    return ((v - min) % r + r) % r + min;
}

function length2(x, y) {
    return Math.sqrt(x * x + y * y);
}

function normalize2(x, y) {
    const len = Math.sqrt(x * x + y * y);
    if (len === 0) return { x: 0, y: 0 };
    return { x: x / len, y: y / len };
}

module.exports = {
    clamp,
    lerp,
    invLerp,
    smoothstep,
    degToRad,
    radToDeg,
    wrap,
    length2,
    normalize2
};