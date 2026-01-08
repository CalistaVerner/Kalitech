// FILE: resources/kalitech/engine/bootstrap/Deferred.js
"use strict";

function createDeferredProxy(resolveFn, label) {
    const state = {resolved: null};

    function ensureResolved() {
        if (state.resolved) return state.resolved;
        const api = resolveFn();
        if (api) state.resolved = api;
        return state.resolved;
    }

    function makeChain(steps) {
        return new Proxy(function () {
        }, {
            get(_t, prop) {
                if (prop === "__isDeferred") return true;
                if (prop === "__label") return label;
                if (prop === "then") return undefined;
                return makeChain(steps.concat([{type: "get", key: prop}]));
            },
            apply(_t, _thisArg, args) {
                return makeChain(steps.concat([{type: "call", args: args || []}]));
            }
        });
    }

    return new Proxy(Object.create(null), {
        get(_t, prop) {
            const api = ensureResolved();
            if (api) {
                const v = api[prop];
                if (typeof v === "function") return v.bind(api);
                return v;
            }
            return makeChain([{type: "get", key: prop}]);
        }
    });
}

module.exports = {createDeferredProxy};