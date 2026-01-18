// FILE: resources/kalitech/builtin/Camera.js
// Author: Calista Verner
"use strict";

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function makeApi(engine /*, K */) {
    req(engine, "[camera] engine is required");

    const log = (engine.log && typeof engine.log === "function") ? engine.log() : null;

    function info(msg) {
        if (log && typeof log.info === "function") log.info(String(msg));
    }

    function warn(msg) {
        if (log && typeof log.warn === "function") log.warn(String(msg));
    }

    // Single source of truth for the orchestrator location.
    // If the file moves, update it here.
    const ORCH_MODULE_ID = "./CameraOrchestrator.js";

    function requireOrchestrator() {
        const Orchestrator = require(ORCH_MODULE_ID);
        if (typeof Orchestrator !== "function") {
            throw new Error("[camera] Orchestrator export must be a function/class: " + ORCH_MODULE_ID);
        }
        return Orchestrator;
    }

    function createOrchestrator(player) {
        req(player, "[camera] createOrchestrator(player): player is required");
        const Orchestrator = requireOrchestrator();
        return new Orchestrator(player);
    }

    function hasOrchestrator() {
        try {
            return typeof require(ORCH_MODULE_ID) === "function";
        } catch (e) {
            warn("[camera] Orchestrator missing: " + ORCH_MODULE_ID);
            return false;
        }
    }

    info("[camera] module ready orchestrator=" + ORCH_MODULE_ID);

    return Object.freeze({
        version: "1.0.0",
        orchestrator: ORCH_MODULE_ID,
        hasOrchestrator,
        createOrchestrator
    });
}

function create(engine, K) {
    return makeApi(engine, K);
}

create.META = {
    moduleId: "camera",
    globalName: "CAMERA",
    version: "1.0.0",
    description: "Engine camera module. Owns player camera orchestrator factory.",
    engineMin: "0.1.0"
};

module.exports = create;
