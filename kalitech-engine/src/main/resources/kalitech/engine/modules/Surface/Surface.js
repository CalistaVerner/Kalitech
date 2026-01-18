// FILE: resources/kalitech/engine/modules/Surface/Surface.js
// Author: Kalitech
"use strict";

function requireApi(engine) {
    if (!engine || typeof engine.surface !== "function") {
        throw new Error("[SURFACE] engine.surface() is required");
    }
    const api = engine.surface();
    if (!api) throw new Error("[SURFACE] engine.surface() returned null");
    return api;
}

function requireHandle(handle, method) {
    if (!handle) throw new Error("[SURFACE] " + method + " requires a SurfaceHandle");
    return handle;
}

/**
 * Surface API wrapper.
 * Exposes strict handle-based operations and world picking helpers.
 */
function create(engine /*, K */) {
    const api = requireApi(engine);

    return Object.freeze({
        setMaterial(handle, materialHandleOrCfg) {
            api.setMaterial(requireHandle(handle, "setMaterial"), materialHandleOrCfg);
            return handle;
        },

        applyMaterialToChildren(handle, materialHandle) {
            api.applyMaterialToChildren(requireHandle(handle, "applyMaterialToChildren"), materialHandle);
            return handle;
        },

        setTransform(handle, cfg) {
            api.setTransform(requireHandle(handle, "setTransform"), cfg);
            return handle;
        },

        setShadowMode(handle, mode) {
            api.setShadowMode(requireHandle(handle, "setShadowMode"), String(mode));
            return handle;
        },

        attachToRoot(handle) {
            api.attachToRoot(requireHandle(handle, "attachToRoot"));
            return handle;
        },

        detach(handle) {
            api.detach(requireHandle(handle, "detach"));
            return handle;
        },

        destroy(handle) {
            api.destroy(requireHandle(handle, "destroy"));
            return true;
        },

        exists(handle) {
            return !!api.exists(requireHandle(handle, "exists"));
        },

        setCull(handle, hint) {
            api.setCull(requireHandle(handle, "setCull"), String(hint));
            return handle;
        },

        setVisible(handle, visible) {
            api.setVisible(requireHandle(handle, "setVisible"), !!visible);
            return handle;
        },

        attachedBody(surfaceId) {
            return api.attachedBody(surfaceId | 0);
        },

        getWorldBounds(handle) {
            return api.getWorldBounds(requireHandle(handle, "getWorldBounds"));
        },

        raycast(handle, cfg) {
            return api.raycast(requireHandle(handle, "raycast"), cfg);
        },

        pickUnderCursor(handle) {
            return api.pickUnderCursor(requireHandle(handle, "pickUnderCursor"));
        },

        pickUnderCursorCfg(handle, cfg) {
            return api.pickUnderCursorCfg(requireHandle(handle, "pickUnderCursorCfg"), cfg);
        },

        pickWorldUnderCursor() {
            return api.pickUnderCursor();
        },

        pickWorldUnderCursorCfg(cfg) {
            return api.pickUnderCursorCfg(cfg);
        },

        attachEntity(handle, uuid) {
            api.attachEntity(requireHandle(handle, "attachEntity"), uuid);
            return handle;
        },

        detachFromEntity(handle) {
            api.detachFromEntity(requireHandle(handle, "detachFromEntity"));
            return handle;
        },

        attachedEntityUuid(handle) {
            return api.attachedEntityUuid(requireHandle(handle, "attachedEntityUuid"));
        },

        api
    });
}

create.META = {
    moduleId: "surface",
    globalName: "SURFACE",
    version: "1.0.0",
    description: "Surface wrapper for handle-based operations and picking helpers",
    engineMin: "0.1.0"
};

module.exports = create;
