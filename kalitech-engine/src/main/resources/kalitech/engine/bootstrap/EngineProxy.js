"use strict";

// FILE: kalitech/engine/bootstrap/EngineProxy.js
// Author: KΛYLΛ

/**
 * ENGINE Proxy for Graal host EngineApi:
 *  - no globals
 *  - safe dynamic module storage inside ENGINE.modules (no collisions with host methods)
 *  - dynamic API access: ENGINE.api("input") and sugar ENGINE.input()
 *
 * Contract:
 *  - Host may export explicit methods (engine.input()), but it is NOT required.
 *  - Host must expose registry-based lookup via either:
 *      - engine.registry().api(id, type?) / engine.registry().api(id)
 *      - or engine.api(id) directly
 *
 * This proxy provides:
 *  - ENGINE.api(id): universal module lookup (host-first, then registry)
 *  - ENGINE.<id>(): sugar for ENGINE.api("<id>")
 */
function createEngineProxy(javaEngine) {
    if (!javaEngine) throw new Error("[bootstrap] createEngineProxy: javaEngine is required");

    const store = Object.create(null);
    const modules = Object.create(null);

    function hostHas(prop) {
        try {
            return prop in javaEngine;
        } catch (_) {
            return false;
        }
    }

    function isSafeKey(prop) {
        // Prevent weird symbol/meta keys from being treated as API ids.
        if (typeof prop !== "string") return false;
        if (prop.length === 0) return false;
        if (prop === "__proto__" || prop === "prototype" || prop === "constructor") return false;
        return true;
    }

    function getHostFn(name) {
        try {
            const v = javaEngine[name];
            return (typeof v === "function") ? v.bind(javaEngine) : null;
        } catch (_) {
            return null;
        }
    }

    // Universal API lookup. Works even if host doesn't provide engine.<id>() methods.
    function api(id) {
        id = String(id);

        // 1) If host has direct method engine[id](), prefer it (explicit host API).
        const direct = getHostFn(id);
        if (direct) return direct();

        // 2) If host has engine.api(id), use it (new host contract).
        const hostApi = getHostFn("api");
        if (hostApi) return hostApi(id);

        // 3) If host has registry(), use registry.api(id)
        const regFn = getHostFn("registry");
        if (regFn) {
            const reg = regFn();
            if (reg) {
                try {
                    // Accept both registry.api(id) and registry.api(id, type) (we only need id here).
                    const ra = reg.api;
                    if (typeof ra === "function") return ra.call(reg, id);
                } catch (_) {
                    // ignore and continue
                }
                try {
                    // fallback to get(id) returning entry with .api
                    const ge = reg.get;
                    if (typeof ge === "function") {
                        const e = ge.call(reg, id);
                        if (e && e.api) return e.api;
                    }
                } catch (_) {
                    // ignore
                }
            }
        }

        // 4) Not found
        return null;
    }

    // Sugar function factory for ENGINE.<id>()
    function apiGetterFn(key) {
        return function () {
            const out = api(key);
            if (!out) throw new Error("[ENGINE] api missing: '" + key + "'");
            return out;
        };
    }

    function setModule(key, value) {
        key = String(key);
        if (modules[key]) throw new Error("[ENGINE] duplicate module key '" + key + "'");
        modules[key] = value;

        // only create short alias if it doesn't collide with host
        // IMPORTANT: short alias is a FUNCTION getter, not the module object itself
        // to avoid conflicts with engine.<id>() style.
        if (!hostHas(key)) store[key] = value;
        return value;
    }

    function getModule(key) {
        return modules[String(key)] || null;
    }

    function hasModule(key) {
        return !!modules[String(key)];
    }

    // Expose helpers on ENGINE itself (stored, not host)
    store.modules = modules;
    store.setModule = setModule;
    store.getModule = getModule;
    store.hasModule = hasModule;
    store.__host__ = javaEngine;

    // Expose universal API method
    store.api = api;

    return new Proxy(javaEngine, {
        has(target, prop) {
            if (prop in store) return true;
            if (prop in modules) return true;
            return prop in target;
        },

        get(target, prop) {
            if (prop in store) return store[prop];
            if (prop in modules) return modules[prop];

            // Prefer host member
            let v;
            try {
                v = target[prop];
            } catch (_) {
                v = undefined;
            }

            // If host provides a function, bind it, BUT:
            // if it's a zero-arg api getter (engine.input()) returning null, fallback to registry-based api().
            if (typeof v === "function" && typeof prop === "string") {
                const bound = v.bind(target);

                // Wrap only for safe keys to avoid breaking random host functions.
                if (prop && prop !== "api" && prop !== "registry" && prop !== "getRegistry") {
                    return function () {
                        const out = bound.apply(target, arguments);
                        if (out !== null && out !== undefined) return out;

                        // fallback to universal API lookup
                        const a = store.api ? store.api(prop) : null;
                        if (a !== null && a !== undefined) return a;

                        return out; // keep original null
                    };
                }

                return bound;
            }

            if (v !== undefined) return v;

            // Dynamic sugar: ENGINE.<id>() => ENGINE.api("<id>")
            if (typeof prop === "string" && prop.length > 0) {
                return function () {
                    const out = store.api(prop);
                    if (!out) throw new Error("[ENGINE] api missing: '" + prop + "'");
                    return out;
                };
            }

            return undefined;
        },

        set(_target, prop, value) {
            // Never write to host.
            // If this key collides with host API, put it ONLY into modules.
            // If it doesn't collide, keep it as a normal dynamic member.
            if (hostHas(prop)) {
                modules[prop] = value;
                return true;
            }
            store[prop] = value;
            return true;
        },

        ownKeys(target) {
            const hostKeys = Reflect.ownKeys(target);
            const storeKeys = Reflect.ownKeys(store);
            const modKeys = Reflect.ownKeys(modules);

            const out = hostKeys.slice();
            for (let i = 0; i < storeKeys.length; i++) if (out.indexOf(storeKeys[i]) < 0) out.push(storeKeys[i]);
            for (let i = 0; i < modKeys.length; i++) if (out.indexOf(modKeys[i]) < 0) out.push(modKeys[i]);
            return out;
        },

        getOwnPropertyDescriptor(target, prop) {
            if (prop in store) {
                return {configurable: true, enumerable: true, writable: true, value: store[prop]};
            }
            if (prop in modules) {
                return {configurable: true, enumerable: true, writable: true, value: modules[prop]};
            }

            // Provide a virtual descriptor for dynamic api getters so tooling doesn't choke.
            if (!(prop in target) && isSafeKey(prop)) {
                return {configurable: true, enumerable: true, writable: false, value: apiGetterFn(prop)};
            }

            return Object.getOwnPropertyDescriptor(target, prop);
        }
    });
}

module.exports = {createEngineProxy};