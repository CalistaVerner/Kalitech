"use strict";

function num(x, def = 0) {
    x = Number(x);
    return Number.isFinite(x) ? x : def;
}

function isObj(x) {
    return x && typeof x === "object";
}

function vec3Obj(v, dx, dy, dz) {
    if (Array.isArray(v)) return {x: num(v[0], dx), y: num(v[1], dy), z: num(v[2], dz)};
    if (isObj(v)) return {x: num(v.x, dx), y: num(v.y, dy), z: num(v.z, dz)};
    return {x: dx, y: dy, z: dz};
}

function vec3Arr(v, dx, dy, dz) {
    if (Array.isArray(v)) return [num(v[0], dx), num(v[1], dy), num(v[2], dz)];
    if (isObj(v)) return [num(v.x, dx), num(v.y, dy), num(v.z, dz)];
    return [dx, dy, dz];
}

function warn(s) {
    if (typeof LOG !== "undefined" && LOG && typeof LOG.warn === "function") {
        LOG.warn(String(s));
    }
}

module.exports = Object.freeze({
    num,
    vec3Obj,
    vec3Arr,
    warn,
});