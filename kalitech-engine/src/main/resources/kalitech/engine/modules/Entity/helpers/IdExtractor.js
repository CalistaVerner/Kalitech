// FILE: resources/kalitech/builtin/helpers/entity/IdExtractor.js
"use strict";

function idOf(h, kind /* "body"|"surface"|"entity" */) {
    if (h == null) return 0;
    if (typeof h === "number") return h | 0;

    if (typeof h.valueOf === "function") {
        const v = h.valueOf(); // may throw -> good (fail loudly)
        if (typeof v === "number" && isFinite(v)) return v | 0;
    }

    if (typeof h.id === "number") return h.id | 0;
    if (typeof h.bodyId === "number") return h.bodyId | 0;
    if (typeof h.surfaceId === "number") return h.surfaceId | 0;
    if (typeof h.entityId === "number") return h.entityId | 0;

    const bodyFns = ["id", "getId", "bodyId", "getBodyId", "handle"];
    const surfFns = ["id", "getId", "surfaceId", "getSurfaceId", "handle"];
    const entFns = ["id", "getId", "entityId", "getEntityId"];

    const fnNames = kind === "body" ? bodyFns : (kind === "surface" ? surfFns : entFns);

    for (let i = 0; i < fnNames.length; i++) {
        const n = fnNames[i];
        const fn = h[n];
        if (typeof fn === "function") {
            const v = fn.call(h);
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
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