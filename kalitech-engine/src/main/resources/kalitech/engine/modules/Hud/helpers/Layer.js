// FILE: resources/kalitech/builtin/helpers/hud/Layer.js
"use strict";

const {isObj, num, bool, idOf} = require("./HudUtil.js");
const {applyCoordY, parsePlace, placeRect, placePoint} = require("./HudPlacement.js");
const {Element, Panel} = require("./HudElements.js");
const {buildFromSpec} = require("./HudBuilder.js");

function isFn(v) {
    return typeof v === "function";
}

class PanelBuilder {
    constructor(layer, panel) {
        this._layer = layer;
        this._panel = panel;
    }

    flow(cfg) {
        this._panel.flow(cfg || {});
        return this;
    }

    stack(id, text, cfg) {
        this._panel.stack(id, text, cfg || {});
        return this;
    }

    done() {
        if (this._layer) this._layer.flushLayout();
        return this._panel;
    }
}

class Layer {
    constructor(hud, handle) {
        this._hud = hud;
        this._api = hud._api;
        this.handle = handle;
        this.id = idOf(handle);

        this._reg = Object.create(null);
        this._regKeys = [];

        this._placed = [];

        this._lastVp = {w: 0, h: 0};
        this._radioGroups = Object.create(null);

        this._dirtyLayout = false;
        this._autoLayout = true;

        this.__specEpochCounter = 0;
    }

    /**
     * Release JS-side references so GC can collect wrappers quickly.
     * Does not call engine API.
     */
    _disposeLocal() {
        this._reg = Object.create(null);
        this._regKeys.length = 0;

        this._radioGroups = Object.create(null);
        this._placed.length = 0;

        this._dirtyLayout = false;
        this._autoLayout = true;
        this.__specEpochCounter = 0;

        this._lastVp.w = 0;
        this._lastVp.h = 0;

        this._hud = null;
        this._api = null;
        this.handle = null;
    }

    destroy() {
        const api = this._api;
        const h = this.handle;
        this._disposeLocal();
        if (api && h) api.destroyLayer(h);
    }

    clear() {
        this._reg = Object.create(null);
        this._regKeys.length = 0;

        this._radioGroups = Object.create(null);
        this._placed.length = 0;
        this._dirtyLayout = false;
        this.__specEpochCounter = 0;

        this._api.clearLayer(this.handle);
    }

    buildPanel(cfg = {}) {
        const p = this.panel(cfg);
        return new PanelBuilder(this, p);
    }

    /**
     * Build UI from declarative spec into this layer.
     *
     * @param {object|object[]} spec
     * @param {object} opts { relayout?:boolean, pull?:boolean, reuse?:boolean, prune?:boolean, pruneMode?:"hide"|"remove", model?:object }
     * @returns {{created: Object, used: Object}}
     */
    spec(spec, opts = {}) {
        return buildFromSpec(this, spec, opts || {});
    }

    ns(prefix) {
        const p = String(prefix || "").trim();
        if (!p) throw new Error("[HUD] layer.ns(prefix): prefix is required");

        const layer = this;

        function pid(id) {
            return p + "." + String(id);
        }

        function withId(cfg) {
            const c = isObj(cfg) ? cfg : {};
            if (c.id == null) throw new Error("[HUD] ns(...): cfg.id is required");
            const out = Object.assign({}, c);
            out.id = pid(c.id);
            return out;
        }

        return {
            prefix: p,

            get(id) {
                return layer.get(pid(id));
            },
            has(id) {
                return layer.has(pid(id));
            },
            drop(id, remove) {
                return layer.drop(pid(id), !!remove);
            },

            setText(id, text) {
                return layer.setText(pid(id), text);
            },
            setValue(id, v) {
                return layer.setValue(pid(id), v);
            },
            setVisible(id, v) {
                return layer.setVisible(pid(id), v);
            },

            text(cfg) {
                return layer.text(withId(cfg));
            },
            label(cfg) {
                return layer.text(withId(cfg));
            },
            panel(cfg) {
                return layer.panel(withId(cfg));
            },
            buildPanel(cfg) {
                return layer.buildPanel(withId(cfg));
            },
            container(cfg) {
                return layer.container(withId(cfg));
            },
            input(cfg) {
                return layer.input(withId(cfg));
            },
            checkbox(cfg) {
                return layer.checkbox(withId(cfg));
            },
            slider(cfg) {
                return layer.slider(withId(cfg));
            },
            radio(cfg) {
                return layer.radio(withId(cfg));
            },

            spec(spec, opts) {
                return layer.spec(spec, opts || {});
            }
        };
    }

