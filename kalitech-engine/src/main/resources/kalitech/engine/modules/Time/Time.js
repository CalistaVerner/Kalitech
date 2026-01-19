// FILE: resources/kalitech/engine/modules/Time/Time.js
// Author: Kalitech
"use strict";

function requireApi(engine) {
    if (!engine || typeof engine.time !== "function") {
        throw new Error("[TIME] engine.time() is required");
    }
    const api = engine.time();
    if (!api) throw new Error("[TIME] engine.time() returned null");
    return api;
}

/**
 * Time API wrapper.
 * Provides stable access to frame timing values.
 */
function create(engine /*, K */) {
    const api = requireApi(engine);

    function snapshot() {
        return {
            tpf: api.tpf(),
            dt: api.dt(),
            now: api.now(),
            frame: api.frame()
        };
    }

    return Object.freeze({
        tpf: () => api.tpf(),
        dt: () => api.dt(),
        now: () => api.now(),
        frame: () => api.frame(),
        snapshot,
        api
    });
}

create.META = {
    moduleId: "time",
    globalName: "TIME",
    version: "1.0.0",
    description: "Time wrapper for tpf/dt/now/frame snapshot helpers",
    engineMin: "0.1.0"
};

module.exports = create;
