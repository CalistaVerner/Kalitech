// FILE: Scripts/systems/editor.grid.system.js
// Author: Calista Verner
"use strict";

exports.meta = {
    id: "kalitech.system.editor.grid",
    version: "1.1.0",
    apiMin: "0.1.0",
    name: "Editor Grid Plane"
};

exports.create = function () {
    var state = {
        enabled: true,
        size: 200,
        step: 1.0,
        majorStep: 10,
        y: 0.01,
        opacity: 0.35
    };

    var created = false;
    var handle = null;
    var warned = false;

    function tryCreate(log) {
        if (created) return true;

        var editor = null, render = null, entity = null;
        try { editor = engine.editor(); } catch (_) {}
        try { render = engine.render(); } catch (_) {}
        try { entity = engine.entity(); } catch (_) {}

        // контракт: если метод есть — он вызывается; если нет — ловим исключение
        try {
            handle = editor.createGridPlane({
                size: state.size, step: state.step, majorStep: state.majorStep, y: state.y, opacity: state.opacity
            });
            created = true;
            log.info("[editor.grid] created via editor.createGridPlane handle=" + String(handle));
            return true;
        } catch (_) {}

        try {
            handle = render.createGridPlane({
                size: state.size, step: state.step, majorStep: state.majorStep, y: state.y, opacity: state.opacity
            });
            created = true;
            log.info("[editor.grid] created via render.createGridPlane handle=" + String(handle));
            return true;
        } catch (_) {}

        try {
            handle = entity.create({ name: "editor.grid", tags: ["editor", "grid"] });
            created = true;
            log.info("[editor.grid] created placeholder entityId=" + String(handle));
            return true;
        } catch (_) {}

        if (!warned) {
            warned = true;
            log.warn("[editor.grid] no grid backend available yet.");
        }
        return false;
    }

    function tryDestroy(log, reason) {
        if (!created) return;

        var editor = null, render = null, entity = null;
        try { editor = engine.editor(); } catch (_) {}
        try { render = engine.render(); } catch (_) {}
        try { entity = engine.entity(); } catch (_) {}

        try { editor.destroyGridPlane(handle); } catch (_) {}
        try { render.destroyGridPlane(handle); } catch (_) {}
        try { entity.destroy(handle); } catch (_) {}

        log.info("[editor.grid] destroyed reason=" + String(reason || "unknown"));
        created = false;
        handle = null;
    }

    return {
        init: function (ctx) {
            var log = engine.log();
            try {
                var cfg = (ctx && ctx.config) ? ctx.config : ctx;
                if (cfg && typeof cfg === "object") state = deepMerge(state, cfg);
            } catch (_) {}

            state.size = clamp(state.size, 10, 5000);
            state.step = clamp(state.step, 0.1, 100);
            state.majorStep = clamp(state.majorStep, 1, 1000);
            state.opacity = clamp(state.opacity, 0.0, 1.0);

            if (state.enabled) tryCreate(log);
        },

        update: function (tpf) {
            if (!state.enabled) return;
            if (!created) {
                // простой “редкий” ретрай без time?.timeSec()
                // (всё равно дешево — try/catch)
                tryCreate(engine.log());
            }
        },

        destroy: function (reason) {
            tryDestroy(engine.log(), reason);
        },

        serialize: function () { return state; },

        deserialize: function (s) {
            if (s && typeof s === "object") state = deepMerge(state, s);
        }
    };
};