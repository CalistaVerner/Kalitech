// FILE: resources/kalitech/builtin/helpers/hud/Layer.js
"use strict";

const {isObj, num, bool, idOf} = require("./HudUtil.js");
const {applyCoordY, parsePlace, placeRect, placePoint} = require("./HudPlacement.js");
const {Element, Panel} = require("./HudElements.js");
const {UIBuilder} = require("./UIBuilder.js");

class Layer {
    constructor(hud, handle) {
        this._hud = hud;
        this._api = hud._api;
        this.handle = handle;
        this.id = idOf(handle);

        this._reg = Object.create(null); // key -> Element
        this._placed = [];               // for relayout

        this._lastVp = {w: 0, h: 0};

        // JS-only radio groups: groupName -> Set<key>
        this._radioGroups = Object.create(null);
    }

    destroy() {
        this._api.destroyLayer(this.handle);
    }

    clear() {
        // clear registry + radio groups deterministically
        this._reg = Object.create(null);
        this._radioGroups = Object.create(null);
        this._placed.length = 0;
        this._api.clearLayer(this.handle);
    }

    ui() {
        return new UIBuilder(this);
    }

    // --------------------------------------------------------
    // registry
    // --------------------------------------------------------

    _regPut(key, el) {
        const k = String(key);
        el.key = k;
        this._reg[k] = el;
        return el;
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
        const x0 = num(c.x, 0);
        const y0 = num(c.y, 0);

        let x = x0;
        let y = y0;

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

        if (place) this._trackPlaced(el);
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
        const h = num(c.h, 0);

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

        // visual opts
        if (c.bg) panel.bg(num(c.bg.r, 0), num(c.bg.g, 0), num(c.bg.b, 0), num(c.bg.a, 1));
        if (c.fontSize != null) panel.fontSize(c.fontSize);

        if (place) {
            panel._setPlace(place);
            this._trackPlaced(panel);
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

        // internal stack placement: top-left inside panel
        const m = p._flow || {padX: 0, padY: 0, gap: 0, fontSize: null};

        const x = (num(p._place ? 0 : p._w, 0), 0); // unused; we compute relative via Lemur parent anyway
        const y = 0;

        // Create label as child of panel node:
        const hh = this._api.addLabel(this.handle, p.handle, text, 0, 0);

        const el = new Element(this._hud, hh, this, p);
        el.kind = "text";

        if (m.fontSize != null) el.fontSize(m.fontSize);

        // Store in panel stack list for relayout inside panel (point placement)
        p._stack.push({el, padX: m.padX, padY: m.padY, gap: m.gap});

        // Immediately layout this stack item (incremental deterministic)
        this._relayoutPanelStack(p);

        if (id != null) this._regPut(id, el);
        return el;
    }

    _relayoutPanelStack(panel) {
        const p = panel;
        const vp = this._vp();
        const coord = this._coord();

        // Panel top-left in script space:
        // - For children, Lemur uses local coords relative to parent, but our API takes x/y in screen space.
        // We use panel current screen translation as baseline.
        // NOTE: This stays stable because Java keeps TL contract.
        const panelLoc = p.handle; // handle
        // We cannot read actual position from Java API (no getter) — so we lay out using API calls with relative y stacking only.
        // Strategy:
        // - Place children using their current x/y already set by Lemur layout OR keep explicit positions relative to panel origin.
        // - Simplest: stack by setting child positions at (padX, padY + idx*(line+gap)) with parent's coordinate transform handled in Java attachTo.
        // In your Java attachTo(), children are attached to parent Node, so localTranslation becomes relative -> good.
        let y = num(p._flow.padY, 0);
        for (let i = 0; i < p._stack.length; i++) {
            const it = p._stack[i];
            const el = it.el;
            if (!el) continue;
            const x = num(it.padX, 0);
            // here y is top-left style; Java uses HudCoords conversion inside setPosition() (box contract)
            // but for labels Java is point-placed baseline (your addLabel uses point TL conversion).
            // So we treat y as point TL:
            const yy = applyCoordY(coord, 0, y);
            this._api.setPosition(el.handle, x, yy);
            y += (num(p._flow.gap, 0) + 18); // 18 default line height; fontSize affects it but we don't have measure; keep deterministic
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
        if (c.checked != null) this._api.setChecked(el.handle, !!c.checked);

        if (place) {
            el._setPlace(place);
            this._trackPlaced(el);
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
        }

        if (id != null) this._regPut(id, el);
        return el;
    }

    // --------------------------------------------------------
    // JS-only Radio (Checkbox + exclusivity)
    // cfg: {id,text,group,checked,parent,x,y,place,fontSize}
    // --------------------------------------------------------

    radio(cfg = {}) {
        const c = isObj(cfg) ? cfg : {};
        if (c.id == null) throw new Error("[HUD] radio requires {id}");

        const group = String(c.group || "default");
        const id = String(c.id);

        // create as checkbox, then upgrade semantics
        const el = this.checkbox(Object.assign({}, c, {id}));
        el.kind = "radio";
        el._radioGroup = group;

        this._radioRegister(group, id);

        if (c.checked) this._radioSelect(group, id);
        else this._api.setChecked(el.handle, false);

        // small sugar
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
            if (String(it) !== k) this._api.setChecked(el.handle, false);
        }

        const chosen = this.get(k);
        if (chosen) this._api.setChecked(chosen.handle, true);
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
                    if (el && self._api.isChecked(el.handle)) return el;
                }
                return null;
            },
            select(elOrId) {
                const id = (typeof elOrId === "string" || typeof elOrId === "number") ? String(elOrId) : String(elOrId?.key ?? elOrId?.id ?? "");
                if (!id) return null;
                self._radioSelect(g, id);
                return self.get(id);
            },
            clear() {
                const s = self._radioGroups[g];
                if (!s) return;
                for (const k of s) {
                    const el = self.get(k);
                    if (el) self._api.setChecked(el.handle, false);
                }
            }
        };
    }
}

module.exports = {Layer};