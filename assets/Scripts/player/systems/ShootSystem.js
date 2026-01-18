"use strict";

const U = require("../util.js");

function clamp(v, a, b) {
    v = +v;
    return v < a ? a : (v > b ? b : v);
}

function normalize3_into(x, y, z, out) {
    x = +x;
    y = +y;
    z = +z;
    const l2 = x * x + y * y + z * z;
    if (!(l2 > 1e-12) || !Number.isFinite(l2)) {
        out.x = 0;
        out.y = 0;
        out.z = 1;
        return out;
    }
    const inv = 1.0 / Math.sqrt(l2);
    out.x = x * inv; out.y = y * inv; out.z = z * inv;
    return out;
}

function vec3FromAny(v) {
    if (!v) return null;

    if (Array.isArray(v) && v.length >= 3) {
        const x = +v[0], y = +v[1], z = +v[2];
        if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z)) return null;
        return {x, y, z};
    }

    const x = +v.x, y = +v.y, z = +v.z;
    if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z)) return null;
    return {x, y, z};
}

function idOfSurfaceHandle(g) {
    if (!g) return 0;
    if (typeof g.surfaceId === "number") return g.surfaceId | 0;
    if (typeof g.id === "number") return g.id | 0;
    if (typeof g.id === "function") return (g.id() | 0) || 0;
    if (typeof g.getId === "function") return (g.getId() | 0) || 0;
    if (g.handle && typeof g.handle.id === "number") return g.handle.id | 0;
    return 0;
}

/**
 * Deterministic, local RNG (Mulberry32) seeded from a stable source.
 * Does not affect global Math.random(), so it is safe for the rest of the game.
 */
