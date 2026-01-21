"use strict";

function toInt32(v) {
    if (v == null) return 0;

    if (typeof v === "number") {
        return Number.isFinite(v) ? (v | 0) : 0;
    }

    if (typeof v === "bigint") {
        const n = Number(v);
        return Number.isFinite(n) ? (n | 0) : 0;
    }

    if (typeof v === "string") {
        const s = v.trim();
        if (!s) return 0;
        const n = Number(s);
        if (Number.isFinite(n)) return n | 0;

        const p = parseInt(s, 10);
        return Number.isFinite(p) ? (p | 0) : 0;
    }

    if (typeof v.valueOf === "function") {
        const vv = v.valueOf(); // may throw -> fail loudly
        if (vv !== v) return toInt32(vv);
    }

    if (typeof v.intValue === "function") {
        const n = Number(v.intValue());
        return Number.isFinite(n) ? (n | 0) : 0;
    }

    if (typeof v.longValue === "function") {
        const n = Number(v.longValue());
        return Number.isFinite(n) ? (n | 0) : 0;
    }

    const n = Number(v);
    return Number.isFinite(n) ? (n | 0) : 0;
}

function idOf(h, kind /* "body"|"surface" */) {
    if (h == null) return 0;

    const direct = toInt32(h);
    if (direct > 0) return direct;

    if (typeof h.id === "number") return h.id | 0;
    if (typeof h.bodyId === "number") return h.bodyId | 0;
    if (typeof h.surfaceId === "number") return h.surfaceId | 0;

    const props = ["id", "bodyId", "surfaceId", "handleId", "nativeId"];
    for (let i = 0; i < props.length; i++) {
        const p = props[i];
        if (h[p] != null) {
            const n = toInt32(h[p]);
            if (n > 0) return n;
        }
    }

    const bodyFns = [
        "id", "getId",
        "bodyId", "getBodyId",
        "handleId", "getHandleId",
        "nativeId", "getNativeId",
        "handle"
    ];

    const surfFns = [
        "id", "getId",
        "surfaceId", "getSurfaceId",
        "handleId", "getHandleId",
        "nativeId", "getNativeId",
        "handle"
    ];

    const fnNames = (kind === "body") ? bodyFns : surfFns;

    for (let i = 0; i < fnNames.length; i++) {
        const name = fnNames[i];
        const fn = h[name];
        if (typeof fn !== "function") continue;

        const v = fn.call(h); // may throw -> good
        const n = toInt32(v);
        if (n > 0) return n;
    }

    return 0;
}

function surfaceId(handleOrId) {
    return idOf(handleOrId, "surface");
}

function bodyId(handleOrId) {
    return idOf(handleOrId, "body");
}

module.exports = {idOf, surfaceId, bodyId};