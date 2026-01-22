// FILE: resources/kalitech/builtin/Hud.js
// Author: Calista Verner
"use strict";

const {isObj, num} = require("./helpers/HudUtil.js");
const {Layer} = require("./helpers/Layer.js");
const {buildFromHtml} = require("./helpers/HudHtml.js");

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

    const hud = {
        _api: api,

        META: {
            id: "kalitech.hud",
            globalName: "HUD",
            version: "3.1.0",
            coord: String(c.coord || "topLeft")
        },

        components: new ComponentRegistry(),

        layer(name) {
            const h = api.createLayer(String(name ?? "hud"));
            return new Layer(hud, h);
        },

        /**
         * Build UI from HTML string into a new layer.
         *
         * @param {string} layerName
         * @param {string} html
         * @param {object} opts { model?:object, relayout?:boolean, pull?:boolean }
         * @returns {object} { layer, created, root }
         */
        html(layerName, html, opts) {
            const layer = hud.layer(String(layerName ?? "hud"));
            const res = buildFromHtml(layer, String(html ?? ""), opts || {});
            return {layer, created: res.created, root: res.root};
        },

        viewport() {
            const vp = api.viewport();
            return vp ? {w: num(vp.w(), 0) | 0, h: num(vp.h(), 0) | 0} : {w: 0, h: 0};
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
module.exports.META = {moduleId: "hud", globalName: "HUD", version: "3.1.0"};