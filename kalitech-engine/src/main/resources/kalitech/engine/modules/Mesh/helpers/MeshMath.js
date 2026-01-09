"use strict";

function num(v, fb) {
    const n = +v;
    return Number.isFinite(n) ? n : (fb || 0);
}

function isObj(v) {
    return v !== null && typeof v === "object" && !Array.isArray(v);
}

function normalizePos(p) {
    if (Array.isArray(p)) return [num(p[0], 0), num(p[1], 0), num(p[2], 0)];
    if (isObj(p)) {
        const x = (p.x != null) ? p.x : p[0];
        const y = (p.y != null) ? p.y : p[1];
        const z = (p.z != null) ? p.z : p[2];
        return [num(x, 0), num(y, 0), num(z, 0)];
    }
    return undefined;
}

module.exports = Object.freeze({
    num,
    isObj,
    normalizePos,
});