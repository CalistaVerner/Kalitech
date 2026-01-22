// FILE: resources/kalitech/builtin/helpers/hud/HudHtml.js
"use strict";

const {isObj, num, bool} = require("./HudUtil.js");

/**
 * Supported tags:
 *  - <layer> root optional
 *  - <panel> -> Layer.panel
 *  - <rect>  -> Layer.panel
 *  - <text> / <label> / <span> -> Layer.text
 *  - <input> -> Layer.input
 *  - <checkbox> -> Layer.checkbox
 *  - <radio> -> Layer.radio
 *  - <slider> -> Layer.slider
 *  - <container> -> Layer.container
 *
 * Attributes:
 *  id, parent, x, y, w, h, width, height, minH, autoHeight, anchor, px, py, gap, font, fontSize
 *  value, min, max, checked, group
 *  style="bg:#0b0f14;bgA:0.65;color:#fff;alpha:1;width:280;..."
 *  data-bind="path" data-prefix="..." data-fmt="int|fixed2|fixed3|raw"
 */

function isSpace(ch) {
    return ch === " " || ch === "\n" || ch === "\r" || ch === "\t";
}

function isNameChar(ch) {
    const c = ch.charCodeAt(0);
    return (
        (c >= 48 && c <= 57) || // 0-9
        (c >= 65 && c <= 90) || // A-Z
        (c >= 97 && c <= 122) || // a-z
        ch === "-" || ch === "_" || ch === ":" || ch === "." // allow data-bind etc
    );
}

function decodeEntities(s) {
    return String(s)
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&amp;/g, "&")
        .replace(/&quot;/g, "\"")
        .replace(/&#39;/g, "'");
}

function parseHexColor(s) {
    const v = String(s || "").trim();
    if (!v) return null;

    const hex = v.startsWith("#") ? v.slice(1) : v;
    if (hex.length === 3) {
        const r = parseInt(hex[0] + hex[0], 16);
        const g = parseInt(hex[1] + hex[1], 16);
        const b = parseInt(hex[2] + hex[2], 16);
        if (!Number.isFinite(r) || !Number.isFinite(g) || !Number.isFinite(b)) return null;
        return {r: r / 255, g: g / 255, b: b / 255, a: 1};
    }
    if (hex.length === 6) {
        const r = parseInt(hex.slice(0, 2), 16);
        const g = parseInt(hex.slice(2, 4), 16);
        const b = parseInt(hex.slice(4, 6), 16);
        if (!Number.isFinite(r) || !Number.isFinite(g) || !Number.isFinite(b)) return null;
        return {r: r / 255, g: g / 255, b: b / 255, a: 1};
    }
    if (hex.length === 8) {
        const r = parseInt(hex.slice(0, 2), 16);
        const g = parseInt(hex.slice(2, 4), 16);
        const b = parseInt(hex.slice(4, 6), 16);
        const a = parseInt(hex.slice(6, 8), 16);
        if (!Number.isFinite(r) || !Number.isFinite(g) || !Number.isFinite(b) || !Number.isFinite(a)) return null;
        return {r: r / 255, g: g / 255, b: b / 255, a: a / 255};
    }
    return null;
}

function parseStyle(styleText) {
    const s = String(styleText || "");
    if (!s.trim()) return null;

    const out = Object.create(null);
    const parts = s.split(";");
    for (let i = 0; i < parts.length; i++) {
        const p = parts[i].trim();
        if (!p) continue;
        const idx = p.indexOf(":");
        if (idx <= 0) continue;

        const k = p.slice(0, idx).trim();
        const v = p.slice(idx + 1).trim();
        if (!k) continue;

        out[k] = v;
    }
    return out;
}

function fmtFromName(name) {
    const n = String(name || "").trim();
    if (!n) return null;

    if (n === "int") return (v) => String((+v || 0) | 0);
    if (n === "raw") return (v) => (v == null) ? "" : String(v);

    const m = /^fixed(\d+)$/.exec(n);
    if (m) {
        const digits = Math.max(0, Math.min(8, (+m[1] || 0) | 0));
        return (v) => {
            const x = +v;
            if (!Number.isFinite(x)) return "0";
            return x.toFixed(digits);
        };
    }
    return null;
}

function buildPlace(attrs) {
    const anchor = String(attrs.anchor || attrs.placeAnchor || "tl");
    const x = (attrs.x != null) ? num(attrs.x, 0) : num(attrs.placeX, 0);
    const y = (attrs.y != null) ? num(attrs.y, 0) : num(attrs.placeY, 0);
    return {anchor, x, y};
}