function mulberry32(seedU32) {
    let a = seedU32 >>> 0;
    return function rand() {
        a = (a + 0x6D2B79F5) >>> 0;
        let t = a;
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

/**
 * Stable string->u32 hash (FNV-1a).
 */
function hashStrU32(s) {
    s = String(s == null ? "" : s);
    let h = 2166136261 >>> 0;
    for (let i = 0; i < s.length; i++) {
        h ^= s.charCodeAt(i) & 0xffff;
        h = Math.imul(h, 16777619);
    }
    return h >>> 0;
}

/**
 * Mix 2 u32 values into one u32.
 */
function mixU32(a, b) {
    a = (a >>> 0) ^ (b >>> 0);
    a = Math.imul(a ^ (a >>> 16), 0x7feb352d);
    a = Math.imul(a ^ (a >>> 15), 0x846ca68b);
    return (a ^ (a >>> 16)) >>> 0;
}

const DEFAULT_CFG = Object.freeze({
    enabled: true,
    spawnOffset: 0.0,
    speed: 460.0,
    invertPitch: true,

    radiusMin: 0.05,
    radiusMax: 1.85,

    density: 18.0,
    massMin: 1.0,
    massMax: 1200.0,

    impactFilter: {
        minImpulse: 0.25,
        minRelSpeed: 0.20
    },

    impact: {
        enabled: true,
        soundEvent: "world.impact",
        particles: {
            enabled: true,
            template: "impact",
            burst: 24,
            ttlMs: 900,
            override: null
        }
    },

    events: {
        fire: "game.shoot.fire",
        hit: "game.shoot.hit"
    },

    /**
     * Determinism settings for shot simulation (JS-side).
     * - enabled: if true, uses local seeded RNG instead of Math.random().
     * - seed: optional manual seed; if 0/undefined uses particles frameSeed if available.
     */
    deterministic: {
        enabled: true,
        seed: 0
    }
});

function mergeCfg(rootCfg) {
    const b = DEFAULT_CFG;
    const s = (rootCfg && rootCfg.shoot) || {};

    const impact = s.impact || {};
    const particles = (impact.particles || {});
    const filter = s.impactFilter || {};
    const ev = s.events || {};
    const det = s.deterministic || {};

    return {
        enabled: s.enabled !== undefined ? !!s.enabled : b.enabled,
        spawnOffset: s.spawnOffset !== undefined ? U.num(s.spawnOffset, b.spawnOffset) : b.spawnOffset,
        speed: s.speed !== undefined ? U.num(s.speed, b.speed) : b.speed,
        invertPitch: s.invertPitch !== undefined ? !!s.invertPitch : b.invertPitch,

        radiusMin: s.radiusMin !== undefined ? U.num(s.radiusMin, b.radiusMin) : b.radiusMin,
        radiusMax: s.radiusMax !== undefined ? U.num(s.radiusMax, b.radiusMax) : b.radiusMax,
        density: s.density !== undefined ? U.num(s.density, b.density) : b.density,
        massMin: s.massMin !== undefined ? U.num(s.massMin, b.massMin) : b.massMin,
        massMax: s.massMax !== undefined ? U.num(s.massMax, b.massMax) : b.massMax,

        impactFilter: {
            minImpulse: filter.minImpulse !== undefined ? U.num(filter.minImpulse, b.impactFilter.minImpulse) : b.impactFilter.minImpulse,
            minRelSpeed: filter.minRelSpeed !== undefined ? U.num(filter.minRelSpeed, b.impactFilter.minRelSpeed) : b.impactFilter.minRelSpeed
        },

        impact: {
            enabled: impact.enabled !== undefined ? !!impact.enabled : b.impact.enabled,
            soundEvent: impact.soundEvent !== undefined ? String(impact.soundEvent || "") : b.impact.soundEvent,
            particles: {
                enabled: particles.enabled !== undefined ? !!particles.enabled : b.impact.particles.enabled,
                template: particles.template !== undefined ? String(particles.template || "") : b.impact.particles.template,
                burst: particles.burst !== undefined ? (U.num(particles.burst, b.impact.particles.burst) | 0) : b.impact.particles.burst,
                ttlMs: particles.ttlMs !== undefined ? (U.num(particles.ttlMs, b.impact.particles.ttlMs) | 0) : b.impact.particles.ttlMs,
                override: particles.override !== undefined ? particles.override : b.impact.particles.override
            }
        },

        events: {
            fire: ev.fire !== undefined ? String(ev.fire || "") : b.events.fire,
            hit: ev.hit !== undefined ? String(ev.hit || "") : b.events.hit
        },

        deterministic: {
            enabled: det.enabled !== undefined ? !!det.enabled : b.deterministic.enabled,
            seed: det.seed !== undefined ? (U.num(det.seed, b.deterministic.seed) | 0) : b.deterministic.seed
        }
    };
}

class ShootSystem {
    constructor(player) {
        this.player = player;
        this.cfg = mergeCfg(player && player.cfg);

        this._shotId = 0;
        this._subImpact = 0;
        this._subCollBegin = 0;

        this._dir = { x: 0, y: 0, z: 1 };
        this._origin = { x: 0, y: 0, z: 0 };
        this._spawn = { x: 0, y: 0, z: 0 };
        this._vel = { x: 0, y: 0, z: 0 };

        this._shotsBySurface = Object.create(null);

        this._P = (typeof PARTICLES !== "undefined" && PARTICLES) ? PARTICLES : null;

        // Deterministic RNG state
        this._rng = null;
        this._rngSeedU32 = 0;
        this._rngReady = false;

        // Stable tag for hashing across runs
        this._sysTagU32 = hashStrU32("ShootSystem.v1");
    }

    configure(cfg) {
        if (this.player) this.player.cfg = cfg;
        this.cfg = mergeCfg(cfg);
        this._P = (typeof PARTICLES !== "undefined" && PARTICLES) ? PARTICLES : null;
        this._rng = null;
        this._rngReady = false;
        return this;
    }

    _bus() {
        return this.player && this.player.d ? this.player.d.bus : null;
    }

    _emit(topic, payload) {
        const bus = this._bus();
        if (bus && typeof bus.emit === "function") bus.emit(topic, payload);
    }

    _bindPhysicsFx() {
        const bus = this._bus();
        if (!bus) throw new Error("[shoot] bus missing");

        if (!this._subImpact) this._subImpact = (bus.on("engine.physics.impact", (p) => this._onImpact(p)) | 0);
        if (!this._subCollBegin) this._subCollBegin = (bus.on("engine.physics.collision.begin", (p) => this._onCollisionBegin(p)) | 0);
    }

    _dirFromYawPitch_into(yaw, pitch, outDir) {
        const c = this.cfg;
        yaw = U.num(yaw, 0);
        pitch = U.num(pitch, 0);

        const LIM = (Math.PI * 0.5) - 1e-4;
        pitch = clamp(pitch, -LIM, LIM);
        if (c.invertPitch) pitch = -pitch;

        const sy = Math.sin(yaw), cy = Math.cos(yaw);
        const sp = Math.sin(pitch), cp = Math.cos(pitch);
        return normalize3_into(sy * cp, sp, cy * cp, outDir);
    }

    _readOrigin_into(frame, outOrigin) {
        outOrigin.x = U.num(frame.pose.x, 0);
        outOrigin.y = U.num(frame.pose.y, 0) + U.num(frame.character.eyeHeight, 1.55);
        outOrigin.z = U.num(frame.pose.z, 0);
        return outOrigin;
    }

    _ensureRng(frame) {
        const det = this.cfg.deterministic;
        if (!det || !det.enabled) {
            this._rng = null;
            this._rngReady = false;
            return;
        }

        // Try to anchor to engine particles frameSeed if available.
        // This is optional: if PARTICLES doesn't provide it, we fall back to deterministic seed only.
        let base = det.seed | 0;

        const P = this._P;
        if (!base && P) {
            try {
                if (typeof P.frameSeed === "function") base = (P.frameSeed() | 0) || 0;
                else if (typeof P.frameSeed === "number") base = (P.frameSeed | 0) || 0;
            } catch (_) {
            }
        }

        // If still no base seed, derive from stable inputs (frame time is NOT used).
        if (!base) {
            const owner = (frame && frame.owner && (frame.owner.id | 0)) || 0;
            base = mixU32(this._sysTagU32, owner);
            if (!base) base = 0x12345678;
        }

        // Mix with shotId to avoid repeating patterns across shots within same frame.
        const seedU32 = mixU32(base >>> 0, (this._shotId + 1) >>> 0);

        if (this._rngReady && seedU32 === this._rngSeedU32 && this._rng) return;

        this._rngSeedU32 = seedU32 >>> 0;
        this._rng = mulberry32(this._rngSeedU32);
        this._rngReady = true;
    }

    _rand01() {
        if (this._rngReady && this._rng) return this._rng();
        return Math.random();
    }

    _randBetween(a, b) {
        a = +a;
        b = +b;
        if (!Number.isFinite(a)) a = 0;
        if (!Number.isFinite(b)) b = 0;
        if (b < a) {
            const t = a;
            a = b;
            b = t;
        }
        return a + this._rand01() * (b - a);
    }

    _massFromRadius(r, density) {
        r = +r;
        density = +density;
        if (!Number.isFinite(r) || r <= 0) r = 0.1;
        if (!Number.isFinite(density) || density <= 0) density = 1.0;
        return density * (4.0 / 3.0) * Math.PI * r * r * r;
    }

    _contactPoint(payload) {
        const p = payload && payload.contact && payload.contact.point;
        return vec3FromAny(p);
    }

    _contactNormal(payload) {
        const n = payload && payload.contact && payload.contact.normal;
        return vec3FromAny(n);
    }

    _contactImpulse(payload) {
        const c = payload && payload.contact;
        const v = c && c.maxImpulse;
        const x = +v;
        return Number.isFinite(x) ? x : 0;
    }

    _relSpeed(payload) {
        const v = payload && payload.relSpeed;
        const x = +v;
        return Number.isFinite(x) ? x : 0;
    }

    _isMyShotPair(payload) {
        const aS = payload && payload.a && typeof payload.a.surfaceId === "number" ? (payload.a.surfaceId | 0) : 0;
        const bS = payload && payload.b && typeof payload.b.surfaceId === "number" ? (payload.b.surfaceId | 0) : 0;
        if (aS <= 0 || bS <= 0) return 0;
        return this._shotsBySurface[aS] ? aS : (this._shotsBySurface[bS] ? bS : 0);
    }

    _passesImpactFilter(payload) {
        const f = this.cfg.impactFilter;
        const imp = this._contactImpulse(payload);
        const rel = this._relSpeed(payload);
        if (imp < +f.minImpulse) return false;
        if (rel > 0 && rel < +f.minRelSpeed) return false;
        return true;
    }

    _impactFx(pos, payload, shotSurfaceId, source) {
        const c = this.cfg;
        const impCfg = c.impact;
        if (!impCfg || !impCfg.enabled || !pos) return;

        ENGINE.sound.playSound({event: impCfg.soundEvent, is3D: true, random: true, x: pos.x, y: pos.y, z: pos.z});

        PARTICLES.spawn("impact", {
            pos: {x: pos.x, y: pos.y, z: pos.z},
            burst: 320,
            ttlMs: 650,
            seed: 12345,
            override: {
                velocity: {min: 4.0, max: 9.0, coneDeg: 20.0},
                color: {start: {r: 0.6, g: 0.9, b: 1.0, a: 1.0}}
            }
        });


        this._emit(c.events.hit, {
            surfaceId: shotSurfaceId | 0,
            pos,
            impulse: payload && payload.impulse,
            relSpeed: payload && payload.relSpeed,
            energyApprox: payload && payload.energyApprox,
            hardSide: payload && payload.hardSide,
            normal: this._contactNormal(payload),
            source
        });
    }

    _onImpact(payload) {
        const shotSurfaceId = this._isMyShotPair(payload);
        if (!shotSurfaceId) return;

        delete this._shotsBySurface[shotSurfaceId];

        const pos = this._contactPoint(payload);
        if (!pos) return;

        this._impactFx(pos, payload, shotSurfaceId, "impact");
    }

    _onCollisionBegin(payload) {
        const shotSurfaceId = this._isMyShotPair(payload);
        if (!shotSurfaceId) return;

        const pos = this._contactPoint(payload);
        if (!pos) return;

        if (!this._passesImpactFilter(payload)) return;

        delete this._shotsBySurface[shotSurfaceId];
        this._impactFx(pos, payload, shotSurfaceId, "collision.begin");
    }

    _fire(frame, ownerBodyId) {
        ENGINE.sound.playSound({event: "player.action.throw", random: true});

        const c = this.cfg;
        if (!c.enabled || !ownerBodyId) return;

        this._bindPhysicsFx();

        // Prepare deterministic RNG for this shot (does not affect global randomness).
        this._ensureRng(frame);

        this._readOrigin_into(frame, this._origin);
        this._dirFromYawPitch_into(frame.view.yaw, frame.view.pitch, this._dir);

        const off = c.spawnOffset;
        this._spawn.x = this._origin.x + this._dir.x * off;
        this._spawn.y = this._origin.y + this._dir.y * off;
        this._spawn.z = this._origin.z + this._dir.z * off;

        const r = this._randBetween(c.radiusMin, c.radiusMax);

        let mass = this._massFromRadius(r, c.density);
        if (mass < c.massMin) mass = +c.massMin;
        if (mass > c.massMax) mass = +c.massMax;

        const name = "shot-" + (++this._shotId);

        const g = ENGINE.mesh.sphere$()
            .size(r)
            .name(name)
            .pos(this._spawn.x, this._spawn.y, this._spawn.z)
            .material(MAT.getMaterial("box"))
            .physics(mass, {lockRotation: false})
            .create();

        this._vel.x = this._dir.x * c.speed;
        this._vel.y = this._dir.y * c.speed;
        this._vel.z = this._dir.z * c.speed;
        g.velocity(this._vel);

        const surfaceId = idOfSurfaceHandle(g);
        if (surfaceId > 0) this._shotsBySurface[surfaceId] = 1;

        this._emit(c.events.fire, {surfaceId: surfaceId | 0, ownerBodyId: ownerBodyId | 0});
    }

    update(frame, ownerBodyId) {
        if (!this.cfg.enabled) return;
        if (!frame || !frame.input || !frame.input.lmbJustPressed) return;
        this._fire(frame, ownerBodyId | 0);
    }

    destroy() {
        const bus = this._bus();
        if (bus && typeof bus.off === "function") {
            if (this._subImpact) bus.off(this._subImpact | 0);
            if (this._subCollBegin) bus.off(this._subCollBegin | 0);
        }
        this._subImpact = 0;
        this._subCollBegin = 0;
        this._shotsBySurface = Object.create(null);
        this._rng = null;
        this._rngReady = false;
    }
}

module.exports = ShootSystem;
