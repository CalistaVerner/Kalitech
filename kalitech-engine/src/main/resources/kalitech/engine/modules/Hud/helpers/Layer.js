"use strict";

const {isObj, num, bool, idOf} = require("./HudUtil.js");
const {applyCoordY, parsePlace, placeRect, placePoint} = require("./HudPlacement.js");
const {Element, Panel} = require("./HudElements.js");
const {buildFromSpec} = require("./HudBuilder.js");

function isFn(v) {
    return typeof v === "function";
}

function q2(v) {
    return Math.round(v * 100);
}

function fmtQ2(q) {
    const neg = q < 0;
    if (neg) q = -q;
    const i = (q / 100) | 0;
    const f = q - i * 100;
    return (neg ? "-" : "") + i + "." + (f < 10 ? "0" : "") + f;
}

/**
 * Prefix spec ids in-place (create-time only).
 * Supports root object or array of root specs.
 * Throws if spec objects are not extensible (frozen/sealed), because silent failure is worse.
 *
 * @param {object|object[]} spec
 * @param {string} prefix
 * @returns {*}
 */
function prefixSpecInPlace(spec, prefix) {
    if (!spec || !prefix) return spec;

    const p = String(prefix);
    const dot = p + ".";

    // Root can be array
    const stack = [];

    if (Array.isArray(spec)) {
        for (let i = spec.length - 1; i >= 0; i--) stack.push(spec[i]);
    } else {
        stack.push(spec);
    }

    while (stack.length) {
        const s = stack.pop();
        if (!s || typeof s !== "object") continue;

        // If someone frozen spec, we can't prefix ids deterministically.
        if (!Object.isExtensible(s)) {
            throw new Error("[HUD] ns.spec(): spec object is not extensible (frozen/sealed). Provide mutable spec or use builder-level prefixing.");
        }

        // id
        if (s.id != null) {
            const id = String(s.id);
            if (id && id.indexOf(dot) !== 0) s.id = dot + id;
        }

        // parent (string id)
        if (s.parent != null && (typeof s.parent === "string" || typeof s.parent === "number")) {
            const pid = String(s.parent);
            if (pid && pid.indexOf(dot) !== 0) s.parent = dot + pid;
        }

        const kids = s.children;
        if (!kids) continue;

        if (Array.isArray(kids)) {
            for (let i = kids.length - 1; i >= 0; i--) stack.push(kids[i]);
        } else if (typeof kids === "object") {
            stack.push(kids);
        }
    }

    return spec;
}

class LayerBindings {
    /**
     * @param {object} ns namespace object
     */
    constructor(ns) {
        this._ns = ns;

        this._n = 0;

        this._kind = [];
        this._id = [];

        this._read = [];
        this._fmt = [];

        this._lastN = [];
        this._lastS = [];

        // vec3q2 caches (3 ints per binding)
        this._vx = [];
        this._vy = [];
        this._vz = [];
    }

    /**
     * Bind text with primitive compare.
     *
     * @param {string} id local id (without prefix)
     * @param {function(*): (string|number|boolean|null|undefined)} read
     * @param {function(*): string} fmt
     * @returns {LayerBindings}
     */
    text(id, read, fmt) {
        const i = this._n++;
        this._kind[i] = 0;
        this._id[i] = String(id);
        this._read[i] = read;
        this._fmt[i] = fmt || null;
        this._lastS[i] = "\u0000";
        return this;
    }

    /**
     * Bind integer text.
     *
     * @param {string} id
     * @param {function(*): number} read
     * @param {string} prefix
     * @returns {LayerBindings}
     */
    int(id, read, prefix) {
        const p = String(prefix || "");
        return this.text(id, read, (v) => p + (v | 0));
    }