    // --------------------------------------------------------
    // registry
    // --------------------------------------------------------

    _regPut(key, el) {
        const k = String(key);
        el.key = k;
        if (this._reg[k] == null) this._regKeys.push(k);
        this._reg[k] = el;
        return el;
    }

    _regRemoveKey(k) {
        const keys = this._regKeys;
        for (let i = 0; i < keys.length; i++) {
            if (keys[i] === k) {
                keys[i] = keys[keys.length - 1];
                keys.pop();
                return;
            }
        }
    }

    get(key) {
        return this._reg[String(key)] || null;
    }

    has(key) {
        return !!this._reg[String(key)];
    }

    drop(key, remove = false) {
        const k = String(key);
        const el = this._reg[k];
        if (!el) return null;

        delete this._reg[k];
        this._regRemoveKey(k);

        if (el.kind === "radio" && el._radioGroup) {
            const s = this._radioGroups[el._radioGroup];
            if (s) s.delete(k);
        }

        if (remove) this._api.remove(el.handle);
        return el;
    }

    setText(id, text) {
        const el = this.get(id);
        if (el) el.text(text);
        return el || null;
    }

    setValue(id, v) {
        const el = this.get(id);
        if (el) el.value(v);
        return el || null;
    }

    setVisible(id, v) {
        const el = this.get(id);
        if (el) el.visible(v);
        return el || null;
    }

    pullAll() {
        const keys = this._regKeys;
        for (let i = 0; i < keys.length; i++) {
            const el = this._reg[keys[i]];
            if (el && isFn(el.pull)) el.pull();
        }
        return this;
    }

    // --------------------------------------------------------
    // viewport / coord
    // --------------------------------------------------------

    _vp() {
        const vp = this._hud.viewport();
        this._lastVp.w = vp.w | 0;
        this._lastVp.h = vp.h | 0;
        return this._lastVp;
    }

    _coord() {
        const c = this._hud && this._hud.META ? this._hud.META.coord : "topLeft";
        return String(c || "topLeft");
    }

    // --------------------------------------------------------
    // layout batching
    // --------------------------------------------------------

    autoLayout(v = true) {
        this._autoLayout = !!v;
        return this;
    }

    _markDirty() {
        this._dirtyLayout = true;
        if (this._autoLayout) this.flushLayout();
    }

    flushLayout() {
        if (!this._dirtyLayout) return this;
        this._dirtyLayout = false;
        return this.relayout();
    }

    // --------------------------------------------------------
    // placement tracking & relayout
    // --------------------------------------------------------

    _trackPlaced(el) {
        this._placed.push(el);
        return el;
    }

    relayout() {
        const vp = this._vp();
        const coord = this._coord();

        for (let i = 0; i < this._placed.length; i++) {
            const el = this._placed[i];
            if (!el || !el._place) continue;

            if (el.kind === "panel" || el.kind === "container" || el.kind === "input" || el.kind === "slider") {
                const p = placeRect(vp.w, vp.h, el._w, el._h, el._place);
                const y = applyCoordY(coord, vp.h, p.y);
                this._api.setPosition(el.handle, p.x, y);
            } else {
                const p = placePoint(vp.w, vp.h, el._place);
                const y = applyCoordY(coord, vp.h, p.y);
                this._api.setPosition(el.handle, p.x, y);
            }
        }

        return this;
    }

    // --------------------------------------------------------
    // container
    // --------------------------------------------------------

    container(cfg = {}) {
        const c = isObj(cfg) ? cfg : {};
        const vp = this._vp();
        const coord = this._coord();

        const id = c.id;
        const parent = c.parent || null;

        const place = c.place ? parsePlace(c.place) : null;

        let x = num(c.x, 0);
        let y = num(c.y, 0);

        if (place) {
            const p = placeRect(vp.w, vp.h, 0, 0, place);
            x = p.x;
            y = p.y;
        }

        y = applyCoordY(coord, vp.h, y);

        const ph = parent ? parent.handle : null;
        const h = ph
            ? this._api.addContainer(this.handle, ph, x, y)
            : this._api.addContainer(this.handle, x, y);

        const el = new Element(this._hud, h, this, parent);
        el.kind = "container";
        el._setPlace(place);

        if (place) {
            this._trackPlaced(el);
            this._markDirty();
        }

        if (id != null) this._regPut(id, el);
        return el;
    }

