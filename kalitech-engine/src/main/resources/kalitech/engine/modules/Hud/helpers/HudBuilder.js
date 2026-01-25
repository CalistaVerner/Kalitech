"use strict";

const {isObj, num, bool} = require("./HudUtil.js");

function isFn(v) {
    return typeof v === "function";
}

const KindByType = Object.freeze({
    Container: "container",
    Panel: "panel",
    Rect: "panel",
    Label: "text",
    Text: "text",
    Input: "input",
    Checkbox: "checkbox",
    Slider: "slider",
    Radio: "radio"
});

function normalizeType(type) {
    const t = String(type || "").trim();
    if (!t) return "";
    return t[0].toUpperCase() + t.slice(1);
}

function expectedKind(type) {
    return KindByType[type] || "element";
}

function normalizeId(v) {
    if (v === undefined || v === null) return null;
    const s = String(v).trim();
    return s ? s : null;
}

function shouldPrefix(prefix, id) {
    if (!prefix || !id) return false;
    return id.indexOf(prefix + ".") !== 0;
}

function prefixedId(prefix, id) {
    if (!id) return null;
    return shouldPrefix(prefix, id) ? (prefix + "." + id) : id;
}

function resolveParent(layer, parent, created, prefix) {
    if (!parent) return null;

    if (typeof parent === "string" || typeof parent === "number") {
        const raw = String(parent);
        const k = prefixedId(prefix, raw);
        const c = created[k];
        if (c) return c;
        const r = layer.get(k);
        if (r) return r;
        return null;
    }

    if (isObj(parent) && parent.kind) return parent;
    return null;
}

function applyCommon(el, spec) {
    if (!el || !spec) return;

    if (spec.visible !== undefined && isFn(el.visible)) el.visible(!!spec.visible);

    if (spec.text !== undefined && isFn(el.text)) el.text(String(spec.text ?? ""));
    if (spec.value !== undefined && isFn(el.value)) el.value(spec.value);

    if (spec.fontSize !== undefined && isFn(el.fontSize)) el.fontSize(num(spec.fontSize, 14));

    if (spec.color && isFn(el.color)) {
        const c = spec.color;
        if (isObj(c)) el.color(num(c.r, 1), num(c.g, 1), num(c.b, 1), num(c.a, 1));
    }

    if (spec.bg && isFn(el.bg)) {
        const b = spec.bg;
        if (isObj(b)) el.bg(num(b.r, 0), num(b.g, 0), num(b.b, 0), num(b.a, 1));
    }

    if ((spec.w !== undefined || spec.h !== undefined) && isFn(el.size)) {
        const w = (spec.w !== undefined) ? num(spec.w, el._w || 0) : (el._w || 0);
        const h = (spec.h !== undefined) ? num(spec.h, el._h || 0) : (el._h || 0);
        el.size(w, h);
    }

    if ((spec.x !== undefined || spec.y !== undefined) && isFn(el.pos)) {
        const x = (spec.x !== undefined) ? num(spec.x, 0) : 0;
        const y = (spec.y !== undefined) ? num(spec.y, 0) : 0;
        el.pos(x, y);
    }

    if (spec.place !== undefined && isFn(el._setPlace)) {
        el._setPlace(spec.place || null);
        if (el.layer && isFn(el.layer._markDirty)) el.layer._markDirty();
    }

    if (el.kind === "panel" && spec.flow && isFn(el.flow)) {
        el.flow(spec.flow);
    }
}

function shouldStackIntoPanel(panel, childSpec) {
    if (!panel || panel.kind !== "panel") return false;
    if (!childSpec || normalizeType(childSpec.type) !== "Text") return false;

    if (childSpec.x !== undefined || childSpec.y !== undefined) return false;
    if (childSpec.place !== undefined) return false;

    if (childSpec.stack === true) return true;
    if (childSpec.stack === false) return false;

    return panel._flow != null;
}

