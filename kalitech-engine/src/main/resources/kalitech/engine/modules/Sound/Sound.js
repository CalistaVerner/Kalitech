// FILE: resources/kalitech/builtin/Sound.js
// Author: Calista Verner
"use strict";

function s(v) {
    return String(v == null ? "" : v);
}

function isObj(v) {
    return v != null && typeof v === "object";
}

function getLog(engine) {
    try {
        if (engine && typeof engine.log === "function") {
            const l = engine.log();
            if (l && typeof l.error === "function") return l;
        }
    } catch (_) {
    }
    try {
        if (globalThis.ENGINE && typeof ENGINE.log === "function") {
            const l = ENGINE.log();
            if (l && typeof l.error === "function") return l;
        }
    } catch (_) {
    }

    const c = console || Object.create(null);
    return {
        error: (m, e) => (c.error ? c.error(m, e) : void 0),
        warn: (m, e) => (c.warn ? c.warn(m, e) : void 0),
        info: (m, e) => (c.info ? c.info(m, e) : void 0)
    };
}

function v3(v, a, b) {
    if (Array.isArray(v)) return [Number(v[0]) || 0, Number(v[1]) || 0, Number(v[2]) || 0];
    if (isObj(v)) return [Number(v.x) || 0, Number(v.y) || 0, Number(v.z) || 0];
    return [Number(v) || 0, Number(a) || 0, Number(b) || 0];
}

function safeExec(log, label, fn) {
    try {
        return fn();
    } catch (e) {
        log.error("[SND] " + label + " failed", e);
        throw e;
    }
}

class SoundInstance {
    constructor(engine, node, api, log) {
        this._node = node;
        this._api = api;
        this._log = log;
    }

    __node() {
        return this._node;
    }

    play() {
        safeExec(this._log, "play", () => this._api.play(this._node));
        return this;
    }

    stop() {
        safeExec(this._log, "stop", () => this._api.stop(this._node));
        return this;
    }

    pause() {
        if (this._api.pause) safeExec(this._log, "pause", () => this._api.pause(this._node));
        return this;
    }

    volume(v) {
        safeExec(this._log, "volume", () => this._api.setVolume(this._node, Math.max(0, Number(v))));
        return this;
    }

    pitch(v) {
        const pv = Math.min(Math.max(Number(v), 0.5), 2.0);
        safeExec(this._log, "pitch", () => this._api.setPitch(this._node, pv));
        return this;
    }

    loop(v = true) {
        safeExec(this._log, "loop", () => this._api.setLooping(this._node, !!v));
        return this;
    }

    pos(x, y, z) {
        const p = v3(x, y, z);
        safeExec(this._log, "pos", () => this._api.setPosition(this._node, p[0], p[1], p[2]));
        return this;
    }

    positional(v = true) {
        if (this._api.setPositional) safeExec(this._log, "positional", () => this._api.setPositional(this._node, !!v));
        return this;
    }

    maxDistance(v) {
        if (this._api.setMaxDistance) safeExec(this._log, "maxDistance", () => this._api.setMaxDistance(this._node, Number(v)));
        return this;
    }

    refDistance(v) {
        if (this._api.setRefDistance) safeExec(this._log, "refDistance", () => this._api.setRefDistance(this._node, Number(v)));
        return this;
    }

    reverb(v = true) {
        safeExec(this._log, "reverb", () => this._api.setReverbEnabled(this._node, !!v));
        return this;
    }

    directional(v = true) {
        safeExec(this._log, "directional", () => this._api.setDirectional(this._node, !!v));
        return this;
    }

    innerAngle(v) {
        if (this._api.setInnerAngle) safeExec(this._log, "innerAngle", () => this._api.setInnerAngle(this._node, Number(v)));
        return this;
    }

    outerAngle(v) {
        if (this._api.setOuterAngle) safeExec(this._log, "outerAngle", () => this._api.setOuterAngle(this._node, Number(v)));
        return this;
    }

