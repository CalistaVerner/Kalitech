// FILE: Scripts/systems/sky/LightRig.js
"use strict";

const SkyMath = require("./SkyMath.js");

class LightRig {
    constructor() {
        this.light = null;
        this.sun = null;
        this.ambient = null;

        this.minAmbient = 0.20;

        this.ambientDay = { r: 0.25, g: 0.28, b: 0.35, intensity: 0.55 };
        this.ambientNight = { r: 0.10, g: 0.12, b: 0.18, intensity: 0.12 };

        this.dbg = null;
        this.debug = {
            enabled: false,
            origin: { x: 0, y: 5, z: 0 },
            sunLen: 40,
            axes: true,
            horizonCross: true,
            ttl: 0.05
        };

        this._dbgAcc = 0;
        this._dbgEvery = 0.0; // 0 = every frame (ttl-driven). Set to e.g. 0.1 to throttle.
    }

    applyCfg(cfg) {
        if (!cfg) return;

        if (cfg.minAmbient != null) {
            const v = +cfg.minAmbient;
            if (Number.isFinite(v)) this.minAmbient = Math.max(0.0, v);
        }

        if (cfg.ambientDay) {
            const a = cfg.ambientDay;
            if (a.r != null) this.ambientDay.r = +a.r;
            if (a.g != null) this.ambientDay.g = +a.g;
            if (a.b != null) this.ambientDay.b = +a.b;
            if (a.intensity != null) this.ambientDay.intensity = +a.intensity;
        }

        if (cfg.ambientNight) {
            const a = cfg.ambientNight;
            if (a.r != null) this.ambientNight.r = +a.r;
            if (a.g != null) this.ambientNight.g = +a.g;
            if (a.b != null) this.ambientNight.b = +a.b;
            if (a.intensity != null) this.ambientNight.intensity = +a.intensity;
        }

        if (cfg.debug) {
            const d = cfg.debug;
            if (d.enabled != null) this.debug.enabled = !!d.enabled;

            if (d.origin) {
                if (d.origin.x != null) this.debug.origin.x = +d.origin.x;
                if (d.origin.y != null) this.debug.origin.y = +d.origin.y;
                if (d.origin.z != null) this.debug.origin.z = +d.origin.z;
            }

            if (d.sunLen != null) {
                const v = +d.sunLen;
                if (Number.isFinite(v) && v > 0) this.debug.sunLen = v;
            }

            if (d.axes != null) this.debug.axes = !!d.axes;
            if (d.horizonCross != null) this.debug.horizonCross = !!d.horizonCross;

            if (d.ttl != null) {
                const v = +d.ttl;
                if (Number.isFinite(v) && v >= 0) this.debug.ttl = v;
            }

            if (d.every != null) {
                const v = +d.every;
                if (Number.isFinite(v) && v >= 0) this._dbgEvery = v;
            }
        }
    }

    init(engine) {
        this.light = engine.light();

        try { this.dbg = engine.debug ? engine.debug() : null; } catch (_) { this.dbg = null; }

        try {
            this.sun = this.light.create({
                type: "directional",
                enabled: true,
                attach: true,
                dir: [-1, -1, -0.3],
                color: [1.0, 0.98, 0.90],
                intensity: 1.2
            });
        } catch (e) {
            engine.log().error("[sky] failed to create sun light: " + e);
        }

        try {
            this.ambient = this.light.create({
                type: "ambient",
                enabled: true,
                attach: true,
                color: [this.ambientDay.r, this.ambientDay.g, this.ambientDay.b],
                intensity: Math.max(this.minAmbient, this.ambientDay.intensity)
            });
        } catch (e) {
            engine.log().error("[sky] failed to create ambient light: " + e);
        }
        this.test();
    }

    setEnabled(enabled) {
        try { if (this.light && this.sun) this.light.enable(this.sun, !!enabled); } catch (_) {}
        try { if (this.light && this.ambient) this.light.enable(this.ambient, !!enabled); } catch (_) {}
    }

    update(engine, sunEval, tpf) {
        if (this.sun) {
            try {
                this.light.set(this.sun, {
                    dir: [sunEval.rayDir.x, sunEval.rayDir.y, sunEval.rayDir.z],
                    color: [sunEval.color.r, sunEval.color.g, sunEval.color.b],
                    intensity: sunEval.intensity
                });
            } catch (e) {
                engine.log().warn("[sky] sun light set failed: " + e);
            }
        }

        const ambR = SkyMath.lerp(this.ambientNight.r, this.ambientDay.r, sunEval.dayFactor);
        const ambG = SkyMath.lerp(this.ambientNight.g, this.ambientDay.g, sunEval.dayFactor);
        const ambB = SkyMath.lerp(this.ambientNight.b, this.ambientDay.b, sunEval.dayFactor);
        const ambI = SkyMath.lerp(this.ambientNight.intensity, this.ambientDay.intensity, sunEval.dayFactor);

        if (this.ambient) {
            try {
                this.light.set(this.ambient, {
                    color: [ambR, ambG, ambB],
                    intensity: Math.max(this.minAmbient, ambI)
                });
            } catch (e) {
                engine.log().warn("[sky] ambient light set failed: " + e);
            }
        }

        this.drawDebug(engine, sunEval, tpf);
    }

