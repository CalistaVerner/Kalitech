// FILE: resources/kalitech/builtin/Events.js
// Author: KΛYLΛ (patched by Kalitech)
// "native-first" wrapper for Java EventsApiImpl (token-based)

"use strict";

/**
 * Events builtin (OOP wrapper).
 *
 * Contract:
 *   module.exports(engine, K) => api
 *   module.exports.META = { name, globalName, version, description, engineMin }
 *
 * API:
 *   EVENTS.on(topic, fn) -> offFn                 // token-based under the hood
 *   EVENTS.once(topic, fn) -> offFn
 *   EVENTS.off(topic, fn) -> boolean             // classic (only if native supports)
 *   EVENTS.offToken(token, topic?) -> boolean     // preferred
 *   EVENTS.emit(topic, payload) -> boolean
 *   EVENTS.scope("player").on("move", fn) => listens "player.move"
 *   EVENTS.enabled() -> boolean
 */

function _isFn(x) { return typeof x === "function"; }
function _isPlainObj(x) {
    if (!x || typeof x !== "object") return false;
    const p = Object.getPrototypeOf(x);
    return p === Object.prototype || p === null;
}

function _isJsonValue(x, depth = 0) {
    if (depth > 24) return false;
    if (x == null) return true;
    const t = typeof x;
    if (t === "string" || t === "number" || t === "boolean") return true;
    if (Array.isArray(x)) return x.every((v) => _isJsonValue(v, depth + 1));
    if (_isPlainObj(x)) {
        for (const k of Object.keys(x)) {
            if (!_isJsonValue(x[k], depth + 1)) return false;
        }
        return true;
    }
    return false;
}

function _typeOfValue(v) {
    if (v == null) return "null";
    if (Array.isArray(v)) return "array";
    return typeof v;
}

function _safeCall(fn, fb) {
    try { return fn(); } catch (_) { return fb; }
}

/**
 * Resolve the canonical engine event bus.
 */
function _getBus(engine) {
    if (!engine) return null;
    return _safeCall(() => (_isFn(engine.bus) ? engine.bus() : null), null);
}

function _busOn(bus, topic, fn) {
    if (!bus) return 0;

    if (_isFn(bus.on)) return (bus.on(topic, fn) | 0);
    if (_isFn(bus.addListener)) return (bus.addListener(topic, fn) | 0);
    if (_isFn(bus.addEventListener)) return (bus.addEventListener(topic, fn) | 0);
    if (_isFn(bus.subscribe)) return (bus.subscribe(topic, fn) | 0);

    return 0;
}

/**
 * Token-based off that works with Java overloads:
 *  - off(int token)
 *  - off(String topic, int token)
 */
function _busOffToken(bus, token, topicMaybe) {
    if (!bus) return false;
    const tok = token | 0;
    if (!tok) return false;

    // Prefer off(token)
    if (_isFn(bus.off)) {
        try {
            // If Java has off(int), Graal will select it.
            const r = bus.off(tok);
            return (r === undefined) ? true : !!r;
        } catch (_) {
            // Try off(topic, token)
            if (topicMaybe != null) {
                try {
                    const r2 = bus.off(String(topicMaybe || ""), tok);
                    return (r2 === undefined) ? true : !!r2;
                } catch (_) {}
            }
        }
    }

    // Other buses
    if (_isFn(bus.offToken)) { try { return !!bus.offToken(tok); } catch (_) {} }
    if (_isFn(bus.unsubscribe)) { try { bus.unsubscribe(tok); return true; } catch (_) {} }

    return false;
}

