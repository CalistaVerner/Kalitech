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
    const worldApi = (engine && typeof engine.world === "function") ? engine.world() : null;

    function snapshot() {
        const w = worldApi && typeof worldApi.getWorldTime === "function" ? worldApi.getWorldTime() : null;
        if (w && typeof w === "object") {
            return {
                tpf: w.stepDt ?? w.simDt ?? 0,
                dt: w.stepDt ?? w.simDt ?? 0,
                now: w.worldTime ?? 0,
                frame: w.frameIndex ?? 0,
                tick: w.tickIndex ?? 0,
                realDt: w.realDt ?? 0,
                simDt: w.simDt ?? 0,
                stepDt: w.stepDt ?? 0,
                interpAlpha: w.interpAlpha ?? 0
            };
        }
        return {
            tpf: api.tpf(),
            dt: api.dt(),
            now: api.now(),
            frame: api.frame(),
            tick: 0,
            realDt: 0,
            simDt: api.dt(),
            stepDt: api.dt(),
            interpAlpha: 1
        };
    }

    return Object.freeze({
        tpf: () => snapshot().tpf,
        dt: () => snapshot().dt,
        now: () => snapshot().now,
        frame: () => snapshot().frame,
        tick: () => snapshot().tick,
        realDt: () => snapshot().realDt,
        simDt: () => snapshot().simDt,
        stepDt: () => snapshot().stepDt,
        interpAlpha: () => snapshot().interpAlpha,
        snapshot,
        api
    });
}

create.META = {
    moduleId: "time",
    id: "time",
    globalName: "TIME",
    version: "2.0.0",
    description: "Time wrapper for world clock snapshots (fixed tick + render interpolation).",
    engineMin: "0.1.0",
    changelog: [
        "2.0.0: route time to WORLD.getWorldTime when available; expose frame/tick/realDt/simDt/stepDt/interpAlpha."
    ],
    deprecation: {
        status: "deprecated",
        since: "2.0.0",
        replaceWith: "ctx.time or WORLD.getWorldTime",
        policy: "Removal only on next major."
    }
};

module.exports = create;
