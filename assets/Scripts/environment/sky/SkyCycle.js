// FILE: Scripts/systems/sky/SkyCycle.js
"use strict";

const SkyMath = require("./SkyMath.js");

class SkyCycle {
    constructor() {
        this.time01 = 0.25;

        this.dayLengthSec = 1200.0;
        this.timeScale = 1.0;
        this.paused = false;

        this._accum = 0.0;
    }

    applyCfg(cfg) {
        if (!cfg) return;

        if (cfg.time01 != null) {
            const t = +cfg.time01;
            if (Number.isFinite(t)) this.time01 = SkyMath.wrap(t, 0, 1);
        }

        if (cfg.dayLengthSec != null) {
            const d = +cfg.dayLengthSec;
            if (Number.isFinite(d)) this.dayLengthSec = Math.max(1.0, d);
        }

        if (cfg.timeScale != null) {
            const s = +cfg.timeScale;
            if (Number.isFinite(s)) this.timeScale = s;
        }

        if (cfg.paused != null) this.paused = !!cfg.paused;
    }

    tick(tpf) {
        const dt = Math.max(0.0, +tpf || 0.0);
        if (this.paused) return this.time01;

        const len = Math.max(1.0, this.dayLengthSec);
        const speed = this.timeScale;

        this._accum += dt * speed;
        const add01 = this._accum / len;

        if (add01 !== 0.0) {
            this.time01 = SkyMath.wrap(this.time01 + add01, 0, 1);
            this._accum = 0.0;
        }

        return this.time01;
    }
}

module.exports = SkyCycle;