function _busOffClassic(bus, topic, fn) {
    if (!bus) return false;
    if (!_isFn(fn)) return false;

    if (_isFn(bus.off)) {
        try { bus.off(topic, fn); return true; } catch (_) {}
    }
    if (_isFn(bus.removeListener)) {
        try { bus.removeListener(topic, fn); return true; } catch (_) {}
    }
    if (_isFn(bus.removeEventListener)) {
        try { bus.removeEventListener(topic, fn); return true; } catch (_) {}
    }
    if (_isFn(bus.unsubscribe)) {
        try { bus.unsubscribe(topic, fn); return true; } catch (_) {}
    }
    return false;
}

function _busEmit(bus, topic, payload) {
    if (!bus) return false;

    // Java EventsApiImpl likely has emit(topic, payload)
    if (_isFn(bus.emit)) {
        try { bus.emit(topic, payload); return true; } catch (_) {}
    }
    if (_isFn(bus.publish)) {
        try { bus.publish(topic, payload); return true; } catch (_) {}
    }
    if (_isFn(bus.dispatch)) {
        try { bus.dispatch(topic, payload); return true; } catch (_) {}
    }
    return false;
}

class EventsApi {
    constructor(engine, K) {
        this.engineRef = engine;
        this.K = K || (globalThis.__kalitech || Object.create(null));
        this._bus = null;
        this._defaultSeparator = ".";
        this._throwIfNoBus = true;
        this._validate = !!(this.K && this.K.config && this.K.config.dev);
        this._schemas = Object.create(null);

        // Re-resolve bus if missing (early boot)
        this._lastResolveAt = 0;
        this._resolveCooldownMs = 50;
    }

    _resolveBus(force) {
        const now = (Date.now ? Date.now() : 0);
        if (!force && this._bus) return this._bus;

        if (!force && now && (now - this._lastResolveAt) < this._resolveCooldownMs) {
            return this._bus;
        }
        this._lastResolveAt = now;

        const b = _getBus(this.engineRef);
        if (b) this._bus = b;
        return this._bus;
    }

    bus() { return this._resolveBus(false); }
    enabled() { return !!this.bus(); }

    configure(cfg) {
        cfg = (cfg && typeof cfg === "object") ? cfg : {};
        if (cfg.separator != null) this._defaultSeparator = String(cfg.separator);
        if (cfg.throwIfNoBus != null) this._throwIfNoBus = !!cfg.throwIfNoBus;
        if (cfg.validate != null) this._validate = !!cfg.validate;
        if (cfg.resolveCooldownMs != null) this._resolveCooldownMs = Math.max(0, cfg.resolveCooldownMs | 0);
        return this;
    }

    _needBus() {
        const b = this._resolveBus(false);
        if (!b && this._throwIfNoBus) throw new Error("[EVENTS] bus is not available (yet)");
        return b;
    }

    on(topic, handler) {
        const t = String(topic || "");
        if (!t) throw new Error("[EVENTS] topic is required");
        if (!_isFn(handler)) throw new Error("[EVENTS] handler must be a function");

        const bus = this._needBus();
        if (!bus) return function offNoop(){ return false; };

        const token = _busOn(bus, t, handler);

        // return offFn (JS-friendly), but actually removes token in Java
        return () => _busOffToken(bus, token, t);
    }

    once(topic, handler) {
        const t = String(topic || "");
        if (!t) throw new Error("[EVENTS] topic is required");
        if (!_isFn(handler)) throw new Error("[EVENTS] handler must be a function");

        let offFn = null;
        const wrapped = (data) => {
            try { if (offFn) offFn(); } catch (_) {}
            return handler(data);
        };

        offFn = this.on(t, wrapped);
        return offFn;
    }

    // classic off(topic, fn) — only works if native supports it
    off(topic, handler) {
        const t = String(topic || "");
        if (!t) return false;
        if (!_isFn(handler)) return false;

        const bus = this.bus();
        if (!bus) return false;

        return _busOffClassic(bus, t, handler);
    }

    // preferred: token-based off
    offToken(token, topicMaybe) {
        const bus = this.bus();
        if (!bus) return false;
        return _busOffToken(bus, token, topicMaybe);
    }