    direction(x, y, z) {
        if (!this._api.setDirection) return this;
        const d = v3(x, y, z);
        safeExec(this._log, "direction", () => this._api.setDirection(this._node, d[0], d[1], d[2]));
        return this;
    }

    velocity(x, y, z) {
        if (!this._api.setVelocity) return this;
        const vv = v3(x, y, z);
        safeExec(this._log, "velocity", () => this._api.setVelocity(this._node, vv[0], vv[1], vv[2]));
        return this;
    }

    velocityFromTranslation(v = true) {
        if (this._api.setVelocityFromTranslation) {
            safeExec(this._log, "velocityFromTranslation", () => this._api.setVelocityFromTranslation(this._node, !!v));
        }
        return this;
    }
}

class SoundObject {
    constructor(registry, mode, base) {
        this._r = registry;
        this._mode = mode; // "event" | "file"
        this._event = mode === "event" ? String(base || "") : null;
        this._fileCfg = mode === "file" ? (base || {}) : null;

        this._deterministic = null;
        this._seed = null;
        this._positional = null;

        this._context = {entityId: 0, surfaceId: 0, seq: 0, tick: 0, slot: 0};
        this._overrides = null;

        this._autoSeq = 0;
        this._autoSeqEnabled = true;
        this._seqMode = "increment"; // "increment" | "keep"
    }

    setDeterministic(v = true) {
        this._deterministic = !!v;
        return this;
    }

    setSeed(seed) {
        this._seed = Number(seed) || 0;
        return this;
    }

    setPositional(v = true) {
        this._positional = !!v;
        return this;
    }

    setOverrides(overrides) {
        this._overrides = overrides || null;
        return this;
    }

    setContext(ctx) {
        if (ctx && typeof ctx === "object") {
            if (ctx.entityId != null) this._context.entityId = Number(ctx.entityId) || 0;
            if (ctx.surfaceId != null) this._context.surfaceId = Number(ctx.surfaceId) || 0;
            if (ctx.seq != null) this._context.seq = Number(ctx.seq) || 0;
            if (ctx.tick != null) this._context.tick = Number(ctx.tick) || 0;
            if (ctx.slot != null) this._context.slot = Number(ctx.slot) || 0;
        }
        return this;
    }

    setEntityId(id) {
        this._context.entityId = Number(id) || 0;
        return this;
    }

    setSurfaceId(id) {
        this._context.surfaceId = Number(id) || 0;
        return this;
    }

    setTick(tick) {
        this._context.tick = Number(tick) || 0;
        return this;
    }

    setSlot(slot) {
        this._context.slot = Number(slot) || 0;
        return this;
    }

    enableAutoSeq(v = true) {
        this._autoSeqEnabled = !!v;
        return this;
    }

    setSeqMode(mode) {
        const m = String(mode || "");
        this._seqMode = (m === "keep") ? "keep" : "increment";
        return this;
    }

    setSeq(seq) {
        this._context.seq = Number(seq) || 0;
        return this;
    }

    nextSeq() {
        this._autoSeq++;
        this._context.seq = this._autoSeq;
        return this._context.seq;
    }

    _buildCfgForPlay() {
        if (this._mode === "event") {
            const cfg = {event: this._event};

            if (this._deterministic != null) cfg.deterministic = !!this._deterministic;
            if (this._seed != null) cfg.seed = Number(this._seed) || 0;

            cfg.context = {
                entityId: this._context.entityId,
                surfaceId: this._context.surfaceId,
                seq: this._context.seq,
                tick: this._context.tick,
                slot: this._context.slot
            };

            let ov = null;

            if (this._overrides && typeof this._overrides === "object") {
                ov = Object.assign({}, this._overrides);
            }

            if (this._positional != null) {
                ov = ov || {};
                ov.is3D = !!this._positional;
            }

            if (ov) cfg.overrides = ov;
            return cfg;
        }

        const cfg = Object.assign({}, this._fileCfg);
        if (this._positional != null) cfg.is3D = !!this._positional;

        if (this._overrides && typeof this._overrides === "object") {
            Object.assign(cfg, this._overrides);
        }

        return cfg;
    }

