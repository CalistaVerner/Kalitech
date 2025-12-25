// FILE: Scripts/systems/sky.js
// Author: Calista Verner
//
// Sky + Sun + Shadows + Skybox + Fog controller
// jsSystem: { module:"Scripts/systems/sky.js", ...config }
//
// NOTE:
// - builtins are loaded before scripts
// - no typeof/function checks
// - use: Number.isFinite / boolean strict checks / try-catch for optional fields

"use strict";

let _t = 0.0;
let _enabled = true;

// defaults (overridable via config)
let _dayLengthSec = 30.0;
let _azimuthDeg = 35.0;

let _nightIntensity = 0.02;
let _dayIntensity = 1.35;

let _sunsetWarmth = 0.35;

let _fogBase = { r: 0.70, g: 0.78, b: 0.90 };
let _fogDistance = 250.0;
let _fogDensityDay = 1.10;
let _fogDensityNight = 1.35;

let _ambientDay = { r: 0.25, g: 0.28, b: 0.35, intensity: 0.55 };
let _ambientNight = { r: 0.10, g: 0.12, b: 0.18, intensity: 0.12 };

let _skyboxAsset = "Textures/Sky/skyBox.dds";
let _shadowsCfg = { mapSize: 2048, splits: 3, lambda: 0.65 };

// internal
let _wiredEvents = false;
let _staticApplied = false;

// fog throttling cache
let _lastFogDensity = NaN;
let _lastFogColorKey = "";
let _lastFogDistance = NaN;

// optional debug heartbeat
let _dbgAcc = 0;

// ---------- helpers ----------
function fogKeyRGB(r, g, b) {
    return (
        Number(r).toFixed(4) + "|" +
        Number(g).toFixed(4) + "|" +
        Number(b).toFixed(4)
    );
}

function dirFromAltAz(alt, az) {
    const ca = Math.cos(alt);
    const x = Math.cos(az) * ca;
    const y = Math.sin(alt);
    const z = Math.sin(az) * ca;
    const len = Math.sqrt(x * x + y * y + z * z) || 1.0;
    return { x: x / len, y: y / len, z: z / len };
}

// config reader (no typeof; assume ctx shapes are stable)
function readCfg(ctx) {
    if (!ctx) return null;
    if (ctx.config) return ctx.config;
    if (ctx.cfg) return ctx.cfg;
    if (ctx.system && ctx.system.config) return ctx.system.config;
    return null;
}

// robust tpf getter (no typeof/function checks)
function getTpf(ctx, maybeTpf) {
    // 1) explicit param
    const p = +maybeTpf;
    if (Number.isFinite(p) && p > 0) return p;

    // 2) ctx.tpf
    try {
        const c = +ctx.tpf;
        if (Number.isFinite(c) && c > 0) return c;
    } catch (_) {}

    // 3) ctx.time.tpf (value)
    try {
        const t = +ctx.time.tpf;
        if (Number.isFinite(t) && t > 0) return t;
    } catch (_) {}

    // 4) engine.time().tpf() (call without checking)
    try {
        const v = +engine.time().tpf();
        if (Number.isFinite(v) && v > 0) return v;
    } catch (_) {}

    return 1.0 / 60.0;
}

