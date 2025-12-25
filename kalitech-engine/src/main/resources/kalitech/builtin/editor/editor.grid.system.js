// FILE: Scripts/systems/editor.grid.system.js
// Author: Calista Verner
"use strict";

exports.meta = {
    id: "kalitech.system.editor.grid",
    version: "1.2.0",
    apiMin: "0.1.0",
    name: "Editor Grid Plane"
};

exports.create = function () {
    var state = {
        enabled: true,

        // size semantics:
        // - halfExtent: half size in world units (recommended)
        // - size: legacy alias for halfExtent
        halfExtent: 200,
        size: 200,

        step: 1.0,
        majorStep: 10,
        y: 0.02,
        opacity: 0.85,

        // dark, readable defaults
        minorColor: { r: 0.05, g: 0.06, b: 0.08 },
        majorColor: { r: 0.10, g: 0.12, b: 0.16 },

        // thickness in WORLD UNITS (stable "editor look")
        minorThickness: 0.02,
        majorThickness: 0.06
    };

    var created = false;
    var handle = null;
    var warned = false;

    // last applied snapshot to detect config change
    var lastKey = "";

    function norm01(v) { return clamp(Number(v) || 0, 0.0, 1.0); }

    function makeKey() {
        // cheap stringify key for "did config change?"
        // safeJson is not guaranteed global, so just build primitive key
        var he = Number(state.halfExtent || state.size || 200);
        return [
            state.enabled ? 1 : 0,
            he,
            Number(state.step),
            Number(state.majorStep),
            Number(state.y),
            Number(state.opacity),
            norm01(state.minorColor.r), norm01(state.minorColor.g), norm01(state.minorColor.b),
            norm01(state.majorColor.r), norm01(state.majorColor.g), norm01(state.majorColor.b),
            Number(state.minorThickness),
            Number(state.majorThickness)
        ].join("|");
    }

    function sanitizeState() {
        // accept legacy "size" as alias
        var he = Number(state.halfExtent || state.size || 200);
        he = clamp(he, 10, 500000);

        state.halfExtent = he;
        state.size = he;

        state.step = clamp(Number(state.step) || 1.0, 0.01, 100000);
        state.majorStep = clamp((Math.round(Number(state.majorStep) || 10) | 0), 1, 1000000);
        state.y = clamp(Number(state.y) || 0.02, -500000, 500000);
        state.opacity = clamp(Number(state.opacity) || 0.85, 0.0, 1.0);

        if (!state.minorColor) state.minorColor = { r: 0.05, g: 0.06, b: 0.08 };
        if (!state.majorColor) state.majorColor = { r: 0.10, g: 0.12, b: 0.16 };

        state.minorColor = {
            r: norm01(state.minorColor.r),
            g: norm01(state.minorColor.g),
            b: norm01(state.minorColor.b)
        };
        state.majorColor = {
            r: norm01(state.majorColor.r),
            g: norm01(state.majorColor.g),
            b: norm01(state.majorColor.b)
        };

        state.minorThickness = clamp(Number(state.minorThickness) || 0.02, 0.0005, 10.0);
        state.majorThickness = clamp(Number(state.majorThickness) || 0.06, 0.0005, 10.0);
    }

    function buildCfg() {
        return {
            halfExtent: state.halfExtent,
            step: state.step,
            majorStep: state.majorStep,
            y: state.y,
            opacity: state.opacity,

            minorColor: {
                r: state.minorColor.r,
                g: state.minorColor.g,
                b: state.minorColor.b
            },
            majorColor: {
                r: state.majorColor.r,
                g: state.majorColor.g,
                b: state.majorColor.b
            },

            minorThickness: state.minorThickness,
            majorThickness: state.majorThickness
        };
    }

    function tryCreate(log) {
        if (created) return true;

        var editorLines = null;
        try { editorLines = engine.editorLines(); } catch (_) {}

        try {
            handle = editorLines.createGridPlane(buildCfg());
            created = true;
            lastKey = makeKey();
            log.info("[editor.grid] created via engine.editorLines handle=" + String(handle));
            return true;
        } catch (e) {
            if (!warned) {
                warned = true;
                log.warn("[editor.grid] editorLines backend not available yet: " + String(e));
            }
            return false;
        }
    }

    function tryDestroy(log, reason) {
        if (!created) return;

        var editorLines = null;
        try { editorLines = engine.editorLines(); } catch (_) {}

        try { editorLines.destroy(handle); } catch (_) {}

        log.info("[editor.grid] destroyed reason=" + String(reason || "unknown"));
        created = false;
        handle = null;
    }

    function recreateIfChanged(log) {
        var k = makeKey();
        if (k === lastKey) return;
        if (!created) return;

        tryDestroy(log, "reconfigure");
        tryCreate(log);
    }

    return {
        init: function (ctx) {
            var log = engine.log();

            try {
                var cfg = (ctx && ctx.config) ? ctx.config : ctx;
                if (cfg) state = deepMerge(state, cfg);
            } catch (_) {}

            sanitizeState();

            if (state.enabled) tryCreate(log);
        },

        update: function (tpf) {
            if (!state.enabled) return;

            var log = engine.log();

            if (!created) {
                tryCreate(log);
                return;
            }

            // allow live-tuning via editor / hot reload / deserialize()
            recreateIfChanged(log);
        },

        destroy: function (reason) {
            tryDestroy(engine.log(), reason);
        },

        serialize: function () { return state; },

        deserialize: function (s) {
            if (!s) return;
            state = deepMerge(state, s);
            sanitizeState();

            // force recreate next tick if already created
            lastKey = "";
        }
    };
};