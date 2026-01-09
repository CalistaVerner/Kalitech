// FILE: resources/kalitech/builtin/Hud.js
// Author: Calista Verner
"use strict";

/**
 * Hud.js v2.4.3 (AAA: autoHeight anchor lock)
 *
 * Requires HudApi:
 *   createLayer, destroyLayer, clearLayer
 *   addPanel, addLabel, addContainer (optional)
 *   setText, setVisible, setPosition, setSize, remove
 *   viewport()
 *   setFontSize(element, px)   (optional)
 */

const {num} = require("./helpers/HudUtil.js");
const {Layer} = require("./helpers/Layer.js");

function HudModule(engine, opts) {
    if (!engine || typeof engine.hud !== "function") throw new Error("[HUD] engine.hud() missing");
    const api = engine.hud();

    const hud = {
        _api: api,
        _coord: (opts && opts.coord) ? String(opts.coord) : "topLeft",

        META: {
            name: "hud",
            globalName: "HUD",
            version: "2.4.3",
            description: "Lemur HUD: declarative placement + registry + builder + relayout (AAA stacking fix + anchor lock)",
            engineMin: "0.1.0"
        },

        coord(mode) {
            this._coord = String(mode || "topLeft");
            return this;
        },

        layer(name = "dev") {
            return new Layer(hud, api.createLayer(String(name)));
        },

        viewport() {
            const vp = api.viewport();
            return vp ? { w: num(vp.w), h: num(vp.h) } : { w: 0, h: 0 };
        },

        clearLayer(l) { api.clearLayer(l.handle || l); },
        destroyLayer(l) { api.destroyLayer(l.handle || l); }
    };

    return hud;
}

module.exports = HudModule;
module.exports.META = {moduleId: "hud", globalName: "HUD", version: "2.4.3"};