    // --------------------------------------------------------
    // panel
    // --------------------------------------------------------

    panel(cfg = {}) {
        const c = isObj(cfg) ? cfg : {};
        const vp = this._vp();
        const coord = this._coord();

        const id = c.id;
        const parent = c.parent || null;

        const w = num(c.w, 0);
        let h = num(c.h, 0);

        const place = c.place ? parsePlace(c.place) : null;

        let x = num(c.x, 0);
        let y = num(c.y, 0);

        if (place) {
            const p = placeRect(vp.w, vp.h, w, h, place);
            x = p.x;
            y = p.y;
        }

        y = applyCoordY(coord, vp.h, y);

        const ph = parent ? parent.handle : null;
        const hh = ph
            ? this._api.addPanel(this.handle, ph, x, y, w, h)
            : this._api.addPanel(this.handle, x, y, w, h);

        const panel = new Panel(this._hud, hh, this, parent);
        panel._w = w;
        panel._h = h;

        panel._autoHeight = bool(c.autoHeight, false);
        panel._autoMinH = num(c.minH, 0);

        const flowCfg = isObj(c.flow) ? Object.assign({}, c.flow) : null;
        if (flowCfg) {
            if (c.padX != null && flowCfg.padX == null) flowCfg.padX = c.padX;
            if (c.padY != null && flowCfg.padY == null) flowCfg.padY = c.padY;
            panel.flow(flowCfg);
        } else if (c.padX != null || c.padY != null || c.gap != null || c.fontSize != null) {
            panel.flow({
                padX: (c.padX != null) ? c.padX : undefined,
                padY: (c.padY != null) ? c.padY : undefined,
                gap: (c.gap != null) ? c.gap : undefined,
                fontSize: (c.fontSize != null) ? c.fontSize : undefined
            });
        }

        if (c.bg) panel.bg(num(c.bg.r, 0), num(c.bg.g, 0), num(c.bg.b, 0), num(c.bg.a, 1));
        if (c.fontSize != null) panel.fontSize(c.fontSize);

        if (place) {
            panel._setPlace(place);
            this._trackPlaced(panel);
            this._markDirty();
        }

        if (id != null) this._regPut(id, panel);
        return panel;
    }

    rect(cfg = {}) {
        return this.panel(cfg);
    }

    // --------------------------------------------------------
    // text / label
    // --------------------------------------------------------

    text(cfg = {}) {
        const c = isObj(cfg) ? cfg : {};
        const vp = this._vp();
        const coord = this._coord();

        const id = c.id;
        const parent = c.parent || null;

        const text = String(c.text ?? "");
        const place = c.place ? parsePlace(c.place) : null;

        let x = num(c.x, 0);
        let y = num(c.y, 0);

        if (place) {
            const p = placePoint(vp.w, vp.h, place);
            x = p.x;
            y = p.y;
        }

        y = applyCoordY(coord, vp.h, y);

        const ph = parent ? parent.handle : null;
        const hh = ph
            ? this._api.addLabel(this.handle, ph, text, x, y)
            : this._api.addLabel(this.handle, text, x, y);

        const el = new Element(this._hud, hh, this, parent);
        el.kind = "text";

        if (c.fontSize != null) el.fontSize(c.fontSize);
        if (c.color) el.color(num(c.color.r, 1), num(c.color.g, 1), num(c.color.b, 1), num(c.color.a, 1));

        if (place) {
            el._setPlace(place);
            this._trackPlaced(el);
            this._markDirty();
        }

        if (id != null) this._regPut(id, el);
        return el;
    }

    label(cfg = {}) {
        return this.text(cfg);
    }

    // --------------------------------------------------------
    // stackText: text inside panel with flow
    // --------------------------------------------------------