    play() {
        if (this._mode === "event") {
            if (this._autoSeqEnabled) {
                if (this._seqMode === "increment") {
                    this.nextSeq();
                } else if (!this._context.seq) {
                    this.nextSeq();
                }
            }
        }
        return this._r.playSound(this._buildCfgForPlay());
    }
}

class SoundRegistry {
    constructor(engine, K) {
        this.engine = engine;
        this.K = K || (globalThis.__kalitech || Object.create(null));

        this._log = getLog(engine);

        this._bankLoaded = false;
        this._bankLoadAttempted = false;

        this._bankPath = "data/sounds.json";
        this._tryAutoLoadBank();
    }

    api() {
        const soundApi = this.engine.sound && this.engine.sound();
        if (!soundApi || typeof soundApi.create !== "function") {
            throw new Error("[SND] engine.sound().create(cfg) is required");
        }
        return soundApi;
    }

    _tryAutoLoadBank() {
        if (this._bankLoaded) return true;
        if (this._bankLoadAttempted) return false;

        this._bankLoadAttempted = true;

        const soundApi = this.api();
        if (typeof soundApi.loadBank !== "function") return false;

        try {
            const txt = ENGINE.assets().readText(this._bankPath);
            const obj = JSON.parse(txt);
            soundApi.loadBank(obj);
        } catch (e) {
            this._log.error("[SND] bank autoload failed: " + this._bankPath, e);
            return false;
        }

        this._bankLoaded = true;
        return true;
    }

    _ensureBankLoaded() {
        if (this._bankLoaded) return true;
        this._tryAutoLoadBank();
        return this._bankLoaded;
    }

    setSeed(seed) {
        const api = this.api();
        if (!api.setSeed) throw new Error("[SND] engine.sound().setSeed(seed) is required");
        safeExec(this._log, "setSeed", () => api.setSeed(Number(seed) || 0));
        return this;
    }

    setDeterministic(v = true) {
        const api = this.api();
        if (!api.setDeterministic) throw new Error("[SND] engine.sound().setDeterministic(bool) is required");
        safeExec(this._log, "setDeterministic", () => api.setDeterministic(!!v));
        return this;
    }

    create(cfg) {
        const api = this.api();
        const node = safeExec(this._log, "create", () => api.create(cfg));
        return new SoundInstance(this.engine, node, api, this._log);
    }

    createAndPlay(cfg) {
        const s0 = this.create(cfg);
        s0.play();
        return s0;
    }

    loadBank(bankObj) {
        const api = this.api();
        if (typeof api.loadBank !== "function") {
            throw new Error("[SND] engine.sound().loadBank(bankObj) is required for event sound bank");
        }
        safeExec(this._log, "loadBank", () => api.loadBank(bankObj));
        this._bankLoaded = true;
        this._bankLoadAttempted = true;
        return this;
    }

    clearBank() {
        const api = this.api();
        if (typeof api.clearBank === "function") safeExec(this._log, "clearBank", () => api.clearBank());
        this._bankLoaded = false;
        this._bankLoadAttempted = false;
        return this;
    }

    listEvents() {
        this._ensureBankLoaded();
        const api = this.api();
        if (typeof api.listEvents !== "function") return [];
        return safeExec(this._log, "listEvents", () => api.listEvents());
    }

    getSound(eventKey) {
        this._ensureBankLoaded();
        return new SoundObject(this, "event", eventKey);
    }

    getSoundFile(srcOrCfg) {
        const cfg = (typeof srcOrCfg === "string") ? {src: srcOrCfg} : (srcOrCfg || {});
        return new SoundObject(this, "file", cfg);
    }

