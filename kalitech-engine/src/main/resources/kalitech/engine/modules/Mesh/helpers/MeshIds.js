"use strict";

// ------------------- strict assumptions -------------------
// 1) SurfaceHandle provides id():int
// 2) ENGINE.surface().attachedBody(surfaceId)->int
// 3) ENGINE.physics() operates only on bodyId

function surfaceId(handle) {
    if (!handle || typeof handle.id !== "function") throw new Error("[MSH] SurfaceHandle must provide id()");
    const sid = handle.id() | 0;
    if (sid <= 0) throw new Error("[MSH] invalid surfaceId=" + sid);
    return sid;
}

function requireSurface(engine) {
    const s = engine.surface();
    if (!s || typeof s.attachedBody !== "function") {
        throw new Error("[MSH] ENGINE.surface().attachedBody(surfaceId) is required");
    }
    return s;
}

function requirePhysics(engine) {
    const p = engine.physics();
    if (!p) throw new Error("[MSH] ENGINE.physics() is required");
    return p;
}

function resolveBodyId(engine, sid) {
    const s = requireSurface(engine);
    const bid = (s.attachedBody(sid) | 0);
    if (bid <= 0) throw new Error("[MSH] surface has no physics body (bodyId=0)");
    return bid;
}

module.exports = Object.freeze({
    surfaceId,
    requireSurface,
    requirePhysics,
    resolveBodyId,
});
