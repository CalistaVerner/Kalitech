// FILE: resources/kalitech/builtin/Hud.js
"use strict";

const {isObj, num} = require("./helpers/HudUtil.js");
const {Layer} = require("./helpers/Layer.js");

class ComponentRegistry {
    constructor() {
        this._reg = Object.create(null);
    }

    register(type, factory) {
        const t = String(type || "").trim();
        if (!t) throw new Error("[HUD] components.register: empty type");
        if (typeof factory !== "function") throw new Error("[HUD] components.register: factory must be function");
        this._reg[t] = factory;
        return this;
    }

    has(type) {
        return !!this._reg[String(type || "").trim()];
    }

    create(type, layer, cfg) {
        const t = String(type || "").trim();
        const fn = this._reg[t];
        if (!fn) throw new Error("[HUD] Unknown component type: " + t);
        return fn(layer, isObj(cfg) ? cfg : {});
    }
}

function HudModule(engine, cfg) {
    if (!engine || typeof engine.hud !== "function") {
        throw new Error("[HUD] ENGINE.hud() missing");
    }

    const api = engine.hud();
    const c = isObj(cfg) ? cfg : {};

    // Stable viewport cache to avoid per-call allocations.
    const _vpCache = {w: 0, h: 0};

    const hud = {
        _api: api,

        META: {
            id: "kalitech.hud",
            globalName: "HUD",
            version: "3.2.0",
            coord: String(c.coord || "topLeft")
        },

        components: new ComponentRegistry(),

        layer(name) {
            const h = api.createLayer(String(name ?? "hud"));
            return new Layer(hud, h);
        },

        spec(layerName, spec, opts) {
            const layer = hud.layer(String(layerName ?? "hud"));
            const res = layer.spec(spec, opts || {});
            return {layer, created: res.created, used: res.used};
        },

        /**
         * Returns a stable object reference {w,h} updated on each call.
         * Do not store it if you need a snapshot; copy values if required.
         */
        viewport() {
            const vp = api.viewport();
            if (!vp) {
                _vpCache.w = 0;
                _vpCache.h = 0;
                return _vpCache;
            }
            _vpCache.w = num(vp.w(), 0) | 0;
            _vpCache.h = num(vp.h(), 0) | 0;
            return _vpCache;
        },

        cursor(enabled, force) {
            if (typeof force === "boolean") api.setCursorEnabled(!!enabled, force);
            else api.setCursorEnabled(!!enabled);
        },

        clearLayer(l) {
            api.clearLayer((l && l.handle) ? l.handle : l);
        },
        destroyLayer(l) {
            api.destroyLayer((l && l.handle) ? l.handle : l);
        }
    };

    hud.components
        .register("Container", (layer, cfg0) => layer.container(cfg0))
        .register("Panel", (layer, cfg0) => layer.panel(cfg0))
        .register("Rect", (layer, cfg0) => layer.panel(cfg0))
        .register("Label", (layer, cfg0) => layer.text(cfg0))
        .register("Text", (layer, cfg0) => layer.text(cfg0))
        .register("Input", (layer, cfg0) => layer.input(cfg0))
        .register("Checkbox", (layer, cfg0) => layer.checkbox(cfg0))
        .register("Slider", (layer, cfg0) => layer.slider(cfg0))
        .register("Radio", (layer, cfg0) => layer.radio(cfg0));

    return hud;
}

module.exports = HudModule;
module.exports.META = {moduleId: "hud", globalName: "HUD", version: "3.2.0"};