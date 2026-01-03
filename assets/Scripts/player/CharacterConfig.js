// FILE: Scripts/player/CharacterConfig.js
// Author: Calista Verner
"use strict";

const U = require("./util.js");

function clamp01(v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

// Minimal nested config readers (no try/catch, no magic)
function readPath(obj, path) {
    let o = obj;
    for (let i = 0; i < path.length; i++) {
        if (!o || typeof o !== "object") return undefined;
        o = o[path[i]];
    }
    return o;
}
function readNum(obj, path, fb) {
    const v = readPath(obj, path);
    return (v === undefined || v === null) ? fb : U.num(v, fb);
}
function readBool(obj, path, fb) {
    const v = readPath(obj, path);
    return (v === undefined || v === null) ? fb : !!v;
}

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
            slideAccel: 18.0,
            maxSlideSpeed: 9.0,
            minSlideNormalY: 0.05
        };
    }

    loadFrom(playerCfg, movementCfg) {
        const ch = (playerCfg && playerCfg.character) ? playerCfg.character : null;
        const sp = (playerCfg && playerCfg.spawn) ? playerCfg.spawn : null;

        // Prefer character.* then spawn.* then defaults.
        const h = (ch && ch.height != null) ? U.num(ch.height, 1.80)
            : (sp && sp.height != null) ? U.num(sp.height, 1.80)
                : 1.80;

        const r = (ch && ch.radius != null) ? U.num(ch.radius, 0.35)
            : (sp && sp.radius != null) ? U.num(sp.radius, 0.35)
                : 0.35;

        const ehDefault = Math.min(h * 0.92, h - 0.08);
        const ehRaw = (ch && ch.eyeHeight != null) ? U.num(ch.eyeHeight, ehDefault) : ehDefault;

        this.height = Math.max(0.8, h);
        this.radius = U.clamp(r, 0.10, 1.20);
        this.eyeHeight = U.clamp(ehRaw, 1.20, this.height - 0.05);

        const mc = movementCfg || Object.create(null);

        // ground
        this.groundRay = readNum(mc, ["ground", "rayLength"], this.groundRay);
        this.groundEps = readNum(mc, ["ground", "eps"], this.groundEps);
        this.maxSlopeDot = readNum(mc, ["ground", "maxSlopeDot"], this.maxSlopeDot);
        this.probeRing = U.clamp(readNum(mc, ["ground", "probeRing"], this.probeRing), 0.1, 1.2);

        // stepUp
        this.stepUp.enabled = readBool(mc, ["stepUp", "enabled"], this.stepUp.enabled);
        this.stepUp.maxHeight = readNum(mc, ["stepUp", "maxHeight"], this.stepUp.maxHeight);
        this.stepUp.forwardProbe = readNum(mc, ["stepUp", "forwardProbe"], this.stepUp.forwardProbe);
        this.stepUp.upProbe = readNum(mc, ["stepUp", "upProbe"], this.stepUp.upProbe);
        this.stepUp.snapUpSpeed = readNum(mc, ["stepUp", "snapUpSpeed"], this.stepUp.snapUpSpeed);
        this.stepUp.minClearNormalY = readNum(mc, ["stepUp", "minClearNormalY"], this.stepUp.minClearNormalY);
        this.stepUp.warpCooldown = U.clamp(readNum(mc, ["stepUp", "warpCooldown"], this.stepUp.warpCooldown), 0, 1);

        // stepDown
        this.stepDown.enabled = readBool(mc, ["stepDown", "enabled"], this.stepDown.enabled);
        this.stepDown.max = readNum(mc, ["stepDown", "max"], this.stepDown.max);
        this.stepDown.speed = readNum(mc, ["stepDown", "speed"], this.stepDown.speed);

        // slope
        this.slope.slideEnabled = readBool(mc, ["slope", "slideEnabled"], this.slope.slideEnabled);
        this.slope.slideAccel = readNum(mc, ["slope", "slideAccel"], this.slope.slideAccel);
        this.slope.maxSlideSpeed = readNum(mc, ["slope", "maxSlideSpeed"], this.slope.maxSlideSpeed);
        this.slope.minSlideNormalY = readNum(mc, ["slope", "minSlideNormalY"], this.slope.minSlideNormalY);

        // keep stepDown.max defaulting to stepUp.maxHeight if not set
        if (!(this.stepDown.max > 0)) this.stepDown.max = this.stepUp.maxHeight;

        // normalize “maxSlopeDot” to [0..1]
        this.maxSlopeDot = clamp01(this.maxSlopeDot);

        return this;
    }
}

module.exports = CharacterConfig;