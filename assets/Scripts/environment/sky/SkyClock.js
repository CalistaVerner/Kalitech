"use strict";

const SkyMath = require("./SkyMath.js");

class SkyClock {
    constructor() {
        this.enabled = true;

        // CDPR-like default: 20 minutes for a full cycle (day+night)
        this.dayLengthSec = 1200.0;

        this.t = 0.0;

        this._startApplied = false;

        // debug
        this._dbgPhase = -1; // 0..7
    }

    applyCfg(cfg) {
        if (!cfg) return;

        if (cfg.enabled === true) this.enabled = true;
        if (cfg.enabled === false) this.enabled = false;

        const dls = +cfg.dayLengthSec;
        if (Number.isFinite(dls) && dls > 1) this.dayLengthSec = dls;

        if (!this._startApplied && cfg.startTime01 != null) {
            const t01 = SkyMath.wrap(+cfg.startTime01, 0, 1);
            this.t = this.dayLengthSec * t01;
            this._startApplied = true;
        }

        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug(
                "[sky][clock] applyCfg enabled=" + this.enabled +
                " dayLengthSec=" + this.dayLengthSec +
                " startApplied=" + this._startApplied
            );
        }
    }

    step(dt) {
        if (!this.enabled) return;

        const d = Number.isFinite(+dt) ? +dt : 0.0;
        this.t += d;

        if (this.dayLengthSec > 0) {
            while (this.t > this.dayLengthSec) this.t -= this.dayLengthSec;
            while (this.t < 0) this.t += this.dayLengthSec;
        }

        // phase debug (8 steps) — cheap and useful
        const phase = Math.floor(this.time01 * 8);
        if (phase !== this._dbgPhase) {
            this._dbgPhase = phase;
            if (ENGINE && ENGINE.log && ENGINE.log.debug) {
                ENGINE.log.debug("[sky][clock] phase=" + phase + " time01=" + this.time01.toFixed(4));
            }
        }
    }

    setTime01(time01) {
        const t01 = SkyMath.wrap(+time01, 0, 1);
        this.t = this.dayLengthSec * t01;

        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug("[sky][clock] setTime01=" + t01.toFixed(4) + " tSec=" + this.t.toFixed(3));
        }
    }

    setTimeSec(timeSec) {
        const ts = +timeSec;
        if (!Number.isFinite(ts)) return;

        this.t = ts;

        if (this.dayLengthSec > 0) {
            while (this.t > this.dayLengthSec) this.t -= this.dayLengthSec;
            while (this.t < 0) this.t += this.dayLengthSec;
        }

        if (ENGINE && ENGINE.log && ENGINE.log.debug) {
            ENGINE.log.debug("[sky][clock] setTimeSec=" + ts.toFixed(3) + " time01=" + this.time01.toFixed(4));
        }
    }

    get time01() {
        return this.dayLengthSec > 0 ? (this.t / this.dayLengthSec) : 0;
    }
}

module.exports = SkyClock;