    /**
     * Bind vec3 as q2 text: "LABEL: x | y | z".
     * Reads existing pose object (no allocations).
     *
     * @param {string} id
     * @param {function(*): {x:number,y:number,z:number}|null} readObj
     * @param {string} label
     * @returns {LayerBindings}
     */
    vec3q2(id, readObj, label) {
        const i = this._n++;
        this._kind[i] = 1;
        this._id[i] = String(id);
        this._read[i] = readObj;
        this._fmt[i] = String(label || "");
        this._vx[i] = 0x7fffffff;
        this._vy[i] = 0x7fffffff;
        this._vz[i] = 0x7fffffff;
        return this;
    }

    /**
     * Execute bindings (no allocations in hot path).
     *
     * @param {*} model
     */
    run(model) {
        const ns = this._ns;

        for (let i = 0; i < this._n; i++) {
            const kind = this._kind[i];

            if (kind === 0) {
                const v = this._read[i](model);

                // Fast path: number compare without converting to string.
                if (typeof v === "number") {
                    const last = this._lastN[i];

                    // NaN !== NaN, so we need stable behavior: treat NaN as equal-to-NaN.
                    if (v === last || (Number.isNaN(v) && Number.isNaN(last))) continue;

                    this._lastN[i] = v;

                    const s = this._fmt[i] ? this._fmt[i](v) : String(v);
                    if (s !== this._lastS[i]) {
                        this._lastS[i] = s;
                        ns.setText(this._id[i], s);
                    }
                    continue;
                }

                const s = this._fmt[i] ? this._fmt[i](v) : String(v ?? "");
                if (s === this._lastS[i]) continue;
                this._lastS[i] = s;
                ns.setText(this._id[i], s);
                continue;
            }

            // vec3q2
            const o = this._read[i](model);
            if (!o) continue;

            const xq = q2(o.x);
            const yq = q2(o.y);
            const zq = q2(o.z);

            if (xq === this._vx[i] && yq === this._vy[i] && zq === this._vz[i]) continue;

            this._vx[i] = xq;
            this._vy[i] = yq;
            this._vz[i] = zq;

            const label = this._fmt[i];
            ns.setText(this._id[i], label + fmtQ2(xq) + " | " + fmtQ2(yq) + " | " + fmtQ2(zq));
        }
    }
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
                const c = isObj(cfg) ? cfg : {};
                if (c.id == null) throw new Error("[HUD] ns.text: cfg.id is required");
                c.id = pid(c.id);
                return layer.text(c);
            },

            panel(cfg) {
                const c = isObj(cfg) ? cfg : {};
                if (c.id == null) throw new Error("[HUD] ns.panel: cfg.id is required");
                c.id = pid(c.id);
                return layer.panel(c);
            },

            /**
             * Spec in namespace.
             * This MUST guarantee that created element ids match ns.setText/ns.get.
             */
            spec(spec0, opts0) {
                const o = (opts0 && typeof opts0 === "object") ? opts0 : {};
                prefixSpecInPlace(spec0, p);
                return layer.spec(spec0, o);
            },

            /**
             * Dirty-check bindings inside API.
             * @returns {LayerBindings}
             */
            bind() {
                return new LayerBindings(this);
            }
        };
    }

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

    // ----- element factories -----

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
        const h = ph ? this._api.addContainer(this.handle, ph, x, y) : this._api.addContainer(this.handle, x, y);

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

        const flowCfg = isObj(c.flow) ? c.flow : null;
        if (flowCfg) panel.flow(flowCfg);
        else if (c.padX != null || c.padY != null || c.gap != null || c.fontSize != null) {
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
        if (c.color) {
            const col = c.color;
            if (isObj(col)) el.color(num(col.r, 1), num(col.g, 1), num(col.b, 1), num(col.a, 1));
        }

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

        const itemFont = (c.fontSize != null)
            ? (num(c.fontSize, 14) | 0)
            : ((m.fontSize != null) ? (num(m.fontSize, 14) | 0) : null);

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
                    : String(elOrId && (elOrId.key ?? elOrId.id ?? ""));
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