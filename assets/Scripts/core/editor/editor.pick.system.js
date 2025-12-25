// FILE: Scripts/systems/editor.pick.system.js
// Author: Calista Verner
//
// Editor pick-under-cursor system.
// Uses engine.surface().pickUnderCursorCfg(...) (world/root overload).
// Emits events:
//   - "editor.pick.hit"  payload: { hit:true, geometry, distance, point:{x,y,z}, normal:{x,y,z}, raw }
//   - "editor.pick.miss" payload: { hit:false }

exports.meta = {
    id: "kalitech.system.editor.pick",
    version: "1.0.0",
    apiMin: "0.1.0",
    name: "Editor Pick Under Cursor"
};

function safeJson(v) { try { return JSON.stringify(v); } catch (_) { return String(v); } }

exports.create = function () {
    let state = {
        enabled: true,
        mode: "click",      // "click" | "hover"
        button: "LMB",      // for click-mode
        maxDistance: 10000, // maps to SurfaceApi cfg.max
        onlyClosest: true,
        flipY: true,
        debugLog: false
    };

    let last = {
        hit: false,
        geometry: null,
        distance: null,
        point: null,
        normal: null
    };

    let warnedInput = false;
    let warnedSurface = false;

    function inputPressed(button) {
        // No typeof checks; just attempt a few known calls.
        const input = engine.input?.();
        if (!input) return false;

        try {
            // Preferred generic API
            return !!input.isMousePressed(button);
        } catch (_) {}

        try {
            // Alternate naming
            return !!input.mousePressed(button);
        } catch (_) {}

        try {
            // Convenience APIs
            if (button === "LMB") return !!input.isLmbPressed();
            if (button === "RMB") return !!input.isRmbPressed();
        } catch (_) {}

        // If none worked, warn once (in click mode only, else spammy)
        if (!warnedInput) {
            warnedInput = true;
            engine.log().warn("[editor.pick] InputApi does not expose isMousePressed/mousePressed/isLmbPressed/isRmbPressed (click mode may not work).");
        }
        return false;
    }

    function normalizeHit(hit) {
        // SurfaceApi.Hit DTO (Java) fields:
        // geometry, distance, px,py,pz, nx,ny,nz
        if (!hit) return null;

        // Defensive reads without typeof function; rely on field access
        const geometry = hit.geometry ?? null;
        const distance = hit.distance ?? null;

        const point = { x: hit.px ?? 0, y: hit.py ?? 0, z: hit.pz ?? 0 };
        const normal = { x: hit.nx ?? 0, y: hit.ny ?? 1, z: hit.nz ?? 0 };

        return { geometry, distance, point, normal, raw: hit };
    }

    function doPick() {
        const surface = engine.surface?.();
        if (!surface) {
            if (!warnedSurface) {
                warnedSurface = true;
                engine.log().warn("[editor.pick] engine.surface() is not available.");
            }
            return { ok: false, reason: "no surface api" };
        }

        // Call your Java overload: pickUnderCursorCfg(Value cfg) returning Hit[]
        try {
            const hits = surface.pickUnderCursorCfg({
                max: state.maxDistance,
                onlyClosest: state.onlyClosest,
                limit: 16,
                flipY: state.flipY
            });

            // Expect Hit[] (array-like). If something else, still return it.
            return { ok: true, hits };
        } catch (e) {
            if (!warnedSurface) {
                warnedSurface = true;
                engine.log().warn("[editor.pick] surface.pickUnderCursorCfg not available yet or failed: " + e);
            }
            return { ok: false, reason: String(e) };
        }
    }

    function emitPickHit(hitNorm) {
        const events = engine.events?.();
        if (!events) return;

        try {
            events.emit("editor.pick.hit", {
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

    function emitPickMiss() {
        const events = engine.events?.();
        if (!events) return;

        try {
            events.emit("editor.pick.miss", { hit: false });
        } catch (e) {
            engine.log().debug("[editor.pick] emit miss failed: " + e);
        }
    }

    function updateLastFromHits(hits) {
        try {
            const h0 = (hits && hits.length > 0) ? hits[0] : null;
            if (!h0) {
                last.hit = false;
                last.geometry = null;
                last.distance = null;
                last.point = null;
                last.normal = null;
                return null;
            }

            const n = normalizeHit(h0);
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
        init(ctx) {
            const log = engine.log();

            // Read cfg (no typeof checks; safe in try)
            try {
                const cfg = ctx?.config || ctx || null;
                if (cfg && typeof cfg === "object") {
                    if (cfg.enabled != null) state.enabled = !!cfg.enabled;
                    if (cfg.mode === "click" || cfg.mode === "hover") state.mode = cfg.mode;
                    if (cfg.button != null) state.button = String(cfg.button);

                    // Support both names
                    if (cfg.maxDistance != null) state.maxDistance = +cfg.maxDistance;
                    if (cfg.max != null) state.maxDistance = +cfg.max;

                    if (cfg.onlyClosest != null) state.onlyClosest = !!cfg.onlyClosest;
                    if (cfg.flipY != null) state.flipY = !!cfg.flipY;
                    if (cfg.debugLog != null) state.debugLog = !!cfg.debugLog;
                }
            } catch (_) {}

            log.info("[editor.pick] init mode=" + state.mode + " max=" + state.maxDistance + " onlyClosest=" + state.onlyClosest + " flipY=" + state.flipY);

            // Capability probe once (without typeof checks)
            try {
                // This will warn once in doPick() if missing; keep init silent.
                const _ = engine.surface?.();
            } catch (_) {}
        },

        update(tpf) {
            if (!state.enabled) return;

            let shouldPick = (state.mode === "hover");
            if (!shouldPick && state.mode === "click") {
                shouldPick = inputPressed(state.button);
            }
            if (!shouldPick) return;

            const out = doPick();
            if (!out.ok) {
                if (state.debugLog) engine.log().debug("[editor.pick] pick failed: " + out.reason);
                return;
            }

            const hitNorm = updateLastFromHits(out.hits);
            if (!hitNorm) {
                if (state.debugLog) engine.log().info("[editor.pick] miss");
                emitPickMiss();
                return;
            }

            if (state.debugLog) {
                engine.log().info("[editor.pick] hit " + safeJson({ geometry: hitNorm.geometry, distance: hitNorm.distance, point: hitNorm.point }));
            }
            emitPickHit(hitNorm);
        },

        destroy(reason) {
            // nothing to destroy; keep lifecycle contract
        },

        serialize() {
            return { state, last };
        },

        deserialize(s) {
            try {
                if (s && typeof s === "object") {
                    if (s.state && typeof s.state === "object") state = { ...state, ...s.state };
                    if (s.last && typeof s.last === "object") last = { ...last, ...s.last };
                }
            } catch (_) {}
        }
    };
};