    drawDebug(engine, sunEval, tpf) {
        if (!this.debug.enabled) return;
        if (!this.dbg) return;

        const dt = Number.isFinite(+tpf) ? +tpf : 0;
        this._dbgAcc += dt;

        if (this._dbgEvery > 0 && this._dbgAcc < this._dbgEvery) return;
        this._dbgAcc = 0;

        const ttl = this.debug.ttl;
        const ox = this.debug.origin.x, oy = this.debug.origin.y, oz = this.debug.origin.z;

        try {
            this.dbg.tick(dt);
        } catch (_) {}

        if (this.debug.axes) {
            this.dbg.axes({
                pos: [0, 0, 0],
                size: 2,
                ttl: ttl,
                depthTest: true,
                depthWrite: false
            });
        }

        const dir = sunEval && sunEval.rayDir ? sunEval.rayDir : { x: -1, y: -1, z: -0.3 };
        const len = this.debug.sunLen;

        const isDay = (sunEval && sunEval.dayFactor != null) ? (sunEval.dayFactor > 0.08) : true;

        const colSun = isDay
            ? [1.0, 0.95, 0.35, 1.0]
            : [0.25, 0.35, 1.0, 1.0];

        this.dbg.ray({
            origin: [ox, oy, oz],
            dir: [dir.x, dir.y, dir.z],
            len: len,
            color: colSun,
            ttl: ttl,
            arrow: true,
            depthTest: true,
            depthWrite: false
        });

        if (this.debug.horizonCross) {
            const cross = Math.max(4, len * 0.25);
            const y = oy;

            this.dbg.line({ a: [ox - cross, y, oz], b: [ox + cross, y, oz], color: [0.9, 0.9, 0.9, 1], ttl, depthTest: true, depthWrite: false });
            this.dbg.line({ a: [ox, y, oz - cross], b: [ox, y, oz + cross], color: [0.9, 0.9, 0.9, 1], ttl, depthTest: true, depthWrite: false });

            this.dbg.line({ a: [ox, 0, oz], b: [ox, y, oz], color: [0.3, 1.0, 0.3, 1], ttl, depthTest: true, depthWrite: false });
        }

        const ambI = Math.max(this.minAmbient, (sunEval && sunEval.ambientIntensity != null) ? sunEval.ambientIntensity : 0);
        const ambBar = Math.max(0.5, Math.min(6.0, ambI * 6.0));
        this.dbg.line({
            a: [ox + 1.5, oy, oz],
            b: [ox + 1.5, oy + ambBar, oz],
            color: [0.2, 0.8, 1.0, 1.0],
            ttl: ttl,
            depthTest: true,
            depthWrite: false
        });
    }

    destroy() {
        try { if (this.light && this.sun) this.light.destroy(this.sun); } catch (_) {}
        try { if (this.light && this.ambient) this.light.destroy(this.ambient); } catch (_) {}
        this.sun = null;
        this.ambient = null;
        this.dbg = null;
    }

    test() {
        const light = engine.light();

        const testLight = light.create({
            type: "point",
            attach: true,
            enabled: true,

            pos: [200, 8, -300],
            radius: 120,                 // ВАЖНО: без радиуса света может "не быть"
            color: [1.0, 1.0, 1.0],
            intensity: 6.0               // ВАЖНО: PBR требует сильный свет
        });

        engine.render().ensureScene(); // на всякий

        const torch = light.create({
            type: "point",
            attach: true,
            enabled: true,
            pos: [200, 8, -300],
            radius: 120,
            color: [1, 0.95, 0.8],
            intensity: 8.0
        });

        engine.log().info("[TEST] torch id=" + torch.id());
        //engine.render().debugViewports();
        for (let i = 0; i < 160; i++) {

            const g = MSH
                .box$()
                .size(this.randNum(1, 5))
                .name("box-" + i)
                .pos(120, 3, -300)
                .material(MAT.getMaterial("box"))
                .physics(10000, { lockRotation: false })
                .create();

    }


        engine.log().info("[TEST] cube created at (200, 8, -300)");


    }

     randNum(min, max) {
        min = +min;
        max = +max;
        if (!Number.isFinite(min) || !Number.isFinite(max)) {
            throw new Error("randNum(min, max): min/max must be numbers");
        }
        if (max < min) {
            const t = min; min = max; max = t;
        }
        return min + Math.random() * (max - min);
    }

}

module.exports = LightRig;