function applyConfig(cfg) {
    if (!cfg) return;

    // booleans: only accept strict true/false (no typeof)
    if (cfg.enabled === true) _enabled = true;
    if (cfg.enabled === false) _enabled = false;

    // numbers: coerce + validate via Number.isFinite
    const dls = +cfg.dayLengthSec;
    if (Number.isFinite(dls) && dls > 1) _dayLengthSec = dls;

    const az = +cfg.azimuthDeg;
    if (Number.isFinite(az)) _azimuthDeg = az;

    const ni = +cfg.nightIntensity;
    if (Number.isFinite(ni)) _nightIntensity = ni;

    const di = +cfg.dayIntensity;
    if (Number.isFinite(di)) _dayIntensity = di;

    const sw = +cfg.sunsetWarmth;
    if (Number.isFinite(sw)) _sunsetWarmth = sw;

    // string: coerce if present (no typeof)
    if (cfg.skybox != null) _skyboxAsset = String(cfg.skybox);

    // fog block (assume objects when present)
    if (cfg.fog) {
        const fc = cfg.fog.color;
        if (fc) {
            // keep defaults; override only present keys
            if (fc.r != null) _fogBase.r = +fc.r;
            if (fc.g != null) _fogBase.g = +fc.g;
            if (fc.b != null) _fogBase.b = +fc.b;
        }

        const fd = +cfg.fog.distance;
        if (Number.isFinite(fd)) _fogDistance = fd;

        const fdd = +cfg.fog.densityDay;
        if (Number.isFinite(fdd)) _fogDensityDay = fdd;

        const fdn = +cfg.fog.densityNight;
        if (Number.isFinite(fdn)) _fogDensityNight = fdn;
    }

    if (cfg.ambientDay) {
        const a = cfg.ambientDay;
        if (a.r != null) _ambientDay.r = +a.r;
        if (a.g != null) _ambientDay.g = +a.g;
        if (a.b != null) _ambientDay.b = +a.b;
        if (a.intensity != null) _ambientDay.intensity = +a.intensity;
    }

    if (cfg.ambientNight) {
        const a = cfg.ambientNight;
        if (a.r != null) _ambientNight.r = +a.r;
        if (a.g != null) _ambientNight.g = +a.g;
        if (a.b != null) _ambientNight.b = +a.b;
        if (a.intensity != null) _ambientNight.intensity = +a.intensity;
    }

    if (cfg.shadows) {
        const s = cfg.shadows;
        const ms = +s.mapSize;
        const sp = +s.splits;
        const lm = +s.lambda;

        if (Number.isFinite(ms)) _shadowsCfg.mapSize = ms;
        if (Number.isFinite(sp)) _shadowsCfg.splits = sp;
        if (Number.isFinite(lm)) _shadowsCfg.lambda = lm;
    }
}

function applyStaticOnce() {
    if (_staticApplied) return;
    _staticApplied = true;

    render.ensureScene();

    render.ambientCfg({
        color: [_ambientDay.r, _ambientDay.g, _ambientDay.b],
        intensity: _ambientDay.intensity
    });

    render.sunCfg({
        dir: [-1, -1, -0.3],
        color: [1.0, 0.98, 0.90],
        intensity: 1.2
    });

    if (_skyboxAsset) render.skyboxCube(_skyboxAsset);

    // enforce ints for mapSize/splits
    const ms = math.clamp((Math.round(Number(_shadowsCfg.mapSize) || 2048) | 0), 256, 8192);
    const sp = math.clamp((Math.round(Number(_shadowsCfg.splits) || 3) | 0), 1, 4);
    const lm = Number(_shadowsCfg.lambda);
    render.sunShadowsCfg({ mapSize: ms, splits: sp, lambda: Number.isFinite(lm) ? lm : 0.65 });

    render.fogCfg({
        color: [_fogBase.r, _fogBase.g, _fogBase.b],
        density: _fogDensityDay,
        distance: _fogDistance
    });

    _lastFogDensity = NaN;
    _lastFogColorKey = "";
    _lastFogDistance = NaN;
}

