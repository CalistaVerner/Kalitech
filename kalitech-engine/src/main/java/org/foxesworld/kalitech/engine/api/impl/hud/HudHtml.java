// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudHtml.java
package org.foxesworld.kalitech.engine.api.impl.hud;

import org.foxesworld.kalitech.engine.api.EngineApiImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "HTML" HUD container.
 *
 * Это не браузер. Это компактный markup -> HUD builder для панелей/текста/картинок.
 *
 * Supported tags:
 *  - <hud> root (optional)
 *  - <div> / <panel> -> group (+ optional background)
 *  - <span> / <text> / <p> -> text
 *  - <img> -> image
 *
 * style="left:10; top:20; width:200; height:40; background:#00000099; color:#fff; font-size:18;"
 *
 * AAA additions:
 *  - Dirty pipeline for markup + data
 *  - Data binding {{path.to.value}} in text nodes and img src
 *  - No full rebuild when only data changes (updates bindings only)
 *  - Stretch semantics: left+right / top+bottom => fillParent (viewport-safe)
 */
final class HudHtml extends HudGroup {

    // -------------------- dirty pipeline --------------------

    private volatile boolean dirtyMarkup = false;
    private volatile String pendingMarkup = "";

    private volatile boolean dirtyData = false;
    private volatile Object pendingData = null;

    // Last applied state (JME thread only)
    private String lastMarkup = "";
    private Object lastData = null;

    // Bindings for fast updates without rebuild (JME thread only)
    private final ArrayList<TextBinding> textBindings = new ArrayList<>(64);
    private final ArrayList<ImageBinding> imageBindings = new ArrayList<>(32);

    HudHtml(int id) {
        super(id);
        this.kind = Kind.HTML;
        this.contentSized = false;
    }

    /**
     * Public API entrypoint.
     *
     * IMPORTANT:
     *  - Safe to call from ANY thread
     *  - Does NOT touch scene graph
     */
    void setHtml(String markup, HudApiImpl api, EngineApiImpl engine) {
        pendingMarkup = (markup == null) ? "" : markup;
        dirtyMarkup = true;
    }

    /**
     * Public API: update data model for bindings.
     *
     * Safe to call from ANY thread.
     * If markup is unchanged, we update only bindings (text/img) without rebuild.
     */
    void setData(Object data, HudApiImpl api, EngineApiImpl engine) {
        pendingData = data;
        dirtyData = true;
    }

    /**
     * Called by HudApiImpl on JME/render thread.
     * Returns true if rebuild happened.
     */
    boolean flushIfDirty(HudApiImpl api, EngineApiImpl engine) {
        boolean doMarkup = dirtyMarkup;
        boolean doData = dirtyData;

        if (!doMarkup && !doData) return false;

        // Consume flags (producer threads only set volatiles; consumer owns scene)
        String m = pendingMarkup;
        Object d = pendingData;

        dirtyMarkup = false;
        dirtyData = false;

        if (m == null) m = "";

        // If markup changed OR never built -> rebuild
        if (!m.equals(lastMarkup) || children.isEmpty()) {
            rebuildNow(m, d, api, engine);
            return true;
        }

        // Markup same, only data changed -> update bindings only
        if (doData) {
            lastData = d;
            applyBindings(d, api, engine);
        }

        return false;
    }

    // -------------------- rebuild --------------------

    private void rebuildNow(String markup, Object data, HudApiImpl api, EngineApiImpl engine) {
        if (api == null) throw new IllegalArgumentException("HudHtml.rebuild: api is null");
        if (engine == null) throw new IllegalArgumentException("HudHtml.rebuild: engine is null");
        if (markup == null) markup = "";

        lastMarkup = markup;
        lastData = data;

        // destroy old children properly (also unregister ids)
        if (!children.isEmpty()) {
            List<HudElement> old = new ArrayList<>(children);
            for (HudElement c : old) api.__destroyRecursiveInternal(c);
            children.clear();
        }
        node.detachAllChildren();

        // clear bindings
        textBindings.clear();
        imageBindings.clear();

        HudHtmlParser.Node dom = HudHtmlParser.parse(markup);
        if (dom == null) return;

        buildChildren(dom, this, api, engine);

        // after full build apply bindings once
        applyBindings(data, api, engine);
    }

