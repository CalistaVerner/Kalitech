// Author: Calista Verner
"use strict";

/**
 * Hud.js v2.4.0 (best-of: stable + declarative + registry + builder + relayout)
 *
 * ✅ Core goals
 *  - Absolute coords (no Lemur Container/layout surprises)
 *  - Declarative placement: place:{anchor,x,y} for panels + labels
 *  - Registry: layer.get(id), layer.setText(id,text), layer.drop(id, remove)
 *  - Tiny builder: layer.ui().panel("debug", cfg).stack("fps","FPS: --").done()
 *  - Relayout: layer.relayout() recomputes placed elements when viewport changes
 *  - Coordinate mode: "topLeft" (default) or "bottomLeft" (legacy)
 *
 * Requires HudApi:
 *   createLayer, destroyLayer, clearLayer
 *   addPanel, addLabel
 *   setText, setVisible, setPosition, setSize, remove
 *   viewport()
 *   setFontSize(element, px)   (optional)
 */

// ------------------------------------------------------------
// utils
// ------------------------------------------------------------

function isObj(o) {
    return !!o && typeof o === "object" &&
        (Object.getPrototypeOf(o) === Object.prototype || Object.getPrototypeOf(o) === null);
}
function num(v, fb = 0) { v = +v; return Number.isFinite(v) ? v : fb; }
function bool(v, fb = true) { return typeof v === "boolean" ? v : fb; }
function idOf(h) {
    return h && typeof h.id === "function" ? h.id() :
        h && h.id != null ? h.id : 0;
}
function round(v) { return (v + 0.5) | 0; }

// ------------------------------------------------------------
// coords + placement
// ------------------------------------------------------------

/**
 * coord:
 *  - "topLeft": x,y from top-left, y grows down
 *  - "bottomLeft": x,y from bottom-left, y grows up (legacy conversion)
 */
function applyCoordY(coord, vpH, y) {
    if (coord === "bottomLeft" && vpH > 0) return vpH - y;
    return y;
}

function parsePlace(cfg) {
    const p = cfg && cfg.place;
    if (!isObj(p)) return null;
    const a = String(p.anchor || "tl");
    const x = num(p.x != null ? p.x : p.mx, 0);
    const y = num(p.y != null ? p.y : p.my, 0);
    return { a, x, y };
}

// anchors for rect (panel)
function placeRect(containerW, containerH, w, h, place) {
    const a = place ? place.a : "tl";
    const ox = place ? place.x : 0;
    const oy = place ? place.y : 0;

    let x = 0, y = 0;

    switch (a) {
        case "tr": x = containerW - w; y = 0; break;
        case "bl": x = 0; y = containerH - h; break;
        case "br": x = containerW - w; y = containerH - h; break;

        case "t":  x = (containerW - w) * 0.5; y = 0; break;
        case "b":  x = (containerW - w) * 0.5; y = containerH - h; break;
        case "l":  x = 0; y = (containerH - h) * 0.5; break;
        case "r":  x = containerW - w; y = (containerH - h) * 0.5; break;
        case "c":  x = (containerW - w) * 0.5; y = (containerH - h) * 0.5; break;

        case "tl":
        default:   x = 0; y = 0; break;
    }

    return { x: round(x + ox), y: round(y + oy) };
}

// anchors for point (label). We DO NOT know label size reliably.
function placePoint(containerW, containerH, place) {
    const a = place ? place.a : "tl";
    const ox = place ? place.x : 0;
    const oy = place ? place.y : 0;

    let x = 0, y = 0;

    switch (a) {
        case "tr": x = containerW; y = 0; break;
        case "bl": x = 0; y = containerH; break;
        case "br": x = containerW; y = containerH; break;

        case "t":  x = containerW * 0.5; y = 0; break;
        case "b":  x = containerW * 0.5; y = containerH; break;
        case "l":  x = 0; y = containerH * 0.5; break;
        case "r":  x = containerW; y = containerH * 0.5; break;
        case "c":  x = containerW * 0.5; y = containerH * 0.5; break;

        case "tl":
        default:   x = 0; y = 0; break;
    }

    return { x: round(x + ox), y: round(y + oy) };
}

// ------------------------------------------------------------
// base wrappers
// ------------------------------------------------------------

class Element {
    constructor(hud, handle, layer, parent) {
        this._hud = hud;
        this._api = hud._api;
        this.handle = handle;
        this.id = idOf(handle);
        this.layer = layer;
        this.parent = parent || null;

        this.kind = "element";
        this.key = null;     // registry key
        this._place = null;  // stored placement for relayout
        this._w = 0;         // known size for relayout (panels always)
        this._h = 0;
    }

