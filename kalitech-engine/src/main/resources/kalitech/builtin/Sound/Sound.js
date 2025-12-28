// FILE: resources/kalitech/builtin/Sound.js
// Author: Calista Verner
"use strict";

/**
 * Sound builtin.
 * Contract:
 *   module.exports(engine, K) => api
 *   module.exports.META = { name, globalName, version, description, engineMin }
 */

class SoundInstance {
    constructor(engine, node, api) {
        this._engine = engine;
        this._node = node;
        this._api = api;
    }

    __node() { return this._node; }

    play() { this._api.play(this._node); return this; }
    stop() { this._api.stop(this._node); return this; }
    pause() { if (this._api.pause) this._api.pause(this._node); return this; }

    volume(v) { if (this._api.setVolume) this._api.setVolume(this._node, Math.max(0, Number(v))); return this; }
    pitch(v) { if (this._api.setPitch) this._api.setPitch(this._node, Math.min(Math.max(Number(v), 0.5), 2.0)); return this; }
    loop(v = true) { if (this._api.setLooping) this._api.setLooping(this._node, !!v); return this; }

    pos(x, y, z) {
        if (!this._api.setPosition) return this;
        let px, py, pz;
        if (Array.isArray(x)) { px = x[0]; py = x[1]; pz = x[2]; }
        else if (x && typeof x === "object") { px = x.x; py = x.y; pz = x.z; }
        else { px = x; py = y; pz = z; }
        this._api.setPosition(this._node, Number(px) || 0, Number(py) || 0, Number(pz) || 0);
        return this;
    }

    positional(v = true) { if (this._api.setPositional) this._api.setPositional(this._node, !!v); return this; }
    maxDistance(v) { if (this._api.setMaxDistance) this._api.setMaxDistance(this._node, Number(v)); return this; }
    refDistance(v) { if (this._api.setRefDistance) this._api.setRefDistance(this._node, Number(v)); return this; }

    reverb(v = true) { if (this._api.setReverbEnabled) this._api.setReverbEnabled(this._node, !!v); return this; }

    directional(v = true) { if (this._api.setDirectional) this._api.setDirectional(this._node, !!v); return this; }
    innerAngle(v) { if (this._api.setInnerAngle) this._api.setInnerAngle(this._node, Number(v)); return this; }
    outerAngle(v) { if (this._api.setOuterAngle) this._api.setOuterAngle(this._node, Number(v)); return this; }

    direction(x, y, z) {
        if (!this._api.setDirection) return this;
        let dx, dy, dz;
        if (Array.isArray(x)) { dx = x[0]; dy = x[1]; dz = x[2]; }
        else if (x && typeof x === "object") { dx = x.x; dy = x.y; dz = x.z; }
        else { dx = x; dy = y; dz = z; }
        this._api.setDirection(this._node, Number(dx) || 0, Number(dy) || 0, Number(dz) || 0);
        return this;
    }

    velocity(x, y, z) {
        if (!this._api.setVelocity) return this;
        let vx, vy, vz;
        if (Array.isArray(x)) { vx = x[0]; vy = x[1]; vz = x[2]; }
        else if (x && typeof x === "object") { vx = x.x; vy = x.y; vz = x.z; }
        else { vx = x; vy = y; vz = z; }
        this._api.setVelocity(this._node, Number(vx) || 0, Number(vy) || 0, Number(vz) || 0);
        return this;
    }

    velocityFromTranslation(v = true) {
        if (this._api.setVelocityFromTranslation) this._api.setVelocityFromTranslation(this._node, !!v);
        return this;
    }
}

class SoundRegistry {
    constructor(engine, K) {
        this.engine = engine;
        this.K = K || (globalThis.__kalitech || Object.create(null));
    }

    api() {
        const soundApi = this.engine.sound && this.engine.sound();
        if (!soundApi || typeof soundApi.create !== "function") {
            throw new Error("[SND] engine.sound().create(cfg) is required");
        }
        return soundApi;
    }

    create(cfg) {
        const soundApi = this.api();
        const node = soundApi.create(cfg);
        return new SoundInstance(this.engine, node, soundApi);
    }

    createAndPlay(cfg) {
        const s = this.create(cfg);
        s.play();
        return s;
    }
}

// factory(engine, K) => api
function create(engine, K) {
    if (!engine) throw new Error("[SND] engine is required");
    return new SoundRegistry(engine, K);
}

// META (adult contract)
create.META = {
    name: "sound",
    globalName: "SND",
    version: "1.0.0",
    description: "Sound registry & instances wrapper (positional, reverb, direction, velocity)",
    engineMin: "0.1.0"
};

module.exports = create;