function buildOne(layer, spec, created, used, opts, parentEl) {
    if (!isObj(spec)) return null;

    const type = normalizeType(spec.type);
    if (!type) return null;

    const prefix = opts.prefix || "";
    const rawId = normalizeId(spec.id);
    const id = prefixedId(prefix, rawId);

    const reuse = bool(opts.reuse, true);
    const resolvedParent = resolveParent(layer, spec.parent || parentEl, created, prefix);

    const cfg = Object.create(null);

    if (id) cfg.id = id;
    if (resolvedParent) cfg.parent = resolvedParent;

    if (spec.place !== undefined) cfg.place = spec.place;
    if (spec.x !== undefined) cfg.x = spec.x;
    if (spec.y !== undefined) cfg.y = spec.y;

    if (spec.w !== undefined) cfg.w = spec.w;
    if (spec.h !== undefined) cfg.h = spec.h;

    if (spec.autoHeight !== undefined) cfg.autoHeight = spec.autoHeight;
    if (spec.minH !== undefined) cfg.minH = spec.minH;

    if (spec.padX !== undefined) cfg.padX = spec.padX;
    if (spec.padY !== undefined) cfg.padY = spec.padY;
    if (spec.gap !== undefined) cfg.gap = spec.gap;
    if (spec.fontSize !== undefined) cfg.fontSize = spec.fontSize;

    if (spec.flow !== undefined) cfg.flow = spec.flow;

    if (spec.text !== undefined) cfg.text = spec.text;
    if (spec.value !== undefined) cfg.value = spec.value;

    if (spec.min !== undefined) cfg.min = spec.min;
    if (spec.max !== undefined) cfg.max = spec.max;
    if (spec.checked !== undefined) cfg.checked = spec.checked;
    if (spec.group !== undefined) cfg.group = spec.group;

    if (spec.color !== undefined) cfg.color = spec.color;
    if (spec.bg !== undefined) cfg.bg = spec.bg;
    if (spec.style !== undefined) cfg.style = spec.style;

    let el = null;

    if (reuse && id) {
        const existing = layer.get(id);
        if (existing) {
            const needKind = expectedKind(type);
            if (existing.kind !== needKind && !(needKind === "panel" && existing.kind === "panel")) {
                layer.drop(id, true);
            } else {
                el = existing;
            }
        }
    }

    if (!el) {
        el = layer._hud.components.create(type, layer, cfg);
        if (id) created[id] = el;
    } else {
        if (resolvedParent && el.parent && el.parent !== resolvedParent) {
            layer.drop(id, true);
            el = layer._hud.components.create(type, layer, cfg);
            if (id) created[id] = el;
        } else {
            applyCommon(el, spec);
        }
    }

    if (!el) return null;

    if (id) {
        used[id] = true;
        el.__specEpoch = opts.__epoch | 0;
    }

    const kids = spec.children;
    if (!kids) return el;

    if (Array.isArray(kids)) {
        if (kids.length === 0) return el;

        if (el.kind === "panel") {
            for (let i = 0; i < kids.length; i++) {
                const ch = kids[i];
                if (!isObj(ch)) continue;

                if (shouldStackIntoPanel(el, ch)) {
                    const rawCid = normalizeId(ch.id) || ("__stack_" + (i | 0));
                    const cid = prefixedId(prefix, rawCid);
                    const text = (ch.text !== undefined) ? String(ch.text ?? "") : "";

                    let stackCfg;
                    if (ch.fontSize !== undefined || ch.color !== undefined) {
                        stackCfg = Object.create(null);
                        if (ch.fontSize !== undefined) stackCfg.fontSize = ch.fontSize;
                        if (ch.color !== undefined) stackCfg.color = ch.color;
                    }

                    const childEl = el.stack(cid, text, stackCfg);
                    if (cid) {
                        created[cid] = childEl;
                        used[cid] = true;
                        if (childEl) childEl.__specEpoch = opts.__epoch | 0;
                    }
                    if (childEl) applyCommon(childEl, ch);
                    continue;
                }

                buildOne(layer, ch, created, used, opts, el);
            }
        } else if (el.kind === "container") {
            for (let i = 0; i < kids.length; i++) buildOne(layer, kids[i], created, used, opts, el);
        } else {
            for (let i = 0; i < kids.length; i++) buildOne(layer, kids[i], created, used, opts, null);
        }

        return el;
    }

    if (isObj(kids)) {
        if (el.kind === "panel") {
            if (shouldStackIntoPanel(el, kids)) {
                const rawCid = normalizeId(kids.id) || "__stack_0";
                const cid = prefixedId(prefix, rawCid);
                const text = (kids.text !== undefined) ? String(kids.text ?? "") : "";

                let stackCfg;
                if (kids.fontSize !== undefined || kids.color !== undefined) {
                    stackCfg = Object.create(null);
                    if (kids.fontSize !== undefined) stackCfg.fontSize = kids.fontSize;
                    if (kids.color !== undefined) stackCfg.color = kids.color;
                }

                const childEl = el.stack(cid, text, stackCfg);
                if (cid) {
                    created[cid] = childEl;
                    used[cid] = true;
                    if (childEl) childEl.__specEpoch = opts.__epoch | 0;
                }
                if (childEl) applyCommon(childEl, kids);
                return el;
            }
            buildOne(layer, kids, created, used, opts, el);
        } else if (el.kind === "container") {
            buildOne(layer, kids, created, used, opts, el);
        } else {
            buildOne(layer, kids, created, used, opts, null);
        }
    }

    return el;
}

function pruneLayer(layer, epoch, opts) {
    const mode = String(opts.pruneMode || "hide");
    const remove = (mode === "remove");

    const reg = layer._reg;
    if (!reg) return;

    const keys = layer._regKeys;
    if (Array.isArray(keys)) {
        for (let i = 0; i < keys.length; i++) {
            const k = keys[i];
            const el = reg[k];
            if (!el) continue;
            if ((el.__specEpoch | 0) === 0) continue;

            if ((el.__specEpoch | 0) !== (epoch | 0)) {
                if (remove) layer.drop(k, true);
                else if (isFn(el.visible)) el.visible(false);
            }
        }
        return;
    }

    for (const k in reg) {
        const el = reg[k];
        if (!el) continue;
        if ((el.__specEpoch | 0) === 0) continue;

        if ((el.__specEpoch | 0) !== (epoch | 0)) {
            if (remove) layer.drop(k, true);
            else if (isFn(el.visible)) el.visible(false);
        }
    }
}

function buildFromSpec(layer, spec, opts = {}) {
    if (!layer) throw new Error("[HUD][SPEC] layer is required");

    const o = isObj(opts) ? opts : {};
    const created = Object.create(null);
    const used = Object.create(null);

    const epoch = ((layer.__specEpochCounter | 0) + 1) | 0;
    layer.__specEpochCounter = epoch;
    o.__epoch = epoch;

    if (Array.isArray(spec)) {
        for (let i = 0; i < spec.length; i++) buildOne(layer, spec[i], created, used, o, null);
    } else if (isObj(spec)) {
        buildOne(layer, spec, created, used, o, null);
    }

    const prune = bool(o.prune, bool(o.reuse, true));
    if (prune) pruneLayer(layer, epoch, o);

    if (bool(o.pull, true) && o.model) layer.pullAll();
    if (bool(o.relayout, true)) layer.flushLayout ? layer.flushLayout() : layer.relayout();

    return {created, used};
}

module.exports = {buildFromSpec};