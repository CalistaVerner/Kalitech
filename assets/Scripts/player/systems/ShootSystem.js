"use strict";

const U = require("../util.js");

function normalize3_into(x, y, z, out) {
    const l2 = x * x + y * y + z * z;
    if (l2 < 1e-12) { out.x = 0; out.y = 0; out.z = 1; return out; }
    const inv = 1.0 / Math.sqrt(l2);
    out.x = x * inv; out.y = y * inv; out.z = z * inv;
    return out;
}
function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }

function logI(msg) {
    try {
        if (ENGINE && ENGINE.log && typeof ENGINE.log.info === "function") ENGINE.log.info(String(msg));
        else if (typeof print === "function") print(String(msg));
        else if (console && typeof console.log === "function") console.log(String(msg));
    } catch (e) {
    }
}

function logD(msg) {
    try {
        if (ENGINE && ENGINE.log && typeof ENGINE.log.debug === "function") ENGINE.log.debug(String(msg));
        else logI(msg);
    } catch (e) {
    }
}

function logW(msg) {
    try {
        if (ENGINE && ENGINE.log && typeof ENGINE.log.warn === "function") ENGINE.log.warn(String(msg));
        else logI(String(msg));
    } catch (e) {
    }
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

    debug: {
        impactFx: true,
        logShots: false,
        logBus: true,
        logCollisions: true,
        logParticles: true
    },

    // Fallback thresholds (JS-side)
    impactFilter: {
        minImpulse: 0.25,
        minRelSpeed: 0.20
    },

    impact: {
        enabled: true,
        sound: {src: "Sounds/hit/.ogg", volume: 0.85, loop: false},
        particles: {
            enabled: true,

            // AAA options
            template: "",
            burst: 24,
            ttlMs: 1200,

            name: "impact",
            type: "triangle",
            max: 96,
            texture: "Textures/Particles/spark.png",
            spriteRows: 1,
            spriteCols: 1,
            rate: 0,
            local: false,
            gravity: {x: 0, y: -6.0, z: 0},
            size: {start: 0.20, end: 0.05},
            life: {min: 0.12, max: 0.35},
            velocity: {
                min: 2.0,
                max: 7.0,
                coneDeg: 35.0,
                variation: 0.35
            },
            shape: {type: "sphere", radius: 0.04},
            render: {additive: true, depthWrite: false, depthTest: true, noCulling: true},
            color: {
                start: {r: 1.0, g: 0.9, b: 0.7, a: 1.0},
                end: {r: 1.0, g: 0.35, b: 0.05, a: 0.0}
            }
        }
    },

    events: {
        fire: "game.shoot.fire",
        hit: "game.shoot.hit"
    }
});

function mergeCfg(rootCfg) {
    const s = (rootCfg && rootCfg.shoot) || {};
    const b = DEFAULT_CFG;

    const dbg = s.debug || {};
    const i = s.impact || {};
    const snd = i.sound || {};
    const p = i.particles || {};
    const ev = s.events || {};
    const impF = s.impactFilter || {};

    const particles = Object.assign({}, b.impact.particles, p);

    if (particles.template != null) particles.template = String(particles.template || "");
    if (particles.ttlMs != null) particles.ttlMs = U.num(particles.ttlMs, b.impact.particles.ttlMs) | 0;
    if (particles.burst != null) particles.burst = (U.num(particles.burst, b.impact.particles.burst) | 0);

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

        debug: {
            impactFx: dbg.impactFx !== undefined ? !!dbg.impactFx : !!b.debug.impactFx,
            logShots: dbg.logShots !== undefined ? !!dbg.logShots : !!b.debug.logShots,
            logBus: dbg.logBus !== undefined ? !!dbg.logBus : !!b.debug.logBus,
            logCollisions: dbg.logCollisions !== undefined ? !!dbg.logCollisions : !!b.debug.logCollisions,
            logParticles: dbg.logParticles !== undefined ? !!dbg.logParticles : !!b.debug.logParticles
        },

        impactFilter: {
            minImpulse: impF.minImpulse !== undefined ? U.num(impF.minImpulse, b.impactFilter.minImpulse) : b.impactFilter.minImpulse,
            minRelSpeed: impF.minRelSpeed !== undefined ? U.num(impF.minRelSpeed, b.impactFilter.minRelSpeed) : b.impactFilter.minRelSpeed
        },

        impact: {
            enabled: i.enabled !== undefined ? !!i.enabled : b.impact.enabled,
            sound: {
                src: snd.src !== undefined ? String(snd.src) : b.impact.sound.src,
                volume: snd.volume !== undefined ? U.num(snd.volume, b.impact.sound.volume) : b.impact.sound.volume,
                loop: snd.loop !== undefined ? !!snd.loop : b.impact.sound.loop
            },
            particles
        },

        events: {
            fire: ev.fire !== undefined ? String(ev.fire) : b.events.fire,
            hit: ev.hit !== undefined ? String(ev.hit) : b.events.hit
        }
    };
}

