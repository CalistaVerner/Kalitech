// FILE: resources/kalitech/builtin/Events.js
// Author: Calista Verner
"use strict";

/**
 * Events builtin (OOP wrapper).
 * Contract:
 *   module.exports(engine, K) => api
 *   module.exports.META = { name, globalName, version, description, engineMin }
 *
 * API:
 *   EVENTS.on(topic, fn) -> offFn
 *   EVENTS.once(topic, fn) -> offFn
 *   EVENTS.off(topic, fn) -> boolean
 *   EVENTS.emit(topic, payload) -> boolean
 *   EVENTS.scope("player").on("move", fn) => listens "player.move"
 *   EVENTS.enabled() -> boolean
 */

function _isFn(x) { return typeof x === "function"; }

function _getBus(engine, K) {
    // 1) direct inject: K.bus
    if (K && K.bus) return K.bus;

    // 2) engine.getBus()
    if (engine && _isFn(engine.getBus)) return engine.getBus();

    // 3) engine.bus()
    if (engine && _isFn(engine.bus)) return engine.bus();

    // 4) engine.api().getBus() / engine.api().bus()
    try {
        if (engine && _isFn(engine.api)) {
            const api = engine.api();
            if (api && _isFn(api.getBus)) return api.getBus();
            if (api && _isFn(api.bus)) return api.bus();
        }
    } catch (_) {}

    return null;
}

function _busOn(bus, topic, fn) {
    if (!bus) return null;

    if (_isFn(bus.on)) return bus.on(topic, fn);
    if (_isFn(bus.addListener)) return bus.addListener(topic, fn);
    if (_isFn(bus.addEventListener)) return bus.addEventListener(topic, fn);

    if (_isFn(bus.subscribe)) return bus.subscribe(topic, fn);

    return null;
}

function _busOff(bus, topic, fn, token) {
    if (!bus) return false;

    // token-based unsubscribe
    if (token != null) {
        if (_isFn(bus.unsubscribe)) { try { bus.unsubscribe(token); return true; } catch (_) {} }
        if (_isFn(bus.offToken)) { try { bus.offToken(token); return true; } catch (_) {} }
    }

    // classic off/removeListener
    if (_isFn(bus.off)) { try { bus.off(topic, fn); return true; } catch (_) {} }
    if (_isFn(bus.removeListener)) { try { bus.removeListener(topic, fn); return true; } catch (_) {} }
    if (_isFn(bus.removeEventListener)) { try { bus.removeEventListener(topic, fn); return true; } catch (_) {} }

    // last resort: unsubscribe(topic, fn)
    if (_isFn(bus.unsubscribe)) { try { bus.unsubscribe(topic, fn); return true; } catch (_) {} }

    return false;
}

function _busEmit(bus, topic, payload) {
    if (!bus) return false;

    if (_isFn(bus.emit)) { try { bus.emit(topic, payload); return true; } catch (_) {} }
    if (_isFn(bus.publish)) { try { bus.publish(topic, payload); return true; } catch (_) {} }
    if (_isFn(bus.dispatch)) { try { bus.dispatch(topic, payload); return true; } catch (_) {} }

    return false;
}

class EventsApi {
    constructor(engine, K) {
        this.engineRef = engine;
        this.K = K || (globalThis.__kalitech || Object.create(null));

        this._bus = null;
        this._resolved = false;

        // tuning
        this._defaultSeparator = ".";
        this._throwIfNoBus = true;
    }

    engine() {
        const e = this.engineRef;
        if (!e) throw new Error("[EVENTS] engine not attached");
        return e;
    }

    bus() {
        if (this._resolved) return this._bus;
        this._resolved = true;
        this._bus = _getBus(this.engineRef, this.K);
        return this._bus;
    }

    enabled() {
        return !!this.bus();
    }

    configure(cfg) {
        cfg = (cfg && typeof cfg === "object") ? cfg : {};
        if (cfg.separator != null) this._defaultSeparator = String(cfg.separator);
        if (cfg.throwIfNoBus != null) this._throwIfNoBus = !!cfg.throwIfNoBus;
        return this;
    }

    _needBus() {
        const b = this.bus();
        if (!b && this._throwIfNoBus) throw new Error("[EVENTS] bus is not available");
        return b;
    }

    on(topic, handler) {
        const t = String(topic || "");
        if (!t) throw new Error("[EVENTS] topic is required");
        if (!_isFn(handler)) throw new Error("[EVENTS] handler must be a function");

        const bus = this._needBus();
        if (!bus) return function offNoop() { return false; };

        const token = _busOn(bus, t, handler);

        // universal unsubscribe fn
        return () => _busOff(bus, t, handler, token);
    }

    once(topic, handler) {
        const t = String(topic || "");
        if (!t) throw new Error("[EVENTS] topic is required");
        if (!_isFn(handler)) throw new Error("[EVENTS] handler must be a function");

        const self = this;
        let offFn = null;

        function wrapped(data) {
            try { if (offFn) offFn(); } catch (_) {}
            return handler(data);
        }

        offFn = self.on(t, wrapped);
        return offFn;
    }

    off(topic, handler) {
        const t = String(topic || "");
        if (!t) return false;
        if (!_isFn(handler)) return false;

        const bus = this.bus();
        if (!bus) return false;

        return _busOff(bus, t, handler, null);
    }

    emit(topic, payload) {
        const t = String(topic || "");
        if (!t) throw new Error("[EVENTS] topic is required");

        const bus = this._needBus();
        if (!bus) return false;

        return _busEmit(bus, t, payload);
    }

    scope(scopeName, separator) {
        const scope = String(scopeName || "").trim();
        const sep = (separator == null ? this._defaultSeparator : String(separator));
        const prefix = scope ? (scope + sep) : "";

        const self = this;
        return Object.freeze({
            scope,
            on:   function (topic, handler) { return self.on(prefix + String(topic || ""), handler); },
            once: function (topic, handler) { return self.once(prefix + String(topic || ""), handler); },
            off:  function (topic, handler) { return self.off(prefix + String(topic || ""), handler); },
            emit: function (topic, payload) { return self.emit(prefix + String(topic || ""), payload); }
        });
    }

    child(scopeName, separator) {
        return this.scope(scopeName, separator);
    }
}

// factory(engine, K) => api
function create(engine, K) {
    if (!engine) throw new Error("[EVENTS] engine is required");
    return new EventsApi(engine, K);
}

// META (adult contract)
create.META = {
    name: "events",
    globalName: "EVENTS",
    version: "1.0.0",
    description: "ScriptEventBus wrapper: on/off/once/emit + scoped topics (OOP)",
    engineMin: "0.1.0"
};

module.exports = create;
