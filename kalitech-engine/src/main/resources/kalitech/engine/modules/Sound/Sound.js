// FILE: resources/kalitech/builtin/Sound.js
// Author: Calista Verner
"use strict";

function s(v) {
    return String(v == null ? "" : v);
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

function isObj(v) {
    return v != null && typeof v === "object";
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
        const v3v = v3(x, y, z);
        safeExec(this._log, "velocity", () => this._api.setVelocity(this._node, v3v[0], v3v[1], v3v[2]));
        return this;
    }

    velocityFromTranslation(v = true) {
        if (this._api.setVelocityFromTranslation) {
            safeExec(this._log, "velocityFromTranslation", () => this._api.setVelocityFromTranslation(this._node, !!v));
        }
        return this;
    }
}

class SoundRegistry {
    constructor(engine, K) {
        this.engine = engine;
        this.K = K || (globalThis.__kalitech || Object.create(null));

        this._log = getLog(engine);

        this._bankLoaded = false;
        this._bankLoadAttempted = false;

        this._bankKey = "sounds";
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

    create(cfg) {
        this._ensureBankLoaded();
        const soundApi = this.api();
        const node = safeExec(this._log, "create", () => soundApi.create(cfg));
        return new SoundInstance(this.engine, node, soundApi, this._log);
    }

    createAndPlay(cfg) {
        const s = this.create(cfg);
        s.play();
        return s;
    }

    loadBank(bankObj) {
        const soundApi = this.api();
        if (typeof soundApi.loadBank !== "function") {
            throw new Error("[SND] engine.sound().loadBank(bankObj) is required for event sound bank");
        }
        safeExec(this._log, "loadBank", () => soundApi.loadBank(bankObj));
        this._bankLoaded = true;
        this._bankLoadAttempted = true;
        return this;
    }

    clearBank() {
        const soundApi = this.api();
        if (typeof soundApi.clearBank === "function") safeExec(this._log, "clearBank", () => soundApi.clearBank());
        this._bankLoaded = false;
        this._bankLoadAttempted = false;
        return this;
    }

    listEvents() {
        this._ensureBankLoaded();
        const soundApi = this.api();
        if (typeof soundApi.listEvents !== "function") return [];
        return safeExec(this._log, "listEvents", () => soundApi.listEvents());
    }

    createEvent(eventKey, overrides) {
        this._ensureBankLoaded();
        const soundApi = this.api();
        if (typeof soundApi.createEvent !== "function") {
            throw new Error("[SND] engine.sound().createEvent(eventKey, overrides) is required");
        }
        const node = safeExec(this._log, "createEvent", () => soundApi.createEvent(s(eventKey), overrides || null));
        return new SoundInstance(this.engine, node, soundApi, this._log);
    }

    playEvent(eventKey, overrides) {
        this._ensureBankLoaded();
        const soundApi = this.api();
        if (typeof soundApi.playEvent !== "function") {
            throw new Error("[SND] engine.sound().playEvent(eventKey, overrides) is required");
        }
        const node = safeExec(this._log, "playEvent", () => soundApi.playEvent(s(eventKey), overrides || null));
        return new SoundInstance(this.engine, node, soundApi, this._log);
    }
}

function create(engine, K) {
    if (!engine) throw new Error("[SND] engine is required");
    return new SoundRegistry(engine, K);
}

create.META = {
    moduleId: "sound",
    globalName: "SND",
    version: "1.2.0",
    description: "Sound registry & instances wrapper + event sound bank autoload (hardened)",
    engineMin: "0.1.0"
};

module.exports = create;