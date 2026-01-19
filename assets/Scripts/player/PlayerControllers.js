"use strict";

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function isObj(v) {
    return !!v && typeof v === "object" && !Array.isArray(v);
}

function asInt(v, fb) {
    const n = (v | 0);
    return n ? n : (fb | 0);
}

function asStr(v, fb) {
    const s = (v == null) ? "" : String(v);
    return s ? s : String(fb || "");
}

function normalizeEntry(e) {
    if (!isObj(e)) throw new Error("[PlayerControllers] controllers[] entry must be an object");
    const id = asStr(e.id, "");
    const modulePath = asStr(e.module, "");
    const exportName = (e.export != null) ? asStr(e.export, "") : "";
    const enabled = (e.enabled !== undefined) ? !!e.enabled : true;

    if (!id) throw new Error("[PlayerControllers] controllers[] entry requires 'id'");
    if (!modulePath) throw new Error("[PlayerControllers] controllers[] entry requires 'module' (path)");

    const order = asInt(e.order, 0);
    const deps = Array.isArray(e.deps) ? e.deps.map((x) => String(x || "")).filter(Boolean) : [];

    return { id, modulePath, exportName, enabled, order, deps };
}

function loadControllerClass(modulePath, exportName) {
    // modulePath can be:
    //  - relative: "./controllers/X.js" (recommended)
    //  - absolute-like within scripts env (if your loader supports it)
    //  - package-style: "@env/..." (if your resolver supports it)
    const mod = require(modulePath);

    if (!exportName) {
        // Prefer named export patterns first, then module itself
        if (mod && typeof mod === "function") return mod;
        if (mod && typeof mod.default === "function") return mod.default;

        // If module exports {SomeController}, pick the only function export if unambiguous
        if (mod && typeof mod === "object") {
            const keys = Object.keys(mod);
            if (keys.length === 1 && typeof mod[keys[0]] === "function") return mod[keys[0]];
        }

        throw new Error("[PlayerControllers] module '" + modulePath + "' does not export a controller class");
    }

    const Ctor = mod ? mod[exportName] : null;
    if (typeof Ctor !== "function") {
        throw new Error("[PlayerControllers] module '" + modulePath + "' missing export '" + exportName + "'");
    }
    return Ctor;
}

const DEFAULT_CONTROLLERS = Object.freeze([
    { id: "player.events",   module: "./controllers/PlayerEventsController.js",   export: "PlayerEventsController",   order: 10 },
    { id: "player.gameplay", module: "./controllers/PlayerGameplayController.js", export: "PlayerGameplayController", order: 20, deps: ["player.events"] },
    { id: "player.camera",   module: "./controllers/PlayerCameraController.js",   export: "PlayerCameraController",   order: 30, deps: ["player.gameplay"] },
    { id: "player.ui",       module: "./controllers/PlayerUIController.js",       export: "PlayerUIController",       order: 40, deps: ["player.events", "player.gameplay"] }
]);

function readControllersCfg(cfg) {
    console.log(JSON.stringify(cfg))
    const c = (cfg && cfg.controllers) ? cfg.controllers : null;
    if (Array.isArray(c) && c.length) return c;
    return DEFAULT_CONTROLLERS;
}

/**
 * Creates and registers "player" registry using cfg.controllers (config-first).
 *
 * Contract:
 *  - ENGINE.controllers.registry(name) must exist
 *  - Registry.register(id, Ctor, {order, deps}) must exist
 *
 * @param {object} cfg player config
 * @returns {*} engine registry instance
 */
function createPlayerRegistryFromConfig(cfg) {
    const ENGINE = req(globalThis.ENGINE, "[PlayerControllers] ENGINE is required");
    const C = req(ENGINE.controllers, "[PlayerControllers] ENGINE.controllers is required");
    if (typeof C.registry !== "function") throw new Error("[PlayerControllers] ENGINE.controllers.registry(name) required");

    const regName = (cfg && cfg.registry && cfg.registry.name) ? String(cfg.registry.name || "player") : "player";
    const R = C.registry(regName);
    console.log(JSON.stringify(cfg))
    const list = readControllersCfg(cfg).map(normalizeEntry).filter((e) => e.enabled);

    // Deterministic order: sort by (order asc, id asc)
    list.sort((a, b) => {
        const d = (a.order | 0) - (b.order | 0);
        if (d) return d;
        return a.id < b.id ? -1 : (a.id > b.id ? 1 : 0);
    });

    for (let i = 0; i < list.length; i++) {
        const e = list[i];
        const Ctor = loadControllerClass(e.modulePath, e.exportName);
        R.register(e.id, Ctor, { order: e.order | 0, deps: e.deps });
    }

    return R;
}

module.exports = {
    DEFAULT_CONTROLLERS,
    createPlayerRegistryFromConfig
};