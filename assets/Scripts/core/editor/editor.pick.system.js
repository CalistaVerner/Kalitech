// FILE: Scripts/systems/editor.pick.system.js
// Author: Calista Verner
"use strict";

exports.meta = {
    id: "kalitech.system.editor.pick",
    version: "1.1.0",
    apiMin: "0.1.0",
    name: "Editor Pick Under Cursor"
};

exports.create = function () {
    var state = {
        enabled: true,
        mode: "click",
        button: "LMB",
        maxDistance: 10000,
        onlyClosest: true,
        flipY: true,
        debugLog: false
    };

    var last = {
        hit: false,
        geometry: null,
        distance: null,
        point: null,
        normal: null
    };

    var warnedInput = false;
    var warnedSurface = false;

    function inputPressed(button) {
        var input;
        try { input = engine.input(); } catch (_) { input = null; }
        if (!input) return false;

        try { return !!input.isMousePressed(button); } catch (_) {}
        try { return !!input.mousePressed(button); } catch (_) {}
        try {
            if (button === "LMB") return !!input.isLmbPressed();
            if (button === "RMB") return !!input.isRmbPressed();
        } catch (_) {}

        if (!warnedInput) {
            warnedInput = true;
            engine.log().warn("[editor.pick] InputApi missing mouse-press helpers (click mode may not work).");
        }
        return false;
    }

    function normalizeHit(hit) {
        if (!hit) return null;

        var geometry = (hit.geometry !== undefined) ? hit.geometry : null;
        var distance = (hit.distance !== undefined) ? hit.distance : null;

        var px = (hit.px !== undefined) ? hit.px : 0;
        var py = (hit.py !== undefined) ? hit.py : 0;
        var pz = (hit.pz !== undefined) ? hit.pz : 0;

        var nx = (hit.nx !== undefined) ? hit.nx : 0;
        var ny = (hit.ny !== undefined) ? hit.ny : 1;
        var nz = (hit.nz !== undefined) ? hit.nz : 0;

        return {
            geometry: geometry,
            distance: distance,
            point: { x: px, y: py, z: pz },
            normal: { x: nx, y: ny, z: nz },
            raw: hit
        };
    }

    function doPick() {
        var surface;
        try { surface = engine.surface(); } catch (_) { surface = null; }

        if (!surface) {
            if (!warnedSurface) {
                warnedSurface = true;
                engine.log().warn("[editor.pick] engine.surface() is not available.");
            }
            return { ok: false, reason: "no surface api" };
        }

        try {
            var hits = surface.pickUnderCursorCfg({
                max: state.maxDistance,
                onlyClosest: state.onlyClosest,
                limit: 16,
                flipY: state.flipY
            });
            return { ok: true, hits: hits };
        } catch (e) {
            if (!warnedSurface) {
                warnedSurface = true;
                engine.log().warn("[editor.pick] surface.pickUnderCursorCfg failed: " + e);
            }
            return { ok: false, reason: String(e) };
        }
    }

    function emitHit(hitNorm) {
        var bus = engine.events();
        try {
            bus.emit("editor.pick.hit", {
                hit: true,
                geometry: hitNorm.geometry,
                distance: hitNorm.distance,
                point: hitNorm.point,
                normal: hitNorm.normal,
                raw: hitNorm.raw
            });
        } catch (e) {
            engine.log().debug("[editor.pick] emit hit failed: " + e);
        }
    }

    function emitMiss() {
        var bus = engine.events();
        try { bus.emit("editor.pick.miss", { hit: false }); }
        catch (e) { engine.log().debug("[editor.pick] emit miss failed: " + e); }
    }

    function updateLast(hits) {
        try {
            var h0 = (hits && hits.length > 0) ? hits[0] : null;
            if (!h0) {
                last.hit = false;
                last.geometry = null;
                last.distance = null;
                last.point = null;
                last.normal = null;
                return null;
            }

            var n = normalizeHit(h0);
            if (!n) return null;

            last.hit = true;
            last.geometry = n.geometry;
            last.distance = n.distance;
            last.point = n.point;
            last.normal = n.normal;
            return n;
        } catch (_) {
            last.hit = false;
            return null;
        }
    }

    return {
        init: function (ctx) {
            var log = engine.log();

            // schema (builtin) можно применить жёстко, но без знания API — мягко мержим:
            try {
                var cfg = (ctx && ctx.config) ? ctx.config : ctx;
                if (cfg && typeof cfg === "object") state = deepMerge(state, cfg);
            } catch (_) {}

            // clamp — builtin
            state.maxDistance = clamp(state.maxDistance, 1, 10000000);

            log.info("[editor.pick] init mode=" + state.mode + " max=" + state.maxDistance);
        },

        update: function (tpf) {
            if (!state.enabled) return;

            var shouldPick = false;
            if (state.mode === "hover") shouldPick = true;
            else if (state.mode === "click") shouldPick = inputPressed(state.button);

            if (!shouldPick) return;

            var out = doPick();
            if (!out.ok) return;

            var hitNorm = updateLast(out.hits);
            if (!hitNorm) {
                if (state.debugLog) engine.log().info("[editor.pick] miss");
                emitMiss();
                return;
            }

            if (state.debugLog) engine.log().info("[editor.pick] hit " + safeJson({ g: hitNorm.geometry, d: hitNorm.distance, p: hitNorm.point }));
            emitHit(hitNorm);
        },

        destroy: function (reason) {},

        serialize: function () { return { state: state, last: last }; },

        deserialize: function (s) {
            if (s && typeof s === "object") {
                if (s.state) state = deepMerge(state, s.state);
                if (s.last)  last  = deepMerge(last, s.last);
            }
        }
    };
};