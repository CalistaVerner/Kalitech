// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudHtmlParser.java
package org.foxesworld.kalitech.engine.api.impl.hud;

import com.jme3.math.ColorRGBA;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTML5-ish parser for HUD markup backed by jsoup.
 *
 * ...
 */
final class HudHtmlParser {

    enum NodeType { ELEMENT, TEXT }

    static final class Style {
        // positioning / sizing
        Float left, top, right, bottom, width, height;

        // typography
        float fontSizePx = -1f;

        // colors
        ColorRGBA background;
        ColorRGBA color;

        // layout (flexbox via Yoga)
        String display;        // "flex" etc
        String flexDirection;  // "row"|"column"
        float gapPx = 0f;

        // box model
        float padL = 0f, padT = 0f, padR = 0f, padB = 0f;

        // positioning mode
        String position; // absolute|relative|static
    }

    static final class Node {
        final NodeType type;
        String tag;
        String text;
        final Map<String, String> attrs = new HashMap<>();
        final List<Node> children = new ArrayList<>();
        final Style style = new Style();

        Node(NodeType type) { this.type = type; }

        String attr(String name) {
            if (name == null) return null;
            return attrs.get(name.toLowerCase());
        }

        String collectText() {
            if (type == NodeType.TEXT) return text == null ? "" : text;
            StringBuilder sb = new StringBuilder();
            collectTextInto(this, sb);
            return sb.toString();
        }

        private static void collectTextInto(Node n, StringBuilder sb) {
            if (n == null) return;
            if (n.type == NodeType.TEXT) {
                if (n.text != null) sb.append(n.text);
                return;
            }
            for (Node c : n.children) collectTextInto(c, sb);
        }
    }

    static Node parse(String html) {
        if (html == null) return null;

        Document doc = Jsoup.parseBodyFragment(html);
        Element body = doc.body();

        Node root = new Node(NodeType.ELEMENT);
        root.tag = "hud";

        // Convert only body children into our nodes
        for (org.jsoup.nodes.Node n : body.childNodes()) {
            Node cn = convert(n);
            if (cn != null) root.children.add(cn);
        }

        return root;
    }

    private static Node convert(org.jsoup.nodes.Node n) {
        if (n == null) return null;

        if (n instanceof TextNode tn) {
            String t = tn.text();
            if (t == null) return null;
            if (t.isBlank()) return null;
            Node out = new Node(NodeType.TEXT);
            out.text = t;
            return out;
        }

        if (n instanceof Element el) {
            Node out = new Node(NodeType.ELEMENT);
            out.tag = el.tagName().toLowerCase();

            // attrs
            el.attributes().forEach(a -> out.attrs.put(a.getKey().toLowerCase(), a.getValue()));

            // inline style
            parseStyle(out.attr("style"), out.style);

            // children
            for (org.jsoup.nodes.Node c : el.childNodes()) {
                Node cc = convert(c);
                if (cc != null) out.children.add(cc);
            }

            return out;
        }

        // other nodes (comments, etc) ignored
        return null;
    }

    // -----------------------------
    // CSS inline style parsing (subset)
    // -----------------------------

    private static void parseStyle(String style, Style out) {
        if (style == null || style.isBlank()) return;
        String[] parts = style.split(";");
        for (String p : parts) {
            String item = p.trim();
            if (item.isEmpty()) continue;
            int c = item.indexOf(':');
            if (c < 0) continue;
            String k = item.substring(0, c).trim().toLowerCase();
            String v = item.substring(c + 1).trim();

            switch (k) {
                case "left" -> out.left = parsePx(v);
                case "top" -> out.top = parsePx(v);
                case "right" -> out.right = parsePx(v);
                case "bottom" -> out.bottom = parsePx(v);
                case "width" -> out.width = parsePx(v);
                case "height" -> out.height = parsePx(v);

                case "font-size" -> {
                    Float fs = parsePx(v);
                    out.fontSizePx = (fs == null) ? -1f : fs;
                }

                case "background", "background-color" -> out.background = parseColor(v);
                case "color" -> out.color = parseColor(v);

                case "display" -> out.display = norm(v);
                case "flex-direction" -> out.flexDirection = norm(v);
                case "gap" -> {
                    Float g = parsePx(v);
                    out.gapPx = (g == null) ? 0f : Math.max(0f, g);
                }

                case "padding" -> applyPaddingShorthand(v, out);
                case "padding-left" -> out.padL = nz(parsePx(v));
                case "padding-top" -> out.padT = nz(parsePx(v));
                case "padding-right" -> out.padR = nz(parsePx(v));
                case "padding-bottom" -> out.padB = nz(parsePx(v));

                case "position" -> out.position = norm(v);

                default -> { }
            }
        }
    }