function applyStyleToCfg(cfg, styleObj) {
    if (!styleObj) return cfg;

    const bg = styleObj.bg || styleObj.background || null;
    const bgA = styleObj.bgA || styleObj.backgroundAlpha || null;

    if (bg) {
        const c = parseHexColor(bg);
        if (c) {
            if (bgA != null) c.a = num(bgA, c.a);
            cfg.bg = {r: c.r, g: c.g, b: c.b, a: c.a};
        }
    }

    const col = styleObj.color || null;
    const alpha = styleObj.alpha || styleObj.a || null;
    if (col) {
        const c = parseHexColor(col);
        if (c) {
            if (alpha != null) c.a = num(alpha, c.a);
            cfg.color = {r: c.r, g: c.g, b: c.b, a: c.a};
        }
    }

    // Size from style (CSS-like)
    if (styleObj.w != null) cfg.w = num(styleObj.w, cfg.w || 0);
    if (styleObj.h != null) cfg.h = num(styleObj.h, cfg.h || 0);
    if (styleObj.width != null) cfg.w = num(styleObj.width, cfg.w || 0);
    if (styleObj.height != null) cfg.h = num(styleObj.height, cfg.h || 0);

    if (styleObj.fontSize != null) cfg.fontSize = num(styleObj.fontSize, cfg.fontSize || 14);
    if (styleObj.font != null) cfg.fontSize = num(styleObj.font, cfg.fontSize || 14);

    if (styleObj.padX != null) cfg.padX = num(styleObj.padX, cfg.padX || 0);
    if (styleObj.padY != null) cfg.padY = num(styleObj.padY, cfg.padY || 0);
    if (styleObj.gap != null) cfg.gap = num(styleObj.gap, cfg.gap || 0);

    if (styleObj.autoHeight != null) cfg.autoHeight = bool(styleObj.autoHeight, cfg.autoHeight || false);
    if (styleObj.minH != null) cfg.minH = num(styleObj.minH, cfg.minH || 0);

    return cfg;
}

function tokenize(html) {
    const s = String(html || "");
    const tokens = [];
    let i = 0;

    function push(type, value) {
        tokens.push({type, value});
    }

    while (i < s.length) {
        const ch = s[i];

        if (ch === "<") {
            if (s.startsWith("<!--", i)) {
                const end = s.indexOf("-->", i + 4);
                if (end < 0) break;
                i = end + 3;
                continue;
            }

            const close = (i + 1 < s.length && s[i + 1] === "/");
            i += close ? 2 : 1;

            while (i < s.length && isSpace(s[i])) i++;

            let name = "";
            while (i < s.length && isNameChar(s[i])) {
                name += s[i++];
            }
            name = name.toLowerCase();

            const attrs = Object.create(null);

            while (i < s.length) {
                while (i < s.length && isSpace(s[i])) i++;
                if (i >= s.length) break;
                if (s[i] === "/" || s[i] === ">") break;

                let an = "";
                while (i < s.length && isNameChar(s[i])) an += s[i++];

                an = an.toLowerCase();
                while (i < s.length && isSpace(s[i])) i++;

                let av = "true";
                if (s[i] === "=") {
                    i++;
                    while (i < s.length && isSpace(s[i])) i++;
                    if (s[i] === "\"" || s[i] === "'") {
                        const q = s[i++];
                        let buf = "";
                        while (i < s.length && s[i] !== q) buf += s[i++];
                        if (s[i] === q) i++;
                        av = decodeEntities(buf);
                    } else {
                        let buf = "";
                        while (i < s.length && !isSpace(s[i]) && s[i] !== ">" && s[i] !== "/") {
                            buf += s[i++];
                        }
                        av = decodeEntities(buf);
                    }
                }

                if (an) attrs[an] = av;
            }

            let selfClose = false;
            while (i < s.length && isSpace(s[i])) i++;
            if (s[i] === "/") {
                selfClose = true;
                i++;
            }
            if (s[i] === ">") i++;

            if (close) push("close", {name});
            else push("open", {name, attrs, selfClose});

            continue;
        }

        let txt = "";
        while (i < s.length && s[i] !== "<") txt += s[i++];
        if (txt) push("text", decodeEntities(txt));
    }

    return tokens;
}

function parseAst(html) {
    const toks = tokenize(html);
    const root = {name: "root", attrs: Object.create(null), children: []};
    const stack = [root];

    for (let i = 0; i < toks.length; i++) {
        const t = toks[i];

        if (t.type === "text") {
            const v = String(t.value || "");
            if (v.trim().length) stack[stack.length - 1].children.push({name: "#text", text: v});
            continue;
        }

        if (t.type === "open") {
            const node = {name: t.value.name, attrs: t.value.attrs || Object.create(null), children: []};
            stack[stack.length - 1].children.push(node);
            if (!t.value.selfClose) stack.push(node);
            continue;
        }

        if (t.type === "close") {
            const n = t.value.name;
            for (let k = stack.length - 1; k > 0; k--) {
                if (stack[k].name === n) {
                    stack.length = k;
                    break;
                }
            }
        }
    }

    return root;
}

