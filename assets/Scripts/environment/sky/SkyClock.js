// FILE: Scripts/systems/sky/SkyClock.js
"use strict";

const SkyMath = require("./SkyMath.js");

class SkyClock {
    constructor() {
        this.enabled = true;
        this.dayLengthSec = 30.0;
        this.t = 0.0;
    }

    applyCfg(cfg) {
        if (!cfg) return;

        if (cfg.enabled === true) this.enabled = true;
        if (cfg.enabled === false) this.enabled = false;

        const dls = +cfg.dayLengthSec;
        if (Number.isFinite(dls) && dls > 1) this.dayLengthSec = dls;
    }

    step(dt) {
        if (!this.enabled) return;
        this.t += dt;
        if (this.t > this.dayLengthSec) this.t -= this.dayLengthSec;
    }

    setTime01(time01) {
        const t01 = SkyMath.wrap(+time01, 0, 1);
        this.t = this.dayLengthSec * t01;
    }

    setTimeSec(timeSec) {
        const ts = +timeSec;
        if (!Number.isFinite(ts)) return;
        this.t = ts;
        if (this.dayLengthSec > 0) {
            while (this.t > this.dayLengthSec) this.t -= this.dayLengthSec;
            while (this.t < 0) this.t += this.dayLengthSec;
        }
    }

    get time01() {
        return this.dayLengthSec > 0 ? (this.t / this.dayLengthSec) : 0;
    }
}

module.exports = SkyClock;