    private void buildChildren(HudHtmlParser.Node dom, HudElement parent, HudApiImpl api, EngineApiImpl engine) {
        for (HudHtmlParser.Node n : dom.children) {
            if (n.type == HudHtmlParser.NodeType.TEXT) {
                String raw = (n.text == null) ? "" : n.text;
                String txt = raw.trim();
                if (txt.isEmpty()) continue;

                Map<String, Object> cfg = new HashMap<>(8);
                cfg.put("text", txt);
                HudElement el = api.__createInternal("text", parent, cfg);
                if (el instanceof HudText ht && containsBinding(txt)) {
                    textBindings.add(new TextBinding(ht, txt));
                }
                continue;
            }

            String tag = (n.tag == null ? "" : n.tag.toLowerCase());
            switch (tag) {
                case "div", "panel", "hud" -> buildPanelLike(n, parent, api, engine);
                case "span", "text", "p" -> buildTextLike(n, parent, api, engine);
                case "img" -> buildImageLike(n, parent, api, engine);
                default -> buildPanelLike(n, parent, api, engine);
            }
        }
    }

    private void buildPanelLike(HudHtmlParser.Node n, HudElement parent, HudApiImpl api, EngineApiImpl engine) {
        Map<String, Object> cfg = styleToHudCfg(n.style, n.attrs);

        // AAA default: <hud> root without explicit coords should fill parent
        String tag = (n.tag == null ? "" : n.tag.toLowerCase());
        if ("hud".equals(tag)) {
            // If user didn't specify size/position — make it viewport root
            cfg.putIfAbsent("fillParent", true);
            cfg.putIfAbsent("anchor", "topLeft");
            cfg.putIfAbsent("pivot", "topLeft");
            cfg.putIfAbsent("offset", Map.of("x", 0, "y", 0));
        }

        HudElement group = api.__createInternal("group", parent, cfg);

        // Optional background (panel) - fill parent
        if (n.style != null && n.style.background != null) {
            Map<String, Object> bg = new HashMap<>(12);
            bg.put("anchor", "topLeft");
            bg.put("pivot", "topLeft");
            bg.put("offset", Map.of("x", 0, "y", 0));
            bg.put("fillParent", true);
            bg.put("color", Map.of(
                    "r", n.style.background.r,
                    "g", n.style.background.g,
                    "b", n.style.background.b,
                    "a", n.style.background.a
            ));
            api.__createInternal("rect", group, bg);
        }

        buildChildren(n, group, api, engine);
    }

    private void buildTextLike(HudHtmlParser.Node n, HudElement parent, HudApiImpl api, EngineApiImpl engine) {
        Map<String, Object> cfg = styleToHudCfg(n.style, n.attrs);

        String template = n.collectText();
        if (template == null) template = "";
        template = template.trim();

        cfg.put("text", template);

        if (n.style != null && n.style.color != null) {
            cfg.put("color", Map.of(
                    "r", n.style.color.r,
                    "g", n.style.color.g,
                    "b", n.style.color.b,
                    "a", n.style.color.a
            ));
        }
        if (n.style != null && n.style.fontSizePx > 0f) cfg.put("fontSize", n.style.fontSizePx);

        HudElement el = api.__createInternal("text", parent, cfg);
        if (el instanceof HudText ht && containsBinding(template)) {
            textBindings.add(new TextBinding(ht, template));
        }
    }

