// FILE: Scripts/main.js
// Author: KΛYLΛ
//
// Zero-magic entrypoint:
// RuntimeAppState loads this module and asks it for an "app".
// App decides what world to load and how.

"use strict";

// Data-first world descriptor (recommended)
const MainWorldDesc = require("@env");

exports.meta = {
    id: "kalitech.app",
    version: "2.0.0",
    apiMin: "0.1.0",
    name: "Kalitech App Entrypoint"
};

/**
 * Contract (new):
 *   const main = require('Scripts/main.js')
 *   const app  = main.create?.(opts) ?? main
 *   const worldDesc = app.getWorld?.(ctx) ?? app.world ?? legacy(main.world)
 *   build world from worldDesc
 *   app.start?.(ctx)
 */
exports.create = function create(opts) {
    opts = (opts && typeof opts === "object") ? opts : {};

    return {
        meta: exports.meta,

        /**
         * Decide which world descriptor to load.
         * You can route by mode: game/editor/menu/benchmark/etc.
         */
        getWorld(ctx) {
            const mode =
                (opts && opts.mode) ||
                (ctx && ctx.opts && ctx.opts.mode) ||
                "game";

            // WorldDesc module already validates & freezes the descriptor
            return MainWorldDesc.create({mode});
        },

        /** Optional lifecycle: called after world has been built and entities applied. */
        start(ctx) {
            // Put app-level init here (UI bootstrap, subscriptions, etc.)
            // Keep world-specific logic inside systems.
        },

        /** Optional per-frame app update (only if Java calls it in future). */
        update(ctx) {
            // noop
        },

        /** Optional shutdown. */
        stop(reason) {
            // noop
        }
    };
};