    emit(topic, payload) {
        const t = String(topic || "");
        if (!t) throw new Error("[EVENTS] topic is required");

        const bus = this._needBus();
        if (!bus) return false;

        return _busEmit(bus, t, payload);
    }

    register(def) {
        def = (def && typeof def === "object") ? def : {};
        const id = String(def.id || "").trim();
        if (!id) throw new Error("[EVENTS] schema id is required");
        const version = def.version != null ? String(def.version) : "1.0.0";
        const schema = _isPlainObj(def.schema) ? def.schema : Object.create(null);
        const delivery = def.delivery || def.frequency || "at-most-once";
        const order = def.order || "none";
        this._schemas[id] = {id, version, schema, delivery, order};
        return this._schemas[id];
    }

    schema(id) {
        return this._schemas[String(id || "")];
    }

    evt(id, payload, meta) {
        const topic = String(id || "");
        if (!topic) throw new Error("[EVENTS] evt id is required");

        const def = this._schemas[topic];
        if (this._validate) {
            if (!def) {
                throw new Error("[EVENTS] missing schema for event: " + topic);
            }
            if (!_isJsonValue(payload)) {
                throw new Error("[EVENTS] event payload must be JSON-safe (no host objects): " + topic);
            }
            const schema = def.schema || Object.create(null);
            if (_isPlainObj(schema)) {
                for (const key of Object.keys(schema)) {
                    const rule = schema[key];
                    const expected = _isPlainObj(rule) ? String(rule.type || "any") : String(rule || "any");
                    const optional = _isPlainObj(rule) ? !!rule.optional : false;
                    const actual = _typeOfValue(payload ? payload[key] : undefined);
                    if (actual === "undefined") {
                        if (!optional) throw new Error("[EVENTS] missing field '" + key + "' in " + topic);
                        continue;
                    }
                    if (expected !== "any" && expected !== actual) {
                        throw new Error("[EVENTS] field '" + key + "' type mismatch in " + topic +
                            " (expected " + expected + ", got " + actual + ")");
                    }
                }
            }
        }

        const bus = this._needBus();
        if (!bus) return false;

        const metaObj = meta || null;
        if (_isFn(bus.emitEvent)) {
            try { bus.emitEvent(topic, payload, metaObj); return true; } catch (_) {}
        }
        return _busEmit(bus, topic, payload);
    }

    scope(scopeName, separator) {
        const scope = String(scopeName || "").trim();
        const sep = (separator == null ? this._defaultSeparator : String(separator));
        const prefix = scope ? (scope + sep) : "";

        const self = this;
        return Object.freeze({
            scope,
            on: (topic, handler) => self.on(prefix + String(topic || ""), handler),
            once: (topic, handler) => self.once(prefix + String(topic || ""), handler),
            off: (topic, handler) => self.off(prefix + String(topic || ""), handler),
            emit: (topic, payload) => self.emit(prefix + String(topic || ""), payload),
            evt: (topic, payload, meta) => self.evt(prefix + String(topic || ""), payload, meta),
            offToken: (token, topicMaybe) => self.offToken(token, topicMaybe != null ? (prefix + String(topicMaybe)) : null)
        });
    }

    child(scopeName, separator) { return this.scope(scopeName, separator); }
}

// factory(engine, K) => api
function create(engine, K) {
    if (!engine) throw new Error("[EVENTS] engine is required");
    return new EventsApi(engine, K);
}

create.META = {
    moduleId: "events",
    id: "events",
    globalName: "EVENTS",
    version: "2.0.0",
    description: "Event bus v2: schema-aware evt() with optional validation and JSON-only payloads.",
    engineMin: "0.1.0",
    changelog: [
        "2.0.0: added schema registry + evt() with dev-mode validation, JSON-only payload enforcement."
    ],
    deprecation: {
        status: "active",
        policy: "Breaking changes require major bump."
    }
};

module.exports = create;