    stackText(panel, cfg = {}) {
        const p = panel;
        if (!p || p.kind !== "panel") throw new Error("[HUD] stackText requires panel");

        const c = isObj(cfg) ? cfg : {};
        const id = c.id;
        const text = String((c.text != null) ? c.text : "");

        const m = p._flow || {padX: 0, padY: 0, gap: 0, fontSize: null};

        const hh = this._api.addLabel(this.handle, p.handle, text, 0, 0);

        const el = new Element(this._hud, hh, this, p);
        el.kind = "text";

        const itemFont = (c.fontSize != null) ? (num(c.fontSize, 14) | 0) : ((m.fontSize != null) ? (num(m.fontSize, 14) | 0) : null);
        if (itemFont != null) el.fontSize(itemFont);

        p._stack.push({
            el,
            padX: m.padX,
            padY: m.padY,
            gap: m.gap,
            fontSize: itemFont
        });

        this._relayoutPanelStack(p);

        if (id != null) this._regPut(id, el);
        return el;
    }

    _computeAutoPanelHeight(panel) {
        const p = panel;

        const padY = num(p._flow.padY, 0);
        const gap = num(p._flow.gap, 0);

        const defaultFont = (p._flow.fontSize != null) ? num(p._flow.fontSize, 14) : 14;

        let contentH = padY;
        for (let i = 0; i < p._stack.length; i++) {
            const it = p._stack[i];
            const fs = (it && it.fontSize != null) ? num(it.fontSize, defaultFont) : defaultFont;
            const lineH = Math.max(10, (fs | 0) + 4);
            contentH += lineH;
            if (i !== p._stack.length - 1) contentH += gap;
        }
        contentH += padY;

        const minH = num(p._autoMinH, 0);
        if (minH > 0) contentH = Math.max(contentH, minH);

        return contentH;
    }

    _relayoutPanelStack(panel) {
        const p = panel;
        const coord = this._coord();

        const basePadX = num(p._flow.padX, 0);
        let y = num(p._flow.padY, 0);

        const defaultFont = (p._flow.fontSize != null) ? num(p._flow.fontSize, 14) : 14;
        const gap = num(p._flow.gap, 0);

        for (let i = 0; i < p._stack.length; i++) {
            const it = p._stack[i];
            const el = it.el;
            if (!el) continue;

            const fs = (it.fontSize != null) ? num(it.fontSize, defaultFont) : defaultFont;
            const lineH = Math.max(10, (fs | 0) + 4);

            const x = num(it.padX, basePadX);
            const yy = applyCoordY(coord, 0, y);

            this._api.setPosition(el.handle, x, yy);

            y += lineH;
            if (i !== p._stack.length - 1) y += gap;
        }

        if (p._autoHeight) {
            const newH = this._computeAutoPanelHeight(p);
            if ((newH | 0) !== (p._h | 0)) {
                p._h = newH;
                this._api.setSize(p.handle, p._w, p._h);
                this._markDirty();
            }
        } else {
            this._markDirty();
        }
    }

    // --------------------------------------------------------
    // interactive controls
    // --------------------------------------------------------

    input(cfg = {}) {
        const c = isObj(cfg) ? cfg : {};
        const vp = this._vp();
        const coord = this._coord();

        const id = c.id;
        const parent = c.parent || null;

        const text = String(c.text ?? "");
        const w = num(c.w, 220);
        const h = num(c.h, 26);

        const place = c.place ? parsePlace(c.place) : null;

        let x = num(c.x, 0);
        let y = num(c.y, 0);

        if (place) {
            const p = placeRect(vp.w, vp.h, w, h, place);
            x = p.x;
            y = p.y;
        }

        y = applyCoordY(coord, vp.h, y);

        const ph = parent ? parent.handle : null;
        const hh = ph
            ? this._api.addTextField(this.handle, ph, text, x, y, w, h)
            : this._api.addTextField(this.handle, text, x, y, w, h);

        const el = new Element(this._hud, hh, this, parent);
        el.kind = "input";
        el._w = w;
        el._h = h;

        if (c.fontSize != null) el.fontSize(c.fontSize);

        if (place) {
            el._setPlace(place);
            this._trackPlaced(el);
            this._markDirty();
        }

        if (id != null) this._regPut(id, el);
        return el;
    }