    private void buildImageLike(HudHtmlParser.Node n, HudElement parent, HudApiImpl api, EngineApiImpl engine) {
        Map<String, Object> cfg = styleToHudCfg(n.style, n.attrs);

        String src = n.attr("src");
        if (src != null && !src.isBlank()) {
            cfg.put("image", src);
        }

        HudElement el = api.__createInternal("image", parent, cfg);
        if (el instanceof HudImage hi && src != null && containsBinding(src)) {
            imageBindings.add(new ImageBinding(hi, src));
        }
    }

    // -------------------- bindings --------------------

    private void applyBindings(Object data, HudApiImpl api, EngineApiImpl engine) {
        // Text
        for (int i = 0; i < textBindings.size(); i++) {
            TextBinding b = textBindings.get(i);
            if (b == null || b.el == null) continue;
            String resolved = resolveTemplate(b.template, data);
            b.el.setText(resolved, engine);
        }

        // Images
        for (int i = 0; i < imageBindings.size(); i++) {
            ImageBinding b = imageBindings.get(i);
            if (b == null || b.el == null) continue;
            String resolved = resolveTemplate(b.template, data).trim();
            if (!resolved.isEmpty()) {
                b.el.setImage(resolved, engine);
            }
        }
    }

    private static boolean containsBinding(String s) {
        if (s == null) return false;
        int a = s.indexOf("{{");
        if (a < 0) return false;
        int b = s.indexOf("}}", a + 2);
        return b > a;
    }

    /**
     * Replace {{path.to.value}} with values from data model.
     * - Missing values become empty string
     * - Non-strings -> String.valueOf(...)
     */
    private static String resolveTemplate(String template, Object data) {
        if (template == null || template.isEmpty()) return "";
        int a = template.indexOf("{{");
        if (a < 0) return template;

        StringBuilder sb = new StringBuilder(template.length() + 16);
        int i = 0;

        while (true) {
            int s = template.indexOf("{{", i);
            if (s < 0) {
                sb.append(template, i, template.length());
                break;
            }
            int e = template.indexOf("}}", s + 2);
            if (e < 0) {
                // broken token -> append rest
                sb.append(template, i, template.length());
                break;
            }

            // prefix
            sb.append(template, i, s);

            String key = template.substring(s + 2, e).trim();
            Object val = resolvePath(data, key);

            if (val != null) sb.append(val);
            // else empty

            i = e + 2;
        }

        return sb.toString();
    }

    /**
     * Resolve dot-path from JS object / map-like via HudValueParsers.member.
     * Supports "a.b.c" paths.
     */
    private static Object resolvePath(Object data, String path) {
        if (data == null || path == null || path.isBlank()) return null;

        Object cur = data;
        int i = 0;
        int n = path.length();

        while (i < n) {
            int dot = path.indexOf('.', i);
            String key = (dot < 0) ? path.substring(i) : path.substring(i, dot);
            key = key.trim();
            if (key.isEmpty()) return null;

            cur = HudValueParsers.member(cur, key);
            if (cur == null) return null;

            if (dot < 0) break;
            i = dot + 1;
        }

        // Unwrap common "Value" cases if needed (HudValueParsers.asString handles polyglot values),
        // but for template we want primitive string-like:
        if (cur instanceof CharSequence) return cur.toString();
        if (HudValueParsers.isNumber(cur)) return String.valueOf(HudValueParsers.asNum(cur, 0));
        if (HudValueParsers.isBool(cur)) return String.valueOf(HudValueParsers.asBool(cur, false));
        return String.valueOf(cur);
    }

    private static final class TextBinding {
        final HudText el;
        final String template;
        TextBinding(HudText el, String template) {
            this.el = el;
            this.template = template;
        }
    }

    private static final class ImageBinding {
        final HudImage el;
        final String template;
        ImageBinding(HudImage el, String template) {
            this.el = el;
            this.template = template;
        }
    }

    // -------------------- style mapping --------------------

