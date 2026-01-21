// FILE: resources/kalitech/builtin/Hud.js
// Author: Calista Verner
"use strict";

/**
 * Hud.js v3.0.0
 *
 * Java HudApi = thin bridge (create/add/set/get/remove/viewport)
 * JS helpers = full UI brain (registry, builder, placement, radio groups)
 *
 * Required Java HudApi:
 *  createLayer(name), destroyLayer(layer), clearLayer(layer)
 *  addContainer(layer,[parent],x,y)
 *  addPanel(layer,[parent],x,y,w,h)
 *  addLabel(layer,[parent],text,x,y)
 *  addTextField(layer,[parent],text,x,y,w,h)
 *  addCheckbox(layer,[parent],text,x,y)
 *  addSlider(layer,[parent],min,max,value,x,y,w,h)
 *  setText(handle,text), getText(handle)
 *  setVisible(handle,bool), setPosition(handle,x,y), setSize(handle,w,h)
 *  setBgColor(handle,r,g,b,a), setTextColor(handle,r,g,b,a)
 *  setChecked(handle,bool), isChecked(handle)
 *  setSliderValue(handle,v), getSliderValue(handle)
 *  setFontSize(handle,px) (optional)
 *  remove(handle)
 *  viewport()
 */

const {isObj, num} = require("./helpers/HudUtil.js");
const {Layer} = require("./helpers/Layer.js");

class ComponentRegistry {
    constructor() {
        this._reg = Object.create(null); // type -> (layer,cfg)=>Element
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
            version: "3.0.0",
            coord: String(c.coord || "topLeft")
        },

        components: new ComponentRegistry(),

        layer(name) {
            const h = api.createLayer(String(name ?? "hud"));
            return new Layer(hud, h);
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

    // -----------------------------
    // Default component registry
    // -----------------------------
    // These factories call Layer methods (Layer owns JS-only logic: registry, radio, relayout)
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
module.exports.META = {moduleId: "hud", globalName: "HUD", version: "3.0.0"};