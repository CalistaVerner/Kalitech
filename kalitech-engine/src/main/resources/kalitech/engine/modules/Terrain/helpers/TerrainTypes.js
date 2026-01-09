// FILE: resources/kalitech/builtin/terrain/TerrainTypes.js
"use strict";

function isObj(v) {
    return !!v && typeof v === "object" && !Array.isArray(v);
}

function num(v, def) {
    const n = +v;
    return Number.isFinite(n) ? n : def;
}

function i32(v, def) {
    const n = (v | 0);
    return n !== 0 ? n : (def | 0);
}

module.exports = {isObj, num, i32};