    /**
     * Converts CSS-like style to HudElement config.
     *
     * Coordinate system expectation:
     * ✅ y-down (screen/HTML style)
     *  - top: +px (moves down)
     *  - bottom: -px (moves up from bottom anchor)
     *  - left: +px
     *  - right: -px
     */
    private static Map<String, Object> styleToHudCfg(HudHtmlParser.Style st, Map<String, String> attrs) {
        Map<String, Object> cfg = new HashMap<>(32);
        if (attrs == null) attrs = Map.of();

        // -----------------------------
        // Layout (Flexbox via Yoga/Flex)
        // -----------------------------
        if (st != null) {
            if (st.display != null && "flex".equalsIgnoreCase(st.display.trim())) {
                cfg.put("layout", "flex");
                if (st.flexDirection != null) cfg.put("flexDirection", st.flexDirection);
            }
            if (st.gapPx > 0f) cfg.put("gap", st.gapPx);
            if (st.padL != 0f || st.padT != 0f || st.padR != 0f || st.padB != 0f) {
                cfg.put("padding", Map.of("l", st.padL, "t", st.padT, "r", st.padR, "b", st.padB));
            }
            if (st.position != null) cfg.put("position", st.position);
        }

        // explicit overrides from markup attributes
        String anchorAttr = attrs.get("anchor");
        String pivotAttr  = attrs.get("pivot");
        if (anchorAttr != null) cfg.put("anchor", anchorAttr);
        if (pivotAttr != null)  cfg.put("pivot", pivotAttr);

        // -----------------------------
        // CSS-like positioning: left/top/right/bottom
        // -----------------------------
        String a = "topLeft";
        String p = "topLeft";
        float ox = 0f;
        float oy = 0f;

        boolean inferredAbsolute = (st != null) && (st.left != null || st.top != null || st.right != null || st.bottom != null);
        boolean posAbs;

        if (st != null && st.position != null) {
            String pos = st.position.trim().toLowerCase();
            posAbs = "absolute".equals(pos);
            if (!posAbs && inferredAbsolute) posAbs = true;
        } else {
            posAbs = inferredAbsolute;
        }

        cfg.put("positionAbsolute", posAbs);

        boolean stretchX = false;
        boolean stretchY = false;

        if (st != null) {
            // Stretch semantics (AAA):
            // left+right without explicit width -> stretch to parent width
            // top+bottom without explicit height -> stretch to parent height
            stretchX = (st.left != null && st.right != null && st.width == null);
            stretchY = (st.top != null && st.bottom != null && st.height == null);

            // X: right/left
            if (st.right != null && st.left == null) {
                a = "topRight";
                p = "topRight";
                ox = -st.right;
            } else if (st.left != null && st.right == null) {
                ox = st.left;
            } else if (st.left != null /* && st.right != null */) {
                // When both are set we prefer topLeft anchor + offset = left,
                // stretching is handled via fillParent.
                ox = st.left;
            }

            // Y: bottom/top (y-down)
            if (st.bottom != null && st.top == null) {
                a = a.startsWith("top") ? a.replace("top", "bottom") : a;
                p = p.startsWith("top") ? p.replace("top", "bottom") : p;
                oy = -st.bottom;
            } else if (st.top != null && st.bottom == null) {
                oy = st.top;
            } else if (st.top != null /* && st.bottom != null */) {
                oy = st.top;
            }

            // Size
            if (st.width != null || st.height != null) {
                cfg.put("size", Map.of(
                        "w", st.width == null ? 0f : st.width,
                        "h", st.height == null ? 0f : st.height
                ));
            }

            // If BOTH stretched => fillParent (the real fix for root "left:0;right:0;top:0;bottom:0;")
            if (stretchX && stretchY) {
                cfg.put("fillParent", true);
            }
        }

        // Defaults if not explicitly set by attrs
        cfg.putIfAbsent("anchor", a);
        cfg.putIfAbsent("pivot", p);
        cfg.putIfAbsent("offset", Map.of("x", ox, "y", oy));

        return cfg;
    }
}