    playSound(cfg) {
        const api = this.api();
        if (!cfg || typeof cfg !== "object") {
            throw new Error("[SND] playSound(cfg): cfg object is required");
        }

        const hasEvent = typeof cfg.event === "string" && cfg.event.length > 0;
        const hasSrc = typeof cfg.src === "string" && cfg.src.length > 0;

        if (!hasEvent && !hasSrc) {
            throw new Error("[SND] playSound(cfg): 'event' or 'src' is required");
        }

        if (hasEvent) {
            this._ensureBankLoaded();
            if (!api.playEventCfg) {
                throw new Error("[SND] engine.sound().playEventCfg(cfg) is required for event sounds");
            }
            const ecfg = this._normalizeEventCfg(cfg);
            const node = safeExec(this._log, "playEventCfg", () => api.playEventCfg(ecfg));
            return new SoundInstance(this.engine, node, api, this._log);
        }

        const scfg = this._normalizeSrcCfg(cfg);
        const node = safeExec(this._log, "create", () => api.create(scfg));
        safeExec(this._log, "play", () => api.play(node));
        return new SoundInstance(this.engine, node, api, this._log);
    }

    _normalizeEventCfg(cfg) {
        const out = {event: s(cfg.event)};

        if (cfg.deterministic != null) out.deterministic = !!cfg.deterministic;
        if (cfg.seed != null) out.seed = Number(cfg.seed) || 0;

        if (cfg.context && typeof cfg.context === "object") {
            const c = cfg.context;
            out.context = {
                entityId: Number(c.entityId) || 0,
                surfaceId: Number(c.surfaceId) || 0,
                seq: Number(c.seq) || 0,
                tick: Number(c.tick) || 0,
                slot: Number(c.slot) || 0
            };
        }

        let ov = null;

        if (cfg.overrides && typeof cfg.overrides === "object") {
            ov = Object.assign({}, cfg.overrides);
        }

        if (cfg.is3D != null) {
            ov = ov || {};
            ov.is3D = !!cfg.is3D;
        }
        if (cfg.volume != null) {
            ov = ov || {};
            ov.volume = cfg.volume;
        }
        if (cfg.pitch != null) {
            ov = ov || {};
            ov.pitch = cfg.pitch;
        }
        if (cfg.looping != null) {
            ov = ov || {};
            ov.looping = !!cfg.looping;
        }

        if (cfg.pos != null || cfg.position != null || cfg.x != null || cfg.y != null || cfg.z != null) {
            const p = cfg.pos != null ? cfg.pos : (cfg.position != null ? cfg.position : {
                x: cfg.x,
                y: cfg.y,
                z: cfg.z
            });
            const vv = v3(p, cfg.y, cfg.z);
            ov = ov || {};
            ov.x = vv[0];
            ov.y = vv[1];
            ov.z = vv[2];
        }

        if (ov) out.overrides = ov;
        return out;
    }

    _normalizeSrcCfg(cfg) {
        const out = {src: s(cfg.src)};

        if (cfg.type != null) out.type = s(cfg.type);
        if (cfg.is3D != null) out.is3D = !!cfg.is3D;
        if (cfg.looping != null) out.looping = !!cfg.looping;
        if (cfg.volume != null) out.volume = cfg.volume;
        if (cfg.pitch != null) out.pitch = cfg.pitch;

        if (cfg.pos != null || cfg.position != null || cfg.x != null || cfg.y != null || cfg.z != null) {
            const p = cfg.pos != null ? cfg.pos : (cfg.position != null ? cfg.position : {
                x: cfg.x,
                y: cfg.y,
                z: cfg.z
            });
            const vv = v3(p, cfg.y, cfg.z);
            out.x = vv[0];
            out.y = vv[1];
            out.z = vv[2];
        }

        return out;
    }
}

function create(engine, K) {
    if (!engine) throw new Error("[SND] engine is required");
    return new SoundRegistry(engine, K);
}

create.META = {
    moduleId: "sound",
    globalName: "SND",
    version: "1.3.0",
    description: "Universal sound facade: playSound(cfg), event bank + src sounds, object-mode getSound/getSoundFile",
    engineMin: "0.1.0"
};

module.exports = create;