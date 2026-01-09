// FILE: resources/kalitech/builtin/terrain/TerrainPhysics.js
"use strict";

const {isObj} = require("./TerrainTypes.js");

function surfaceIdOf(h) {
    if (typeof h === "number") return h | 0;
    if (!h) return 0;
    if (typeof h.id === "function") return h.id() | 0;
    if (typeof h.id === "number") return h.id | 0;
    if (typeof h.surfaceId === "number") return h.surfaceId | 0;
    return 0;
}

function bodyIdOf(h) {
    if (typeof h === "number") return h | 0;
    if (!h) return 0;
    if (typeof h.id === "function") return h.id() | 0;
    if (typeof h.id === "number") return h.id | 0;
    if (typeof h.bodyId === "number") return h.bodyId | 0;
    return 0;
}

class TerrainPhysics {
    constructor(engine) {
        this.engine = engine;
    }

    resolveBodyId(surfaceHandleOrId, maybeBodyHandleOrId) {
        const sid = surfaceIdOf(surfaceHandleOrId);
        if (sid <= 0) return 0;

        const E = this.engine;

        // prefer surface-attached body (surface API)
        if (E.surface && typeof E.surface === "function") {
            const s = E.surface();
            if (s && typeof s.attachedBody === "function") {
                const bid = bodyIdOf(s.attachedBody(sid));
                if (bid > 0) return bid;
            }
        }

        // fallback physics link (physics API)
        if (E.physics && typeof E.physics === "function") {
            const p = E.physics();
            if (p && typeof p.bodyOfSurface === "function") {
                const bid = bodyIdOf(p.bodyOfSurface(sid));
                if (bid > 0) return bid;
            }
        }

        const bid = bodyIdOf(maybeBodyHandleOrId);
        return (bid > 0) ? bid : 0;
    }

    ensureStaticBody(surfaceHandleOrId, physCfg, defaultColliderType) {
        const sid = surfaceIdOf(surfaceHandleOrId);
        if (sid <= 0) return {bodyId: 0, bodyHandle: null};

        const existing = this.resolveBodyId(sid, null);
        if (existing > 0) return {bodyId: existing, bodyHandle: null};

        const base = {
            surface: sid,
            mass: 0,
            kinematic: true,
            collider: {type: defaultColliderType || "mesh"},
        };
        const cfg = isObj(physCfg) ? Object.assign(base, physCfg) : base;

        // strict: physics must exist
        let bodyHandle = null;
        if (typeof PHYS !== "undefined" && PHYS && typeof PHYS.body === "function") {
            bodyHandle = PHYS.body(cfg);
        } else {
            const E = this.engine;
            const p = (E.physics && typeof E.physics === "function") ? E.physics() : null;
            if (!p || typeof p.body !== "function") throw new Error("[TERR] physics.body(cfg) is required");
            bodyHandle = p.body(cfg);
        }

        const bodyId = this.resolveBodyId(sid, bodyHandle);
        return {bodyId, bodyHandle};
    }

    withBody(terrNative, surface, physCfg, defaultColliderType) {
        if (physCfg == null) return surface;

        let bodyHandle = null;

        // prefer native terr.physics(surface,cfg) if present
        if (terrNative && typeof terrNative.physics === "function") {
            bodyHandle = terrNative.physics(surface, physCfg);
        }

        const sid = surfaceIdOf(surface);
        let bodyId = this.resolveBodyId(sid, bodyHandle);

        if (bodyId <= 0) {
            const made = this.ensureStaticBody(surface, physCfg, defaultColliderType || "mesh");
            bodyId = made.bodyId;
            if (!bodyHandle) bodyHandle = made.bodyHandle;
        }

        const bodyRef =
            (bodyId > 0 && typeof PHYS !== "undefined" && PHYS && typeof PHYS.ref === "function")
                ? PHYS.ref(bodyId)
                : undefined;

        return Object.freeze({surface, bodyId, body: bodyRef, handle: bodyHandle});
    }
}

module.exports = {TerrainPhysics, surfaceIdOf, bodyIdOf};