// FILE: Scripts/player/CharacterConfig.js
// Author: Calista Verner
"use strict";

const U = require("./util.js");

function clamp01(v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

class CharacterConfig {
    constructor() {
        this.radius = 0.35;
        this.height = 1.80;
        this.eyeHeight = 1.65;

        // ground probing
        this.groundRay = 1.15;
        this.groundEps = 0.08;
        this.maxSlopeDot = 0.55;      // walkable if normalY >= maxSlopeDot
        this.probeRing = 0.65;        // *radius (offset of side probes)

        // step up/down
        this.stepUp = {
            enabled: false,
            maxHeight: 0.45,
            forwardProbe: 0.35,
            upProbe: 0.55,
            snapUpSpeed: 24.0,
            minClearNormalY: 0.35,
            warpCooldown: 0.07
        };

        this.stepDown = {
            enabled: true,
            max: 0.45,
            speed: 22.0
        };

        // slope slide
        this.slope = {
            slideEnabled: true,
            slideAccel: 18.0,     // m/s^2-ish (tuned)
            maxSlideSpeed: 9.0,   // clamp on xz
            minSlideNormalY: 0.05 // ignore near-vertical nonsense
        };
    }

    loadFrom(playerCfg, movementCfg) {
        const ch = (playerCfg && playerCfg.character) ? playerCfg.character : null;
        const sp = (playerCfg && playerCfg.spawn) ? playerCfg.spawn : null;

        const h = (ch && ch.height != null) ? U.num(ch.height, 1.80) : (sp && sp.height != null ? U.num(sp.height, 1.80) : 1.80);
        const r = (ch && ch.radius != null) ? U.num(ch.radius, 0.35) : (sp && sp.radius != null ? U.num(sp.radius, 0.35) : 0.35);
        const ehRaw = (ch && ch.eyeHeight != null)
            ? U.num(ch.eyeHeight, Math.min(h * 0.92, h - 0.08))
            : Math.min(h * 0.92, h - 0.08);

        this.height = Math.max(0.8, h);
        this.radius = U.clamp(r, 0.10, 1.20);
        this.eyeHeight = U.clamp(ehRaw, 1.20, this.height - 0.05);

        const mc = movementCfg || Object.create(null);

        this.groundRay = U.readNum(mc, ["ground", "rayLength"], this.groundRay);
        this.groundEps = U.readNum(mc, ["ground", "eps"], this.groundEps);
        this.maxSlopeDot = U.readNum(mc, ["ground", "maxSlopeDot"], this.maxSlopeDot);
        this.probeRing = U.clamp(U.readNum(mc, ["ground", "probeRing"], this.probeRing), 0.1, 1.2);

        // step up/down unified
        this.stepUp.enabled = U.readBool(mc, ["stepUp", "enabled"], this.stepUp.enabled);
        this.stepUp.maxHeight = U.readNum(mc, ["stepUp", "maxHeight"], this.stepUp.maxHeight);
        this.stepUp.forwardProbe = U.readNum(mc, ["stepUp", "forwardProbe"], this.stepUp.forwardProbe);
        this.stepUp.upProbe = U.readNum(mc, ["stepUp", "upProbe"], this.stepUp.upProbe);
        this.stepUp.snapUpSpeed = U.readNum(mc, ["stepUp", "snapUpSpeed"], this.stepUp.snapUpSpeed);
        this.stepUp.minClearNormalY = U.readNum(mc, ["stepUp", "minClearNormalY"], this.stepUp.minClearNormalY);
        this.stepUp.warpCooldown = U.clamp(U.readNum(mc, ["stepUp", "warpCooldown"], this.stepUp.warpCooldown), 0, 1);

        this.stepDown.enabled = U.readBool(mc, ["stepDown", "enabled"], this.stepDown.enabled);
        this.stepDown.max = U.readNum(mc, ["stepDown", "max"], this.stepDown.max);
        this.stepDown.speed = U.readNum(mc, ["stepDown", "speed"], this.stepDown.speed);

        // slope
        this.slope.slideEnabled = U.readBool(mc, ["slope", "slideEnabled"], this.slope.slideEnabled);
        this.slope.slideAccel = U.readNum(mc, ["slope", "slideAccel"], this.slope.slideAccel);
        this.slope.maxSlideSpeed = U.readNum(mc, ["slope", "maxSlideSpeed"], this.slope.maxSlideSpeed);
        this.slope.minSlideNormalY = U.readNum(mc, ["slope", "minSlideNormalY"], this.slope.minSlideNormalY);

        // keep stepDown.max defaulting to stepUp.maxHeight if not set
        if (!(this.stepDown.max > 0)) this.stepDown.max = this.stepUp.maxHeight;

        // normalize “maxSlopeDot” to [0..1]
        this.maxSlopeDot = clamp01(this.maxSlopeDot);

        return this;
    }
}

module.exports = CharacterConfig;
