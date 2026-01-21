// FILE: resources/kalitech/builtin/helpers/hud/HudUtil.js
"use strict";

function isObj(o) {
    return !!o && typeof o === "object" &&
        (Object.getPrototypeOf(o) === Object.prototype || Object.getPrototypeOf(o) === null);
}

function num(v, fb = 0) {
    v = +v;
    return Number.isFinite(v) ? v : fb;
}

function bool(v, fb = true) {
    return typeof v === "boolean" ? v : fb;
}

function idOf(h) {
    return h && typeof h.id === "function" ? h.id() :
        h && h.id != null ? h.id : 0;
}

function round(v) {
    return (v + 0.5) | 0;
}

module.exports = {isObj, num, bool, idOf, round};