function applyAtTime01(time01) {
    const phase = math.wrap(time01, 0, 1);

    const alt = Math.sin((phase * Math.PI * 2.0) - Math.PI * 0.5); // -1..1
    const altitude =
        math.lerp(-0.25, 1.05, (alt + 1.0) * 0.5) * (Math.PI / 2.0) -
        (Math.PI / 2.0) * 0.15;

    const azimuth = (phase * Math.PI * 2.0) + math.degToRad(_azimuthDeg);

    const d = dirFromAltAz(altitude, azimuth);

    const above = math.clamp((d.y + 0.02) / 0.45, 0, 1);
    const dayFactor = math.smoothstep(0.02, 0.25, above);

    const noonBoost = math.smoothstep(0.25, 1.0, above);
    const intensity = math.lerp(_nightIntensity, _dayIntensity, dayFactor) * math.lerp(0.55, 1.0, noonBoost);

    const horizonWarm = math.smoothstep(0.0, 0.18, 1.0 - above) * dayFactor;
    const warm = _sunsetWarmth * horizonWarm;

    const baseR = 1.0, baseG = 0.98, baseB = 0.90;
    const r = math.lerp(baseR, 1.15, warm);
    const g = math.lerp(baseG, 0.92, warm);
    const b = math.lerp(baseB, 0.65, warm);

    render.sunCfg({
        dir: [d.x, d.y, d.z],
        color: [r, g, b],
        intensity: intensity
    });

    const ambR = math.lerp(_ambientNight.r, _ambientDay.r, dayFactor);
    const ambG = math.lerp(_ambientNight.g, _ambientDay.g, dayFactor);
    const ambB = math.lerp(_ambientNight.b, _ambientDay.b, dayFactor);
    const ambI = math.lerp(_ambientNight.intensity, _ambientDay.intensity, dayFactor);

    render.ambientCfg({
        color: [ambR, ambG, ambB],
        intensity: ambI
    });

    // fog throttling
    const fogD = math.lerp(_fogDensityNight, _fogDensityDay, dayFactor);
    const key = fogKeyRGB(_fogBase.r, _fogBase.g, _fogBase.b);

    if (
        Math.abs(fogD - _lastFogDensity) > 0.002 ||
        key !== _lastFogColorKey ||
        Math.abs(_fogDistance - _lastFogDistance) > 0.1
    ) {
        render.fogCfg({
            color: [_fogBase.r, _fogBase.g, _fogBase.b],
            density: fogD,
            distance: _fogDistance
        });
        _lastFogDensity = fogD;
        _lastFogColorKey = key;
        _lastFogDistance = _fogDistance;
    }
}

function wireEventsOnce() {
    if (_wiredEvents) return;
    _wiredEvents = true;

    try {
        const ev = engine.events();

        ev.on("sky:setTime", function (p) {
            if (!p) return;

            const dls = +p.dayLengthSec;
            if (Number.isFinite(dls) && dls > 1) _dayLengthSec = dls;

            // prefer time01 if provided
            const t01 = +p.time01;
            if (Number.isFinite(t01)) {
                _t = _dayLengthSec * math.wrap(t01, 0, 1);
            } else {
                const ts = +p.timeSec;
                if (Number.isFinite(ts)) _t = ts;
            }

            applyAtTime01(_t / _dayLengthSec);
        });

        ev.on("sky:setSpeed", function (p) {
            if (!p) return;
            const dls = +p.dayLengthSec;
            if (Number.isFinite(dls) && dls > 1) _dayLengthSec = dls;
        });

        ev.on("sky:setEnabled", function (p) {
            if (!p) return;
            if (p.enabled === true) _enabled = true;
            if (p.enabled === false) _enabled = false;
        });
    } catch (e) {
        engine.log().warn("[sky] events wiring skipped: " + e);
    }
}

// ---------- system hooks ----------
module.exports.init = function (ctx) {
    engine.log().info("[sky] init");

    applyConfig(readCfg(ctx));
    applyStaticOnce();

    _t = _dayLengthSec * 0.18;
    applyAtTime01(_t / _dayLengthSec);

    wireEventsOnce();
};

module.exports.update = function (ctx, tpf) {
    applyConfig(readCfg(ctx));
    if (!_enabled) return;

    const dt = getTpf(ctx, tpf);

    _t += dt;
    if (_t > _dayLengthSec) _t -= _dayLengthSec;

    applyAtTime01(_t / _dayLengthSec);

    _dbgAcc += dt;
    if (_dbgAcc > 2.0) {
        _dbgAcc = 0;
        const phase = _t / _dayLengthSec;
        engine.log().debug("[sky] phase=" + phase.toFixed(3) + " t=" + _t.toFixed(2) + " dt=" + dt.toFixed(4));
    }
};

module.exports.destroy = function () {};