    text(v) { this._api.setText(this.handle, String(v ?? "")); return this; }
    visible(v) { this._api.setVisible(this.handle, !!v); return this; }
    pos(x, y) { this._api.setPosition(this.handle, num(x), num(y)); return this; }
    size(w, h) {
        w = num(w); h = num(h);
        this._w = w; this._h = h;
        this._api.setSize(this.handle, w, h);
        return this;
    }

    remove() { this._api.remove(this.handle); return this; }

    fontSize(px) {
        if (typeof this._api.setFontSize === "function") this._api.setFontSize(this.handle, num(px, 16));
        return this;
    }

    // store place for relayout (used by Layer)
    _setPlace(place) { this._place = place; return this; }
}

class Panel extends Element {
    constructor(hud, handle, layer, meta) {
        super(hud, handle, layer, null);
        this.kind = "panel";
        this.meta = meta;

        // children go to content handle (grouping). MUST remain visible.
        this.content = null;

        // flow state
        this.flow = { y: 0, gap: meta.gap, fontSize: meta.fontSize };

        // panel-local registry (convenience)
        this._kids = Object.create(null);
    }

    // panel registry
    get(id) { return this._kids[String(id)] || null; }
    has(id) { return !!this._kids[String(id)]; }
    drop(id, remove = false) {
        const k = String(id);
        const el = this._kids[k];
        if (el) {
            if (remove) { try { this._api.remove(el.handle); } catch (e) {} }
            delete this._kids[k];
        }
        return el || null;
    }

    // helpers
    text(id, text, cfg) {
        const el = this.layer.text(Object.assign({}, cfg || {}, { parent: this, id, text }));
        if (id != null) this._kids[String(id)] = el;
        return el;
    }

    stack(id, text, cfg) {
        const el = this.layer.stackText(this, Object.assign({}, cfg || {}, { id, text }));
        if (id != null) this._kids[String(id)] = el;
        return el;
    }

    // fast updates
    setText(id, text) {
        const el = this.get(id);
        if (el) el.text(text);
        return el;
    }

    resetFlow(y = 0) { this.flow.y = num(y, 0); return this; }
}

// ------------------------------------------------------------
// UI Builder
// ------------------------------------------------------------

class UIBuilder {
    constructor(layer) {
        this.layer = layer;
        this.panel = null;
    }

    panel(id, cfg) {
        const p = this.layer.panel(Object.assign({}, cfg || {}, { id }));
        this.panel = p;
        return this;
    }

    use(panelOrId) {
        const p = (typeof panelOrId === "string") ? this.layer.get(panelOrId) : panelOrId;
        this.panel = p || null;
        return this;
    }

    text(id, text, cfg) {
        if (this.panel && this.panel.kind === "panel") this.panel.text(id, text, cfg);
        else this.layer.text(Object.assign({}, cfg || {}, { id, text }));
        return this;
    }

    stack(id, text, cfg) {
        if (!this.panel || this.panel.kind !== "panel") {
            throw new Error("[HUD] ui().stack requires active panel. Call ui().panel(...) first.");
        }
        this.panel.stack(id, text, cfg);
        return this;
    }

    done() { return this.panel; }
}

// ------------------------------------------------------------
// Layer
// ------------------------------------------------------------

class Layer {
    constructor(hud, handle) {
        this._hud = hud;
        this._api = hud._api;
        this.handle = handle;
        this.id = idOf(handle);

        this._reg = Object.create(null);       // id -> Element
        this._placed = [];                     // list of elements that have place stored (for relayout)
        this._lastVp = { w: 0, h: 0 };          // cached viewport
    }

    destroy() { this._api.destroyLayer(this.handle); }
    clear() { this._api.clearLayer(this.handle); }

    // builder
    ui() { return new UIBuilder(this); }

    // registry
    _regPut(id, el) {
        if (id == null) return el;
        const k = String(id);
        this._reg[k] = el;
        el.key = k;
        return el;
    }
    get(id) { return this._reg[String(id)] || null; }
    has(id) { return !!this._reg[String(id)]; }
    drop(id, remove = false) {
        const k = String(id);
        const el = this._reg[k];
        if (el) {
            if (remove) { try { this._api.remove(el.handle); } catch (e) {} }
            delete this._reg[k];
        }
        return el || null;
    }

    // super common QoL
    setText(id, text) {
        const el = this.get(id);
        if (el) el.text(text);
        return el;
    }
    setVisible(id, v) {
        const el = this.get(id);
        if (el) el.visible(v);
        return el;
    }

    // viewport cache
    _vp() {
        const vp = this._hud.viewport();
        this._lastVp.w = vp.w | 0;
        this._lastVp.h = vp.h | 0;
        return this._lastVp;
    }

