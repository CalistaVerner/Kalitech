// FILE: Scripts/systems/sky.js
// Author: Calista Verner
//
// Sky + Sun + Shadows + Skybox + Fog controller
// jsSystem: { module:"Scripts/systems/sky.js", ...config }

const M = require("@core/math");

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

// robust config reader (supports different ctx shapes)
function readCfg(ctx) {
    if (!ctx) return null;
    if (ctx.config && typeof ctx.config === "object") return ctx.config;
    if (ctx.cfg && typeof ctx.cfg === "object") return ctx.cfg;
    if (ctx.system && ctx.system.config && typeof ctx.system.config === "object") return ctx.system.config;

    try {
        if (typeof ctx.getConfig === "function") {
            const c = ctx.getConfig();
            if (c && typeof c === "object") return c;
        }
    } catch (_) {}
    return null;
}

// robust tpf getter (update(ctx,tpf) or update(ctx))
function getTpf(ctx, maybeTpf) {
    if (typeof maybeTpf === "number" && isFinite(maybeTpf) && maybeTpf > 0) return maybeTpf;

    if (ctx && typeof ctx.tpf === "number" && isFinite(ctx.tpf) && ctx.tpf > 0) return ctx.tpf;

    try {
        const t = ctx && ctx.time;
        if (t && typeof t.tpf === "number" && isFinite(t.tpf) && t.tpf > 0) return t.tpf;
    } catch (_) {}

    try {
        const timeApi = engine && engine.time && engine.time();
        if (timeApi && typeof timeApi.tpf === "function") {
            const v = timeApi.tpf();
            if (typeof v === "number" && isFinite(v) && v > 0) return v;
        }
    } catch (_) {}

    return 1.0 / 60.0;
}

function applyConfig(cfg) {
    if (!cfg || typeof cfg !== "object") return;

    if (typeof cfg.enabled === "boolean") _enabled = cfg.enabled;

    if (typeof cfg.dayLengthSec === "number" && cfg.dayLengthSec > 1) _dayLengthSec = cfg.dayLengthSec;
    if (typeof cfg.azimuthDeg === "number") _azimuthDeg = cfg.azimuthDeg;

    if (typeof cfg.nightIntensity === "number") _nightIntensity = cfg.nightIntensity;
    if (typeof cfg.dayIntensity === "number") _dayIntensity = cfg.dayIntensity;

    if (typeof cfg.sunsetWarmth === "number") _sunsetWarmth = cfg.sunsetWarmth;

    if (typeof cfg.skybox === "string") _skyboxAsset = cfg.skybox;

    if (cfg.fog && typeof cfg.fog === "object") {
        if (cfg.fog.color && typeof cfg.fog.color === "object") {
            _fogBase = { ..._fogBase, ...cfg.fog.color };
        }
        if (typeof cfg.fog.distance === "number") _fogDistance = cfg.fog.distance;
        if (typeof cfg.fog.densityDay === "number") _fogDensityDay = cfg.fog.densityDay;
        if (typeof cfg.fog.densityNight === "number") _fogDensityNight = cfg.fog.densityNight;
    }

    if (cfg.ambientDay && typeof cfg.ambientDay === "object") _ambientDay = { ..._ambientDay, ...cfg.ambientDay };
    if (cfg.ambientNight && typeof cfg.ambientNight === "object") _ambientNight = { ..._ambientNight, ...cfg.ambientNight };

    if (cfg.shadows && typeof cfg.shadows === "object") {
        _shadowsCfg = {
            mapSize: cfg.shadows.mapSize ?? _shadowsCfg.mapSize,
            splits: cfg.shadows.splits ?? _shadowsCfg.splits,
            lambda: cfg.shadows.lambda ?? _shadowsCfg.lambda
        };
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
    if (_shadowsCfg) {
        const ms = M.clamp((Math.round(Number(_shadowsCfg.mapSize) || 2048) | 0), 256, 8192);
        const sp = M.clamp((Math.round(Number(_shadowsCfg.splits) || 3) | 0), 1, 4);
        const lm = Number(_shadowsCfg.lambda);
        render.sunShadowsCfg({ mapSize: ms, splits: sp, lambda: isFinite(lm) ? lm : 0.65 });
    }

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
    const phase = M.wrap(time01, 0, 1);

    const alt = Math.sin((phase * Math.PI * 2.0) - Math.PI * 0.5); // -1..1
    const altitude =
        M.lerp(-0.25, 1.05, (alt + 1.0) * 0.5) * (Math.PI / 2.0) -
        (Math.PI / 2.0) * 0.15;

    const azimuth = (phase * Math.PI * 2.0) + M.degToRad(_azimuthDeg);

    const d = dirFromAltAz(altitude, azimuth);

    const above = M.clamp((d.y + 0.02) / 0.45, 0, 1);
    const dayFactor = M.smoothstep(0.02, 0.25, above);

    const noonBoost = M.smoothstep(0.25, 1.0, above);
    const intensity = M.lerp(_nightIntensity, _dayIntensity, dayFactor) * M.lerp(0.55, 1.0, noonBoost);

    const horizonWarm = M.smoothstep(0.0, 0.18, 1.0 - above) * dayFactor;
    const warm = _sunsetWarmth * horizonWarm;

    const baseR = 1.0, baseG = 0.98, baseB = 0.90;
    const r = M.lerp(baseR, 1.15, warm);
    const g = M.lerp(baseG, 0.92, warm);
    const b = M.lerp(baseB, 0.65, warm);

    render.sunCfg({
        dir: [d.x, d.y, d.z],
        color: [r, g, b],
        intensity: intensity
    });

    const ambR = M.lerp(_ambientNight.r, _ambientDay.r, dayFactor);
    const ambG = M.lerp(_ambientNight.g, _ambientDay.g, dayFactor);
    const ambB = M.lerp(_ambientNight.b, _ambientDay.b, dayFactor);
    const ambI = M.lerp(_ambientNight.intensity, _ambientDay.intensity, dayFactor);

    render.ambientCfg({
        color: [ambR, ambG, ambB],
        intensity: ambI
    });

    // fog throttling
    const fogD = M.lerp(_fogDensityNight, _fogDensityDay, dayFactor);
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
        const events = engine.events();

        events.on("sky:setTime", function (p) {
            if (!p) return;

            if (typeof p.dayLengthSec === "number" && p.dayLengthSec > 1) _dayLengthSec = p.dayLengthSec;

            if (typeof p.time01 === "number") {
                _t = _dayLengthSec * M.wrap(p.time01, 0, 1);
            } else if (typeof p.timeSec === "number") {
                _t = p.timeSec;
            }
            applyAtTime01(_t / _dayLengthSec);
        });

        events.on("sky:setSpeed", function (p) {
            if (!p) return;
            if (typeof p.dayLengthSec === "number" && p.dayLengthSec > 1) _dayLengthSec = p.dayLengthSec;
        });

        events.on("sky:setEnabled", function (p) {
            if (!p) return;
            if (typeof p.enabled === "boolean") _enabled = p.enabled;
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