function idOfSurfaceHandle(g) {
    if (!g) return 0;
    if (typeof g.surfaceId === "number") return g.surfaceId | 0;
    if (typeof g.id === "number") return g.id | 0;
    if (typeof g.id === "function") return (g.id() | 0) || 0;
    if (typeof g.getId === "function") return (g.getId() | 0) || 0;
    if (typeof g.handle === "object" && g.handle && typeof g.handle.id === "number") return g.handle.id | 0;
    return 0;
}

function vec3FromAny(v) {
    if (!v) return null;
    try {
        if (Array.isArray(v) && v.length >= 3) {
            const x = +v[0], y = +v[1], z = +v[2];
            if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z)) return null;
            return {x, y, z};
        }
    } catch (e) {
    }
    const x = +v.x, y = +v.y, z = +v.z;
    if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z)) return null;
    return {x, y, z};
}

class ShootSystem {
    constructor(player) {
        this.player = player;
        this.cfg = mergeCfg(player.cfg);

        this._shotId = 0;

        this._subImpact = 0;
        this._subCollBegin = 0;

        this._dir = { x: 0, y: 0, z: 1 };
        this._origin = { x: 0, y: 0, z: 0 };
        this._spawn = { x: 0, y: 0, z: 0 };
        this._vel = { x: 0, y: 0, z: 0 };

        // surfaceId -> 1 (projectile marker)
        this._shotsBySurface = Object.create(null);

        this._P = (typeof PARTICLES !== "undefined" && PARTICLES) ? PARTICLES : null;

        this._impactTemplateReady = false;
        this._impactTemplateName = "";

        this._ensureImpactTemplate();

        if (this.cfg.debug.logBus) {
            logD("[shoot] init bus=" + (!!this._bus()) +
                " impactTopic=engine.physics.impact collisionBeginTopic=engine.physics.collision.begin");
        }

        if (this.cfg.debug.impactFx) {
            if (!this._P) logW("[shoot] PARTICLES global missing (Particles.js not loaded?)");
            else logI("[shoot] PARTICLES ready: spawn=" + (typeof this._P.spawn) +
                " define=" + (typeof this._P.define) +
                " create=" + (typeof this._P.create));
        }
    }

    configure(cfg) {
        this.player.cfg = cfg;
        this.cfg = mergeCfg(cfg);
        this._P = (typeof PARTICLES !== "undefined" && PARTICLES) ? PARTICLES : null;

        this._impactTemplateReady = false;
        this._impactTemplateName = "";
        this._ensureImpactTemplate();

        return this;
    }

    _bus() {
        return this.player && this.player.d ? this.player.d.bus : null;
    }

    _emit(topic, payload) {
        const bus = this._bus();
        if (bus) bus.emit(topic, payload);
    }