    // coordinate mode (inherits hud)
    _coord() { return this._hud._coord; }

    // mark element as placed for relayout
    _trackPlaced(el) {
        // avoid duplicates
        if (el && el._place) this._placed.push(el);
        return el;
    }

    /**
     * relayout():
     *  - recompute positions for elements that were created with `place`
     *  - panels are perfect (we know size)
     *  - labels are point-based (no size)
     */
    relayout() {
        const vp = this._vp();
        const coord = this._coord();

        for (let i = 0; i < this._placed.length; i++) {
            const el = this._placed[i];
            if (!el || !el._place) continue;

            // determine container (viewport or panel inner box)
            const parent0 = el.parent;
            if (parent0 && parent0.kind === "panel") {
                const m = parent0.meta;
                const cw = Math.max(0, m.w - m.padX * 2);
                const ch = Math.max(0, m.h - m.padY * 2);

                const p = placePoint(cw, ch, el._place);
                let x = p.x + m.padX;
                let y = p.y + m.padY;

                // coordinate mode conversion for legacy engines
                y = applyCoordY(coord, 0, y); // local panel coords (no viewport flip here)
                this._api.setPosition(el.handle, x, y);
            } else {
                if (el.kind === "panel") {
                    const p = placeRect(vp.w, vp.h, el._w, el._h, el._place);
                    const y = applyCoordY(coord, vp.h, p.y);
                    this._api.setPosition(el.handle, p.x, y);
                } else {
                    const p = placePoint(vp.w, vp.h, el._place);
                    const y = applyCoordY(coord, vp.h, p.y);
                    this._api.setPosition(el.handle, p.x, y);
                }
            }
        }
    }

    // --------------------------------------------------------
    // panel
    // --------------------------------------------------------

    /**
     * panel({
     *   id?: string
     *   w,h,
     *   x,y OR place:{anchor,x,y},
     *   pad: number OR padX/padY,
     *   visible: true,
     *   autoHeight: false,
     *   flow:{ fontSize:16, gap:6 }
     * })
     */
    panel(cfg = {}) {
        const c = isObj(cfg) ? cfg : {};
        const vp = this._vp();
        const coord = this._coord();

        const w = num(c.w, 0);
        const h = num(c.h, 0);

        const padX = (c.padX != null) ? num(c.padX, 10) : (c.pad != null ? num(c.pad, 10) : 10);
        const padY = (c.padY != null) ? num(c.padY, 10) : (c.pad != null ? num(c.pad, 10) : 10);

        const place = parsePlace(c);

        let x = num(c.x, 0);
        let y = num(c.y, 0);

        if (place) {
            const xy = placeRect(vp.w, vp.h, w, h, place);
            x = xy.x;
            y = xy.y;
        }

        // coordinate mode conversion (legacy)
        y = applyCoordY(coord, vp.h, y);

        const hPanel = this._api.addPanel(this.handle, x, y, w, h);

        const flowCfg = isObj(c.flow) ? c.flow : {};
        const meta = {
            w, h,
            padX, padY,
            fontSize: num(flowCfg.fontSize, 16),
            gap: num(flowCfg.gap, 6),
            autoHeight: bool(c.autoHeight, false)
        };

        const panel = new Panel(this._hud, hPanel, this, meta);
        panel._w = w; panel._h = h;

        if (!bool(c.visible, true)) panel.visible(false);

        // content group (MUST stay visible)
        const hContent = this._api.addPanel(this.handle, panel.handle, 0, 0, w, h);
        const content = new Element(this._hud, hContent, this, panel);
        content.visible(true);
        panel.content = content;

        if (place) {
            panel._setPlace(place);
            this._trackPlaced(panel);
        }

        if (c.id != null) this._regPut(c.id, panel);
        return panel;
    }

    rect(cfg = {}) { return this.panel(cfg); }

    // --------------------------------------------------------
    // text / label
    // --------------------------------------------------------

    _parentInfo(parent0) {
        if (!parent0) return { ph: null, insetX: 0, insetY: 0, cw: 0, ch: 0 };

        if (parent0 && parent0.kind === "panel") {
            const m = parent0.meta;
            return {
                ph: parent0.content ? parent0.content.handle : parent0.handle,
                insetX: m.padX,
                insetY: m.padY,
                cw: Math.max(0, m.w - m.padX * 2),
                ch: Math.max(0, m.h - m.padY * 2)
            };
        }

        return { ph: parent0.handle, insetX: 0, insetY: 0, cw: 0, ch: 0 };
    }

