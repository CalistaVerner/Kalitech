// FILE: resources/kalitech/engine/modules/Profiler/Profiler.js
"use strict";

function ms(nanos) {
    return (Number(nanos) || 0) / 1e6;
}

function create(engine, K) {
    if (!engine) throw new Error("[PROFILER] engine is required");
    if (typeof engine.hud !== "function") throw new Error("[PROFILER] engine.hud() is required");

    const hud = engine.hud();
    const bus = (engine.bus && engine.bus()) || null;
    const cfg = K || (globalThis.__kalitech || Object.create(null));

    function overlay(ctx, opts = {}) {
        const layerName = String(opts.layer || "perf");
        const x = Number(opts.x ?? 12);
        const y = Number(opts.y ?? 12);
        const font = Number(opts.font ?? 14);

        const layer = hud.createLayer(layerName);
        const root = hud.addContainer(layer, x, y);
        const title = hud.addLabel(layer, root, "FrameProfiler", 0, 0);
        const frameLine = hud.addLabel(layer, root, "", 0, font + 4);
        const eventsLine = hud.addLabel(layer, root, "", 0, (font + 4) * 2);
        const workerLine = hud.addLabel(layer, root, "", 0, (font + 4) * 3);

        hud.setFontSize(title, font);
        hud.setFontSize(frameLine, font);
        hud.setFontSize(eventsLine, font);
        hud.setFontSize(workerLine, font);

        function update() {
            if (!ctx || !ctx.perf) return;
            const perf = ctx.perf();
            const frame = perf ? perf.frame() : null;
            if (frame) {
                const total = ms(frame.frameNanos);
                const world = ms(frame.worldUpdateNanos);
                const await = ms(frame.awaitWorkersNanos);
                hud.setText(frameLine, `frame=${total.toFixed(2)}ms world=${world.toFixed(2)}ms await=${await.toFixed(2)}ms`);
            }

            const evtStats = bus && typeof bus.stats === "function" ? bus.stats() : null;
            if (evtStats) {
                hud.setText(eventsLine, `events/sec=${evtStats.eventsPerSec.toFixed(1)} queued=${evtStats.queued}`);
            }

            const workers = perf ? perf.workers() : [];
            if (workers && workers.length) {
                const top = workers.slice(0, 3).map((w) =>
                    `${w.systemName}:${ms(w.lastTickNanos).toFixed(2)}ms`).join(" | ");
                hud.setText(workerLine, `systems: ${top}`);
            }
        }

        function destroy() {
            try { hud.remove(workerLine); } catch (_) {}
            try { hud.remove(eventsLine); } catch (_) {}
            try { hud.remove(frameLine); } catch (_) {}
            try { hud.remove(title); } catch (_) {}
            try { hud.remove(root); } catch (_) {}
            try { hud.destroyLayer(layer); } catch (_) {}
        }

        return Object.freeze({update, destroy});
    }

    return Object.freeze({
        overlay,
        config: cfg
    });
}

create.META = {
    moduleId: "profiler",
    id: "profiler",
    globalName: "PROFILER",
    version: "1.0.0",
    description: "FrameProfiler HUD overlay (frame time + events/sec + worker ticks).",
    engineMin: "0.2.0",
    changelog: [
        "1.0.0: initial profiler overlay helper."
    ],
    deprecation: {
        status: "active",
        policy: "Breaking changes require major bump."
    }
};

module.exports = create;
module.exports.META = create.META;
