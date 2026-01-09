// FILE: resources/kalitech/builtin/helpers/entity/PhysicsBinding.js
"use strict";

const {req, subsystem, deepMerge} = require("./EntUtil.js");
const {idOf, surfaceId} = require("./IdExtractor.js");

class PhysicsBinding {
    constructor(engine) {
        this.engine = engine;
    }

    resolveBodyIdBySurface(surfaceHandleOrId) {
        const sid = surfaceId(surfaceHandleOrId);
        if (!sid) return 0;

        const s = subsystem(this.engine, "surface");
        if (typeof s.attachedBody === "function") {
            const bid = s.attachedBody(sid);
            if (typeof bid === "number" && isFinite(bid) && bid > 0) return bid | 0;
        }

        const p = subsystem(this.engine, "physics");
        if (typeof p.bodyOfSurface === "function") {
            const bid = p.bodyOfSurface(sid);
            if (typeof bid === "number" && isFinite(bid) && bid > 0) return bid | 0;
        }

        return 0;
    }

    deriveColliderFromSurfaceCfg(surfaceCfg) {
        if (!surfaceCfg || !surfaceCfg.type) return null;

        const t = String(surfaceCfg.type);
        if (t === "capsule") {
            return {
                type: "capsule",
                radius: (surfaceCfg.radius != null) ? surfaceCfg.radius : 0.35,
                height: (surfaceCfg.height != null) ? surfaceCfg.height : 1.8
            };
        }
        if (t === "sphere") {
            return {type: "sphere", radius: (surfaceCfg.radius != null) ? surfaceCfg.radius : 0.5};
        }
        if (t === "box") {
            return {type: "box", size: (surfaceCfg.size != null) ? surfaceCfg.size : 1};
        }
        return null;
    }

    createBody(bodyDefaults, bodyCfg, surfaceHandle, surfaceCfg) {
        req(bodyCfg && typeof bodyCfg === "object", "[ENT] createBody: bodyCfg object is required");

        const p = subsystem(this.engine, "physics");

        const bCfg = deepMerge(deepMerge({}, bodyDefaults || {}), bodyCfg);

        if (!bCfg.surface && surfaceHandle) bCfg.surface = surfaceHandle;

        if (!bCfg.collider) {
            const inferred = this.deriveColliderFromSurfaceCfg(surfaceCfg);
            if (inferred) bCfg.collider = inferred;
        }

        const bodyHandle = p.body(bCfg);
        const bid = idOf(bodyHandle, "body");

        return {body: bodyHandle, bodyId: bid};
    }
}

module.exports = {PhysicsBinding};