    /**
     * text({
     *   id?: string
     *   text: string
     *   x,y OR place:{anchor,x,y}
     *   parent?: panel
     *   visible?: boolean
     *   fontSize?: number
     * })
     */
    text(cfg = {}) {
        const c = isObj(cfg) ? cfg : {};
        const vp = this._vp();
        const coord = this._coord();

        const parent0 = c.parent || null;
        const pi = this._parentInfo(parent0);

        let x = num(c.x, 0) + pi.insetX;
        let y = num(c.y, 0) + pi.insetY;

        const place = parsePlace(c);
        if (place) {
            const cw = pi.cw || vp.w;
            const ch = pi.ch || vp.h;
            const xy = placePoint(cw, ch, place);
            x = xy.x + pi.insetX;
            y = xy.y + pi.insetY;
        }

        // coordinate mode conversion only applies when rooted in viewport,
        // NOT inside panel (panel-local coords already match y-down layout)
        if (!parent0) y = applyCoordY(coord, vp.h, y);

        const ph = pi.ph;
        const hLabel = ph
            ? this._api.addLabel(this.handle, ph, String(c.text ?? ""), x, y)
            : this._api.addLabel(this.handle, String(c.text ?? ""), x, y);

        const el = new Element(this._hud, hLabel, this, parent0);
        if (!bool(c.visible, true)) el.visible(false);
        if (c.fontSize != null) el.fontSize(c.fontSize);

        if (place) {
            el._setPlace(place);
            this._trackPlaced(el);
        }

        if (c.id != null) this._regPut(c.id, el);
        return el;
    }

    label(cfg = {}) { return this.text(cfg); }

    // --------------------------------------------------------
    // flow stacking
    // --------------------------------------------------------

    /**
     * stackText(panel, { id?, text, x?, fontSize?, gap?, visible? })
     */
    stackText(panel, cfg = {}) {
        if (!panel || panel.kind !== "panel") throw new Error("[HUD] stackText expects Panel");

        const c = isObj(cfg) ? cfg : {};
        const m = panel.meta;

        const fs = num(c.fontSize, panel.flow.fontSize);
        const gap = num(c.gap, panel.flow.gap);
        const lh = fs + 2;

        const x = num(c.x, 0);
        const y = panel.flow.y;

        panel.flow.y += lh + gap;

        const el = this.text({
            parent: panel,
            x, y,
            text: String(c.text ?? ""),
            visible: bool(c.visible, true),
            fontSize: fs
        });

        if (c.id != null) this._regPut(c.id, el);

        if (m.autoHeight) {
            const need = m.padY + panel.flow.y + m.padY;
            if (need > m.h) {
                m.h = need;
                panel._h = m.h;
                this._api.setSize(panel.handle, m.w, m.h);
                if (panel.content) this._api.setSize(panel.content.handle, m.w, m.h);
            }
        }

        return el;
    }
}

// ------------------------------------------------------------
// Module
// ------------------------------------------------------------

function HudModule(engine, opts) {
    if (!engine || typeof engine.hud !== "function") throw new Error("[HUD] engine.hud() missing");
    const api = engine.hud();

    const hud = {
        _api: api,
        _coord: (opts && opts.coord) ? String(opts.coord) : "topLeft", // ✅ default for your current runtime

        META: {
            name: "hud",
            globalName: "HUD",
            version: "2.4.0",
            description: "Lemur HUD: declarative placement + registry + builder + relayout + coord mode",
            engineMin: "0.1.0"
        },

        coord(mode) { this._coord = String(mode || "topLeft"); return this; },

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
module.exports.META = { name: "hud", globalName: "HUD", version: "2.4.0" };

/* ------------------------------------------------------------
USAGE (recommended):

const HUD = require("kalitech/builtin/Hud.js")(engine); // default coord="topLeft"
const L = HUD.layer("debug");

// declarative + registry:
const p = L.panel({
  id: "debug.panel",
  w: 360, h: 80,
  place: { anchor:"tl", x: 10, y: 10 },
  pad: 10,
  autoHeight: true,
  flow: { fontSize: 16, gap: 6 }
});

L.stackText(p, { id:"debug.title", text:"DEBUG", fontSize:18 });
L.stackText(p, { id:"debug.fps", text:"FPS: --" });
L.stackText(p, { id:"debug.pos", text:"POS: --" });
L.stackText(p, { id:"debug.cam", text:"CAM: --" });

// later:
L.setText("debug.fps", "FPS: 144.0");

// builder:
L.ui()
  .panel("stats", { w: 320, h: 80, place:{anchor:"bl", x: 20, y: -20}, pad: 12, autoHeight:true })
  .stack("hp",   "HP: 100")
  .stack("ammo", "Ammo: 30/120")
  .done();

// relayout after resolution change:
L.relayout();

// legacy engines:
const HUD2 = require("kalitech/builtin/Hud.js")(engine, { coord:"bottomLeft" });
------------------------------------------------------------ */