    private static void applyPaddingShorthand(String v, Style out) {
        // CSS shorthand: 1,2,3,4 values
        String s = (v == null) ? "" : v.trim().toLowerCase();
        if (s.isEmpty()) return;
        String[] xs = s.split("\\s+");
        if (xs.length == 0) return;

        Float a = parsePx(xs[0]);
        Float b = (xs.length > 1) ? parsePx(xs[1]) : null;
        Float c = (xs.length > 2) ? parsePx(xs[2]) : null;
        Float d = (xs.length > 3) ? parsePx(xs[3]) : null;

        if (xs.length == 1) {
            float p = nz(a);
            out.padT = out.padR = out.padB = out.padL = p;
        } else if (xs.length == 2) {
            float pv = nz(a);
            float ph = nz(b);
            out.padT = out.padB = pv;
            out.padL = out.padR = ph;
        } else if (xs.length == 3) {
            out.padT = nz(a);
            out.padL = out.padR = nz(b);
            out.padB = nz(c);
        } else {
            out.padT = nz(a);
            out.padR = nz(b);
            out.padB = nz(c);
            out.padL = nz(d);
        }
    }

    private static float nz(Float f) {
        return (f == null) ? 0f : Math.max(0f, f);
    }

    private static String norm(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    private static Float parsePx(String v) {
        if (v == null) return null;
        String s = v.trim().toLowerCase();
        if (s.endsWith("px")) s = s.substring(0, s.length() - 2).trim();
        if (s.endsWith("%")) return null; // percentage: resolve later if needed
        try { return Float.parseFloat(s); } catch (Throwable ignored) { return null; }
    }

    private static ColorRGBA parseColor(String v) {
        if (v == null) return null;
        String s = v.trim().toLowerCase();
        try {
            if (s.startsWith("#")) {
                String hex = s.substring(1);
                if (hex.length() == 6) {
                    int rgb = Integer.parseInt(hex, 16);
                    float r = ((rgb >> 16) & 0xff) / 255f;
                    float g = ((rgb >> 8) & 0xff) / 255f;
                    float b = (rgb & 0xff) / 255f;
                    return new ColorRGBA(r, g, b, 1f);
                }
                if (hex.length() == 8) {
                    long rgba = Long.parseLong(hex, 16);
                    float r = ((rgba >> 24) & 0xff) / 255f;
                    float g = ((rgba >> 16) & 0xff) / 255f;
                    float b = ((rgba >> 8) & 0xff) / 255f;
                    float a = (rgba & 0xff) / 255f;
                    return new ColorRGBA(r, g, b, a);
                }
            }
            if (s.startsWith("rgba")) {
                int l = s.indexOf('(');
                int r = s.indexOf(')');
                if (l > 0 && r > l) {
                    String[] xs = s.substring(l + 1, r).split(",");
                    if (xs.length >= 4) {
                        float rr = Float.parseFloat(xs[0].trim()) / 255f;
                        float gg = Float.parseFloat(xs[1].trim()) / 255f;
                        float bb = Float.parseFloat(xs[2].trim()) / 255f;
                        float aa = Float.parseFloat(xs[3].trim());
                        return new ColorRGBA(rr, gg, bb, aa);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