function normalizeCfgFromAttrs(attrs) {
    const a = attrs || Object.create(null);
    const cfg = Object.create(null);

    if (a.id != null) cfg.id = String(a.id);
    if (a.parent != null) cfg.parent = String(a.parent);

    if (a.x != null) cfg.x = num(a.x, 0);
    if (a.y != null) cfg.y = num(a.y, 0);

    // size: w/h and width/height
    if (a.w != null) cfg.w = num(a.w, 0);
    if (a.h != null) cfg.h = num(a.h, 0);
    if (a.width != null) cfg.w = num(a.width, cfg.w || 0);
    if (a.height != null) cfg.h = num(a.height, cfg.h || 0);

    // NOTE: keep typo support if someone already used it
    if (a.autohight != null) cfg.autoHeight = bool(a.autohight, false);

    if (a.autoheight != null) cfg.autoHeight = bool(a.autoheight, false);
    if (a.minh != null) cfg.minH = num(a.minh, 0);

    if (a.anchor != null) cfg.anchor = String(a.anchor);
    if (a.placeanchor != null) cfg.anchor = String(a.placeanchor);

    if (a.px != null) cfg.padX = num(a.px, 0);
    if (a.py != null) cfg.padY = num(a.py, 0);
    if (a.gap != null) cfg.gap = num(a.gap, 0);

    if (a.font != null) cfg.fontSize = num(a.font, 14);
    if (a.fontsize != null) cfg.fontSize = num(a.fontsize, 14);

    // controls
    if (a.value != null) cfg.value = num(a.value, 0);
    if (a.min != null) cfg.min = num(a.min, 0);
    if (a.max != null) cfg.max = num(a.max, 1);

    if (a.checked != null) cfg.checked = bool(a.checked === "true" || a.checked === true, false);
    if (a.group != null) cfg.group = String(a.group);

    const st = parseStyle(a.style);
    applyStyleToCfg(cfg, st);

    return cfg;
}

function computeTextContent(node) {
    if (!node || !Array.isArray(node.children)) return "";
    let out = "";
    for (let i = 0; i < node.children.length; i++) {
        const c = node.children[i];
        if (c && c.name === "#text") out += c.text;
    }
    return String(out).trim();
}

function resolveParent(layer, parentKeyOrId, created) {
    if (!parentKeyOrId) return null;

    const k = String(parentKeyOrId);
    const fromMap = created[k];
    if (fromMap) return fromMap;

    const fromLayer = layer.get(k);
    if (fromLayer) return fromLayer;

    return null;
}

function attachBinding(el, attrs, model) {
    if (!el || !attrs) return;

    const bindPath = attrs["data-bind"];
    if (!bindPath || !model) return;

    const prefix = attrs["data-prefix"];
    if (prefix != null && typeof el.bindPrefix === "function") el.bindPrefix(prefix);

    const fmtName = attrs["data-fmt"];
    const fmt = fmtFromName(fmtName);
    if (fmt && typeof el.bindFormat === "function") el.bindFormat(fmt);

    if (typeof el.bind === "function") el.bind(model, bindPath);
}

function tagToType(name) {
    const n = String(name || "").toLowerCase();
    if (n === "panel") return "Panel";
    if (n === "rect") return "Rect";
    if (n === "container") return "Container";
    if (n === "text" || n === "label" || n === "span") return "Text";
    if (n === "input") return "Input";
    if (n === "checkbox") return "Checkbox";
    if (n === "radio") return "Radio";
    if (n === "slider") return "Slider";
    return null;
}

function hasPanelTextChildren(node) {
    if (!node || !Array.isArray(node.children)) return false;
    for (let i = 0; i < node.children.length; i++) {
        const ch = node.children[i];
        if (!ch || ch.name === "#text") continue;
        const n = String(ch.name || "").toLowerCase();
        if (n === "text" || n === "label" || n === "span") return true;
    }
    return false;
}

