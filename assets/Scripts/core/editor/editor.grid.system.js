// FILE: Scripts/systems/editor.grid.system.js
// Author: Calista Verner
//
// Editor grid-plane overlay system.
// Tries multiple backends (editorApi/renderApi/entityApi) and degrades gracefully.

exports.meta = {
    id: "kalitech.system.editor.grid",
    version: "1.0.0",
    apiMin: "0.1.0",
    name: "Editor Grid Plane"
};

function clamp(v, a, b) { v = +v; return v < a ? a : (v > b ? b : v); }

exports.create = function () {
    let state = {
        enabled: true,
        // Visual params (units are engine units; tweak later)
        size: 200,      // half-extent or total depending on backend
        step: 1.0,      // grid spacing
        majorStep: 10,  // every N lines thicker/brighter if backend supports
        y: 0.01,        // slightly above ground to avoid z-fighting
        opacity: 0.35
    };

    let created = false;
    let handle = null; // could be entityId or render handle depending on backend

    function tryCreateGrid(log) {
        if (created) return true;

        const editor = engine.editor?.();
        const render = engine.render?.();
        const entity = engine.entity?.();

        // 1) Preferred: editor API (if you expose it later)
        try {
            if (editor && typeof editor.createGridPlane === "function") {
                handle = editor.createGridPlane({
                    size: state.size,
                    step: state.step,
                    majorStep: state.majorStep,
                    y: state.y,
                    opacity: state.opacity
                });
                created = true;
                log.info("[editor.grid] created via editor.createGridPlane handle=" + String(handle));
                return true;
            }
        } catch (e) {
            log.debug("[editor.grid] editor.createGridPlane failed: " + e);
        }

        // 2) Fallback: render API (if present)
        try {
            if (render && typeof render.createGridPlane === "function") {
                handle = render.createGridPlane({
                    size: state.size,
                    step: state.step,
                    majorStep: state.majorStep,
                    y: state.y,
                    opacity: state.opacity
                });
                created = true;
                log.info("[editor.grid] created via render.createGridPlane handle=" + String(handle));
                return true;
            }
        } catch (e) {
            log.debug("[editor.grid] render.createGridPlane failed: " + e);
        }

        // 3) Fallback: entity-based debug geometry (if you later add helpers)
        try {
            if (entity && typeof entity.create === "function" && render && typeof render.attach === "function") {
                // This is a placeholder path: it will work once you have entity+render attach helpers.
                const eid = entity.create({ name: "editor.grid", tags: ["editor", "grid"] });
                // If you add render.debugGridMesh(...) later, hook it here.
                // render.attach(eid, render.debugGridMesh({...}))
                handle = eid;
                created = true;
                log.info("[editor.grid] created placeholder entityId=" + String(handle) + " (attach backend not implemented yet)");
                return true;
            }
        } catch (e) {
            log.debug("[editor.grid] entity fallback failed: " + e);
        }

        // No backend available yet
        log.warn("[editor.grid] no grid backend available (expected editor.createGridPlane or render.createGridPlane).");
        return false;
    }

    function tryDestroyGrid(log, reason) {
        if (!created) return;

        const editor = engine.editor?.();
        const render = engine.render?.();
        const entity = engine.entity?.();

        // Try best-effort destroy on whichever backend created it
        try {
            if (editor && typeof editor.destroyGridPlane === "function" && handle != null) {
                editor.destroyGridPlane(handle);
            } else if (render && typeof render.destroyGridPlane === "function" && handle != null) {
                render.destroyGridPlane(handle);
            } else if (entity && typeof entity.destroy === "function" && typeof handle === "number") {
                entity.destroy(handle);
            }
        } catch (e) {
            log.debug("[editor.grid] destroy failed: " + e);
        }

        log.info("[editor.grid] destroyed reason=" + String(reason || "unknown"));
        created = false;
        handle = null;
    }

    return {
        init(ctx) {
            const log = engine.log();
            // sanitize config if passed via ctx/config (optional)
            try {
                const cfg = ctx?.config || ctx || null;
                if (cfg && typeof cfg === "object") {
                    if (cfg.size != null) state.size = clamp(cfg.size, 10, 5000);
                    if (cfg.step != null) state.step = clamp(cfg.step, 0.1, 100);
                    if (cfg.majorStep != null) state.majorStep = clamp(cfg.majorStep, 1, 1000);
                    if (cfg.y != null) state.y = clamp(cfg.y, -1000, 1000);
                    if (cfg.opacity != null) state.opacity = clamp(cfg.opacity, 0.0, 1.0);
                    if (cfg.enabled != null) state.enabled = !!cfg.enabled;
                }
            } catch (_) {}

            if (state.enabled) tryCreateGrid(log);
        },

        update(tpf) {
            if (!state.enabled) return;
            if (!created) {
                // retry occasionally (in case backend appears after hot reload)
                // cheap: once every ~0.5s
                const t = engine.time?.()?.timeSec?.() ?? 0;
                if (t === 0 || (t % 0.5) < 0.016) {
                    tryCreateGrid(engine.log());
                }
            }
        },

        destroy(reason) {
            tryDestroyGrid(engine.log(), reason);
        },

        serialize() {
            return state;
        },

        deserialize(s) {
            if (s && typeof s === "object") {
                state = { ...state, ...s };
            }
        }
    };
};