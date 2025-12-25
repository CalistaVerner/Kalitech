// FILE: Scripts/lib/events.js
// Author: Calista Verner
"use strict";

// --- utils ---
function onceFn(fn) {
    let done = false;
    return function (...args) {
        if (done) return;
        done = true;
        return fn.apply(this, args);
    };
}

function debounce(fn, ms) {
    let t = null;
    const delay = (ms | 0);
    return function (...args) {
        if (t) clearTimeout(t);
        t = setTimeout(() => { t = null; fn.apply(this, args); }, delay);
    };
}

// --- EventsApi mirror wrapper ---
// Mirrors Java interface:
//   emit(topic, payload)
//   on(topic, handler) -> token
//   once(topic, handler) -> token
//   off(topic, token) -> boolean
//   clear(topic)
function createEvents(apiOrEngine) {
    // Accept: engine (global) or ctx.api or engine.events()
    const events =
        (apiOrEngine && typeof apiOrEngine.events === "function") ? apiOrEngine.events()
            : (typeof engine !== "undefined" && engine && typeof engine.events === "function") ? engine.events()
                : apiOrEngine;

    if (!events) throw new Error("[events] Events backend not available (engine.events())");

    // token -> { topic, wrapped }
    const map = new Map();
    let nextToken = 1;

    function isCallable(v) {
        // In Graal, Value callable appears as a JS function proxy.
        // We intentionally avoid typeof-function checks on host members elsewhere,
        // but for handlers it’s ok: handler must be callable from JS anyway.
        return typeof v === "function";
    }

    function wrapHandler(topic, token, handler, oneShot) {
        // Wrap to:
        //  - call handler safely
        //  - auto-unsubscribe on oneShot
        return function (payload) {
            if (oneShot) {
                // best-effort: try to remove via backend if it supports off(token)
                // If backend only supports off(topic, token) on Java side, we’ll call our wrapper off().
                try { api.off(topic, token); } catch (_) {}
            }

            try {
                if (!isCallable(handler)) return;
                return handler(payload);
            } catch (e) {
                try {
                    const log = (typeof engine !== "undefined" && engine && engine.log) ? engine.log() : null;
                    if (log && log.debug) log.debug("[events] handler error topic=" + String(topic) + " token=" + String(token) + " err=" + String(e));
                } catch (_) {}
            }
        };
    }

    const api = {
        emit(topic, payload) {
            events.emit(String(topic), payload);
        },

        on(topic, handler) {
            const t = String(topic);
            const token = (nextToken++ | 0);

            const wrapped = wrapHandler(t, token, handler, false);
            map.set(token, { topic: t, wrapped });

            // Backend JS EventsApi (Java) already returns a token — but мы хотим
            // иметь стабильный токен, который гарантированно можем off().
            // Поэтому подписываемся без надежды на backend token, а держим свой.
            events.on(t, wrapped);

            return token;
        },

        once(topic, handler) {
            const t = String(topic);
            const token = (nextToken++ | 0);

            const wrapped = wrapHandler(t, token, handler, true);
            map.set(token, { topic: t, wrapped });

            // Если backend умеет once — используем, но всё равно через wrapped,
            // чтобы гарантировать одинаковое поведение.
            try {
                if (events.once) {
                    events.once(t, wrapped);
                    return token;
                }
            } catch (_) {}

            // fallback: on + auto off inside wrapped
            events.on(t, wrapped);
            return token;
        },

        off(topic, token) {
            const t = String(topic);
            const tk = token | 0;

            const rec = map.get(tk);
            if (!rec) return false;

            // topic mismatch guard (чтобы не снять не то)
            if (rec.topic !== t) return false;

            map.delete(tk);

            // If backend supports off(topic, token) (Java contract) we can't pass our token.
            // So we remove by handler reference using clear+rebind? No.
            // Instead we rely on backend supporting off(topic, handler) OR we store wrapper and call events.off if exists.
            try {
                // Common JS emitter patterns:
                if (events.off) {
                    // some implementations accept (topic, handler)
                    const r = events.off(t, rec.wrapped);
                    return r === undefined ? true : !!r;
                }
            } catch (_) {}

            try {
                // Alternate: removeListener
                if (events.removeListener) {
                    events.removeListener(t, rec.wrapped);
                    return true;
                }
            } catch (_) {}

            // If backend ONLY supports Java token-based off(topic, token),
            // then THIS wrapper must be the canonical API used by scripts,
            // and backend should also accept handler removal. Otherwise it’s impossible
            // to detach a listener created in JS without a backend token.
            return false;
        },

        clear(topic) {
            const t = String(topic);

            // Drop our tokens for this topic
            for (const [tk, rec] of map.entries()) {
                if (rec.topic === t) map.delete(tk);
            }

            // Delegate to backend if available
            events.clear(t);
        },

        // convenience exports
        utils: {
            once: onceFn,
            debounce
        }
    };

    return api;
}

module.exports = {
    createEvents,
    once: onceFn,
    debounce
};