function buildNode(layer, node, created, model) {
    if (!node || node.name === "#text") return null;

    const type = tagToType(node.name);
    if (!type) {
        for (let i = 0; i < node.children.length; i++) buildNode(layer, node.children[i], created, model);
        return null;
    }

    const cfg = normalizeCfgFromAttrs(node.attrs);

    if (type === "Text") {
        const content = computeTextContent(node);
        if (content) cfg.text = content;
        else if (node.attrs && node.attrs.text != null) cfg.text = String(node.attrs.text);
    }

    if (type === "Panel" || type === "Rect") {
        // Deterministic defaults: allow HTML to omit w/h for stacked panels
        if (!(cfg.w > 0)) cfg.w = 280;

        const childText = hasPanelTextChildren(node);
        if (cfg.autoHeight == null && childText) cfg.autoHeight = true;

        if (cfg.autoHeight === true && !(cfg.h > 0)) cfg.h = 1;

        const px = (cfg.padX != null) ? cfg.padX : ((node.attrs && node.attrs["data-padx"] != null) ? num(node.attrs["data-padx"], 0) : null);
        const py = (cfg.padY != null) ? cfg.padY : ((node.attrs && node.attrs["data-pady"] != null) ? num(node.attrs["data-pady"], 0) : null);
        const gap = (cfg.gap != null) ? cfg.gap : ((node.attrs && node.attrs["data-gap"] != null) ? num(node.attrs["data-gap"], 0) : null);
        const fontSize = (cfg.fontSize != null) ? cfg.fontSize : ((node.attrs && node.attrs["data-font"] != null) ? num(node.attrs["data-font"], 14) : null);

        const flow = Object.create(null);
        if (px != null) flow.padX = px;
        if (py != null) flow.padY = py;
        if (gap != null) flow.gap = gap;
        if (fontSize != null) flow.fontSize = fontSize;
        cfg.flow = flow;
    }

    const hasPlace = (node.attrs && (node.attrs.anchor != null || node.attrs.placeanchor != null || node.attrs.x != null || node.attrs.y != null));
    if (hasPlace) cfg.place = buildPlace(cfg);

    if (cfg.parent) {
        const p = resolveParent(layer, cfg.parent, created);
        if (p) cfg.parent = p;
        else delete cfg.parent;
    }

    const el = layer._hud.components.create(type, layer, cfg);
    if (cfg.id) created[cfg.id] = el;

    if ((type === "Panel" || type === "Rect") && el && el.kind === "panel") {
        const st = node.children || [];
        for (let i = 0; i < st.length; i++) {
            const ch = st[i];
            if (!ch || ch.name === "#text") continue;

            const childType = tagToType(ch.name);
            if (childType === "Text") {
                const ccfg = normalizeCfgFromAttrs(ch.attrs);
                const content = computeTextContent(ch);
                const text = content || (ch.attrs && ch.attrs.text != null ? String(ch.attrs.text) : "");
                const id = ccfg.id || null;
                const fontSize = (ccfg.fontSize != null) ? ccfg.fontSize : null;

                const stackCfg = Object.create(null);
                if (fontSize != null) stackCfg.fontSize = fontSize;
                if (ccfg.color) stackCfg.color = ccfg.color;

                const kid = id || ("__stack_" + i);
                const childEl = el.stack(kid, text, stackCfg);

                if (id) created[id] = childEl;
                attachBinding(childEl, ch.attrs, model);
            } else {
                if (!isObj(ch.attrs)) ch.attrs = Object.create(null);
                ch.attrs.parent = cfg.id || (el.key || el.id);
                buildNode(layer, ch, created, model);
            }
        }
    } else {
        if (Array.isArray(node.children) && node.children.length) {
            for (let i = 0; i < node.children.length; i++) {
                const ch = node.children[i];
                if (!ch || ch.name === "#text") continue;

                if (!isObj(ch.attrs)) ch.attrs = Object.create(null);
                if (el && (el.kind === "panel" || el.kind === "container") && ch.attrs.parent == null) {
                    const pid = cfg.id || el.key || null;
                    if (pid) ch.attrs.parent = pid;
                }
                buildNode(layer, ch, created, model);
            }
        }
    }

    attachBinding(el, node.attrs, model);
    return el;
}

function buildFromHtml(layer, html, opts = {}) {
    if (!layer) throw new Error("[HUD][HTML] layer is required");

    const o = isObj(opts) ? opts : {};
    const model = o.model || null;

    const ast = parseAst(html);
    const created = Object.create(null);

    const children = ast.children || [];
    for (let i = 0; i < children.length; i++) {
        buildNode(layer, children[i], created, model);
    }

    if (bool(o.pull, true) && model) layer.pullAll();
    if (bool(o.relayout, true)) layer.flushLayout ? layer.flushLayout() : layer.relayout();

    return {created, root: ast};
}

module.exports = {
    tokenize,
    parseAst,
    buildFromHtml
};