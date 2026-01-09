"use strict";

// FILE: kalitech/engine/bootstrap/EngineProxy.js
// Author: KΛYLΛ

/**
 * ENGINE Proxy for Graal host EngineApiImpl:
 *  - no globals
 *  - safe dynamic module storage inside ENGINE.modules (no collisions with host methods)
 *  - optional short aliases ENGINE.<key> only if host doesn't already have that key
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

    function setModule(key, value) {
        key = String(key);
        if (modules[key]) throw new Error("[ENGINE] duplicate module key '" + key + "'");
        modules[key] = value;

        // only create short alias if it doesn't collide with host
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

    return new Proxy(javaEngine, {
        has(target, prop) {
            if (prop in store) return true;
            if (prop in modules) return true;
            return prop in target;
        },

        get(target, prop) {
            if (prop in store) return store[prop];
            if (prop in modules) return modules[prop];

            const v = target[prop];
            if (typeof v === "function") return v.bind(target);
            return v;
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
            return Object.getOwnPropertyDescriptor(target, prop);
        }
    });
}

module.exports = {createEngineProxy};