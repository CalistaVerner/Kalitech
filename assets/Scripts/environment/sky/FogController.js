"use strict";

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function clamp(x, a, b) {
    x = +x;
    if (!Number.isFinite(x)) return a;
    return x < a ? a : (x > b ? b : x);
}

function lerp(a, b, t) {
    return a + (b - a) * t;
}

class FogController {
    constructor() {
        // Base look
        this.day = {r: 0.70, g: 0.78, b: 0.90, density: 0.006, distance: 260.0};
        this.night = {r: 0.06, g: 0.08, b: 0.12, density: 0.010, distance: 140.0};

        this._cfgRef = null;
        this._render = null;

        this._lastKey = "";
    }

    init(render) {
        this._render = req(render, "[sky][fog] render is required");
    }

    destroy() {
    }

    applyCfg(cfg) {
        if (cfg === this._cfgRef) return;
        this._cfgRef = cfg;

        if (cfg.fogDay) {
            const f = cfg.fogDay;
            if (f.r != null) this.day.r = +f.r;
            if (f.g != null) this.day.g = +f.g;
            if (f.b != null) this.day.b = +f.b;
            if (f.density != null) this.day.density = +f.density;
            if (f.distance != null) this.day.distance = +f.distance;
        }
        if (cfg.fogNight) {
            const f = cfg.fogNight;
            if (f.r != null) this.night.r = +f.r;
            if (f.g != null) this.night.g = +f.g;
            if (f.b != null) this.night.b = +f.b;
            if (f.density != null) this.night.density = +f.density;
            if (f.distance != null) this.night.distance = +f.distance;
        }
    }

    update(render, cel) {
        render = req(render, "[sky][fog] render is required");
        req(cel && typeof cel.dayFactor === "number", "[sky][fog] cel.dayFactor is required");

        const df = clamp(cel.dayFactor, 0, 1);

        const r = lerp(this.night.r, this.day.r, df);
        const g = lerp(this.night.g, this.day.g, df);
        const b = lerp(this.night.b, this.day.b, df);

        const density = clamp(lerp(this.night.density, this.day.density, df), 0.0, 0.03);
        const distance = Math.max(25.0, lerp(this.night.distance, this.day.distance, df));

        const key = [
            r.toFixed(4), g.toFixed(4), b.toFixed(4),
            density.toFixed(6), distance.toFixed(2)
        ].join("|");

        if (key === this._lastKey) return;
        this._lastKey = key;

        render.fogCfg({
            color: {r, g, b},
            density,
            distance
        });
    }
}

module.exports = FogController;