    _bindPhysicsFx() {
        const bus = this._bus();
        if (!bus) throw new Error("[shoot] bus missing");

        if (!this._subImpact) {
            this._subImpact = (bus.on("engine.physics.impact", (p) => this._onImpact(p)) | 0);
            if (this.cfg.debug.logBus) logD("[shoot] subscribed engine.physics.impact subId=" + (this._subImpact | 0));
        }

        // Fallback: if impact never comes, collision.begin still provides contact payload for registered bodies
        if (!this._subCollBegin) {
            this._subCollBegin = (bus.on("engine.physics.collision.begin", (p) => this._onCollisionBegin(p)) | 0);
            if (this.cfg.debug.logBus) logD("[shoot] subscribed engine.physics.collision.begin subId=" + (this._subCollBegin | 0));
        }

        if (!this._subImpact && !this._subCollBegin) {
            throw new Error("[shoot] failed to subscribe physics topics");
        }
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

    _randBetween(a, b) {
        a = +a;
        b = +b;
        if (b < a) {
            const t = a;
            a = b;
            b = t;
        }
        return a + Math.random() * (b - a);
    }

    _massFromRadius(r, density) {
        return (+density || 1.0) * (4.0 / 3.0) * Math.PI * r * r * r;
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
        const mi = c && c.maxImpulse;
        const v = +mi;
        return Number.isFinite(v) ? v : 0;
    }

    _relSpeed(payload) {
        const v = payload && payload.relSpeed;
        const x = +v;
        return Number.isFinite(x) ? x : 0;
    }

    _ensureImpactTemplate() {
        const P = this._P;
        const pCfg = this.cfg && this.cfg.impact && this.cfg.impact.particles;
        if (!P || !pCfg || pCfg.enabled === false) return;

        const tpl = (pCfg.template && String(pCfg.template)) || "";
        if (!tpl) return;

        if (this._impactTemplateReady && this._impactTemplateName === tpl) return;

        if (typeof P.define !== "function") {
            if (this.cfg.debug.impactFx) logW("[shoot] impact template skipped: PARTICLES.define missing");
            return;
        }

        try {
            const cfgCopy = Object.assign({}, pCfg);
            cfgCopy.template = tpl;
            P.define(tpl, cfgCopy);

            this._impactTemplateReady = true;
            this._impactTemplateName = tpl;

            if (this.cfg.debug.impactFx) logD("[shoot] impact particles template defined: " + tpl);
        } catch (e) {
            this._impactTemplateReady = false;
            this._impactTemplateName = "";
            if (this.cfg.debug.impactFx) logW("[shoot] define(template) failed: " + (e && e.message ? e.message : e));
        }
    }

    _spawnImpactParticles(pos, payload, pCfg) {
        const P = this._P;
        if (!P) return false;

        const burst = (pCfg.burst | 0) || 24;
        const ttlMs = (pCfg.ttlMs | 0) || 900;

        const nrm = this._contactNormal(payload);
        const dir = nrm ? {x: nrm.x, y: nrm.y, z: nrm.z} : null;

        this._ensureImpactTemplate();

        if (this.cfg.debug.logParticles) {
            logD("[shoot] particles spawn attempt: hasSpawn=" + (typeof P.spawn) +
                " tpl=" + (pCfg.template || "") +
                " burst=" + burst + " ttlMs=" + ttlMs +
                " pos=(" + pos.x.toFixed(3) + "," + pos.y.toFixed(3) + "," + pos.z.toFixed(3) + ")" +
                " dir=" + (dir ? ("(" + dir.x.toFixed(3) + "," + dir.y.toFixed(3) + "," + dir.z.toFixed(3) + ")") : "null"));
        }

        if (typeof P.spawn === "function") {
            const tpl = (pCfg.template && String(pCfg.template)) || "";
            if (tpl) {
                P.spawn(tpl, {pos, dir, burst, ttlMs, cfg: pCfg});
            } else {
                P.spawn(Object.assign({}, pCfg), {pos, dir, burst, ttlMs});
            }
            return true;
        }

        // fallback to low-level create path
        if (typeof P.create !== "function") return false;

        const h = P.create(Object.assign({}, pCfg));
        if (this.cfg.debug.logParticles) logD("[shoot] particles create -> " + (h ? "OK" : "NULL"));
        if (!h) return false;

        if (typeof P.setPosition === "function") P.setPosition(h, pos);

        try {
            if (dir && typeof P.configure === "function") {
                const o = Object.assign({}, pCfg);
                o.velocity = Object.assign({}, pCfg.velocity || null, {dir});
                P.configure(h, o);
            }
        } catch (e2) {
        }

        if (typeof P.emit === "function" && burst > 0) P.emit(h, burst);
        else if (typeof P.emitAll === "function") P.emitAll(h);

        if (typeof setTimeout === "function" && typeof P.destroy === "function") {
            setTimeout(() => {
                try {
                    P.destroy(h);
                } catch (e3) {
                }
            }, Math.max(50, ttlMs));
        }

        return true;
    }

    _impactFx(pos, payload, shotSurfaceId, sourceTag) {
        const cfg = this.cfg.impact;
        if (!cfg || !cfg.enabled || !pos) return;

        if (this.cfg.debug.impactFx) {
            logD("[shoot] impact fx [" + sourceTag + "] shotSurfaceId=" + (shotSurfaceId | 0) +
                " impulse=" + this._contactImpulse(payload).toFixed(3) +
                " relSpeed=" + this._relSpeed(payload).toFixed(3) +
                " hardSide=" + (payload && payload.hardSide) +
                " pos=(" + pos.x.toFixed(3) + "," + pos.y.toFixed(3) + "," + pos.z.toFixed(3) + ")");
        }

        // Sound
        try {
            const s = cfg.sound;
            if (s && s.src && ENGINE && ENGINE.sound && typeof ENGINE.sound.create === "function") {
                const n = ENGINE.sound.create({
                    src: String(s.src),
                    volume: s.volume != null ? +s.volume : 0.85,
                    loop: !!s.loop,
                    pos: [pos.x, pos.y, pos.z]
                });
                if (n && typeof n.play === "function") n.play();
            }
        } catch (e) {
            if (this.cfg.debug.impactFx) logW("[shoot] sound failed: " + (e && e.message ? e.message : e));
        }

        // Particles
        try {
            const pCfg = cfg.particles;
            if (!pCfg || pCfg.enabled === false) return;

            const ok = this._spawnImpactParticles(pos, payload, pCfg);
            if (!ok && this.cfg.debug.logParticles) logW("[shoot] particles spawn FAILED: no PARTICLES.spawn/create?");
        } catch (e) {
            if (this.cfg.debug.impactFx) logW("[shoot] particles failed: " + (e && e.message ? e.message : e));
        }

        this._emit(this.cfg.events.hit, {
            surfaceId: shotSurfaceId | 0,
            pos,
            impulse: payload && payload.impulse,
            relSpeed: payload && payload.relSpeed,
            energyApprox: payload && payload.energyApprox,
            hardSide: payload && payload.hardSide,
            normal: this._contactNormal(payload),
            source: sourceTag
        });
    }

    _isMyShotPair(payload) {
        const aS = payload && payload.a && typeof payload.a.surfaceId === "number" ? (payload.a.surfaceId | 0) : 0;
        const bS = payload && payload.b && typeof payload.b.surfaceId === "number" ? (payload.b.surfaceId | 0) : 0;
        if (aS <= 0 || bS <= 0) return 0;

        return this._shotsBySurface[aS] ? aS : (this._shotsBySurface[bS] ? bS : 0);
    }

    _passesJsImpactFilter(payload) {
        const fcfg = this.cfg.impactFilter;
        const imp = this._contactImpulse(payload);
        const rel = this._relSpeed(payload);

        if (imp < +fcfg.minImpulse) return false;
        if (rel > 0 && rel < +fcfg.minRelSpeed) return false;

        return true;
    }

    _onImpact(payload) {
        const shotSurfaceId = this._isMyShotPair(payload);
        if (!shotSurfaceId) return;

        delete this._shotsBySurface[shotSurfaceId];

        const pos = this._contactPoint(payload);
        if (!pos) {
            if (this.cfg.debug.impactFx) logW("[shoot] impact ignored: no contact point (impact event)");
            return;
        }

        this._impactFx(pos, payload, shotSurfaceId, "impact");
    }

    _onCollisionBegin(payload) {
        if (!this.cfg.debug.logCollisions && !this.cfg.debug.impactFx) {
            // Still needed for fallback FX, don't early return.
        }

        const shotSurfaceId = this._isMyShotPair(payload);
        if (!shotSurfaceId) return;

        const pos = this._contactPoint(payload);
        if (!pos) {
            if (this.cfg.debug.logCollisions) logW("[shoot] collision.begin ignored: no contact point");
            return;
        }

        // JS-side filter to avoid micro-contacts spam
        if (!this._passesJsImpactFilter(payload)) {
            if (this.cfg.debug.logCollisions) {
                logD("[shoot] collision.begin filtered: impulse=" + this._contactImpulse(payload).toFixed(3) +
                    " relSpeed=" + this._relSpeed(payload).toFixed(3) +
                    " minImpulse=" + this.cfg.impactFilter.minImpulse +
                    " minRelSpeed=" + this.cfg.impactFilter.minRelSpeed);
            }
            return;
        }

        delete this._shotsBySurface[shotSurfaceId];

        this._impactFx(pos, payload, shotSurfaceId, "collision.begin");
    }

    _fire(frame, ownerBodyId) {
        const c = this.cfg;
        if (!c.enabled || !ownerBodyId) return;

        this._bindPhysicsFx();

        this._readOrigin_into(frame, this._origin);
        this._dirFromYawPitch_into(frame.view.yaw, frame.view.pitch, this._dir);

        const off = c.spawnOffset;
        this._spawn.x = this._origin.x + this._dir.x * off;
        this._spawn.y = this._origin.y + this._dir.y * off;
        this._spawn.z = this._origin.z + this._dir.z * off;

        const r = this._randBetween(c.radiusMin, c.radiusMax);

        let mass = this._massFromRadius(r, c.density);
        if (c.massMin != null && mass < c.massMin) mass = +c.massMin;
        if (c.massMax != null && mass > c.massMax) mass = +c.massMax;

        if (!ENGINE || !ENGINE.mesh || typeof ENGINE.mesh.sphere$ !== "function") throw new Error("[shoot] ENGINE.mesh.sphere$ required");
        if (!MAT || typeof MAT.getMaterial !== "function") throw new Error("[shoot] MAT.getMaterial required");

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

        if (!g || typeof g.velocity !== "function") throw new Error("[shoot] projectile handle must support velocity(v)");
        g.velocity(this._vel);

        const surfaceId = idOfSurfaceHandle(g);
        if (surfaceId > 0) {
            this._shotsBySurface[surfaceId] = 1;
            if (c.debug.logShots) {
                logD("[shoot] fired " + name +
                    " surfaceId=" + surfaceId +
                    " r=" + r.toFixed(3) +
                    " m=" + mass.toFixed(2) +
                    " spawn=(" + this._spawn.x.toFixed(3) + "," + this._spawn.y.toFixed(3) + "," + this._spawn.z.toFixed(3) + ")" +
                    " vel=(" + this._vel.x.toFixed(3) + "," + this._vel.y.toFixed(3) + "," + this._vel.z.toFixed(3) + ")");
            }
        } else {
            logW("[shoot] fired but no surfaceId on handle (cannot track impact)");
        }

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
    }
}

module.exports = ShootSystem;