    checkbox(cfg = {}) {
        const c = isObj(cfg) ? cfg : {};
        const vp = this._vp();
        const coord = this._coord();

        const id = c.id;
        const parent = c.parent || null;

        const text = String(c.text ?? "");
        const place = c.place ? parsePlace(c.place) : null;

        let x = num(c.x, 0);
        let y = num(c.y, 0);

        if (place) {
            const p = placeRect(vp.w, vp.h, 0, 0, place);
            x = p.x;
            y = p.y;
        }

        y = applyCoordY(coord, vp.h, y);

        const ph = parent ? parent.handle : null;
        const hh = ph
            ? this._api.addCheckbox(this.handle, ph, text, x, y)
            : this._api.addCheckbox(this.handle, text, x, y);

        const el = new Element(this._hud, hh, this, parent);
        el.kind = "checkbox";

        if (c.fontSize != null) el.fontSize(c.fontSize);
        if (c.checked != null && isFn(this._api.setChecked)) this._api.setChecked(el.handle, !!c.checked);

        if (place) {
            el._setPlace(place);
            this._trackPlaced(el);
            this._markDirty();
        }

        if (id != null) this._regPut(id, el);
        return el;
    }

    slider(cfg = {}) {
        const c = isObj(cfg) ? cfg : {};
        const vp = this._vp();
        const coord = this._coord();

        const id = c.id;
        const parent = c.parent || null;

        const min = num(c.min, 0);
        const max = num(c.max, 1);
        const value = num(c.value, min);

        const w = num(c.w, 220);
        const h = num(c.h, 26);

        const place = c.place ? parsePlace(c.place) : null;

        let x = num(c.x, 0);
        let y = num(c.y, 0);

        if (place) {
            const p = placeRect(vp.w, vp.h, w, h, place);
            x = p.x;
            y = p.y;
        }

        y = applyCoordY(coord, vp.h, y);

        const ph = parent ? parent.handle : null;
        const hh = ph
            ? this._api.addSlider(this.handle, ph, min, max, value, x, y, w, h)
            : this._api.addSlider(this.handle, min, max, value, x, y, w, h);

        const el = new Element(this._hud, hh, this, parent);
        el.kind = "slider";
        el._w = w;
        el._h = h;

        if (place) {
            el._setPlace(place);
            this._trackPlaced(el);
            this._markDirty();
        }

        if (id != null) this._regPut(id, el);
        return el;
    }

    // --------------------------------------------------------
    // JS-only Radio
    // --------------------------------------------------------

    radio(cfg = {}) {
        const c = isObj(cfg) ? cfg : {};
        if (c.id == null) throw new Error("[HUD] radio requires {id}");

        const group = String(c.group || "default");
        const id = String(c.id);

        const el = this.checkbox(Object.assign({}, c, {id}));
        el.kind = "radio";
        el._radioGroup = group;

        this._radioRegister(group, id);

        if (c.checked) this._radioSelect(group, id);
        else if (isFn(this._api.setChecked)) this._api.setChecked(el.handle, false);

        el.select = () => {
            this._radioSelect(group, id);
            return el;
        };
        el.group = () => group;

        return el;
    }

    _radioRegister(groupName, key) {
        const g = String(groupName || "default");
        let s = this._radioGroups[g];
        if (!s) s = this._radioGroups[g] = new Set();
        s.add(String(key));
    }

    _radioSelect(groupName, key) {
        const g = String(groupName || "default");
        const k = String(key);
        const s = this._radioGroups[g];
        if (!s) return;

        for (const it of s) {
            const el = this.get(it);
            if (!el) continue;
            if (String(it) !== k && isFn(this._api.setChecked)) this._api.setChecked(el.handle, false);
        }

        const chosen = this.get(k);
        if (chosen && isFn(this._api.setChecked)) this._api.setChecked(chosen.handle, true);
    }

    radioGroup(name) {
        const g = String(name || "default");
        const self = this;
        return {
            name: g,
            items() {
                const s = self._radioGroups[g];
                if (!s) return [];
                const out = [];
                for (const k of s) {
                    const el = self.get(k);
                    if (el) out.push(el);
                }
                return out;
            },
            selected() {
                const s = self._radioGroups[g];
                if (!s) return null;
                for (const k of s) {
                    const el = self.get(k);
                    if (el && isFn(self._api.isChecked) && self._api.isChecked(el.handle)) return el;
                }
                return null;
            },
            select(elOrId) {
                const id = (typeof elOrId === "string" || typeof elOrId === "number")
                    ? String(elOrId)
                    : String(elOrId?.key ?? elOrId?.id ?? "");
                if (!id) return null;
                self._radioSelect(g, id);
                return self.get(id);
            },
            clear() {
                const s = self._radioGroups[g];
                if (!s) return;
                for (const k of s) {
                    const el = self.get(k);
                    if (el && isFn(self._api.setChecked)) self._api.setChecked(el.handle, false);
                }
            }
        };
    }
}

module.exports = {Layer};