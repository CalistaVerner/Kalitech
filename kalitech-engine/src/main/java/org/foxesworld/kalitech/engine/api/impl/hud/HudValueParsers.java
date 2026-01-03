// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudValueParsers.java
package org.foxesworld.kalitech.engine.api.impl.hud;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import org.graalvm.polyglot.Value;

import java.util.Locale;
import java.util.Map;

/**
 * AAA Value parsers for GraalJS <-> Java bridge.
 *
 * Goals:
 * - Robust: never throw to game loop
 * - Useful: supports dot-path access for bindings ("player.hp", "items.0.name")
 * - Flexible: accept primitives, Map, and Polyglot Value objects
 * - Predictable: clamps, sane defaults, and string parsing
 */
final class HudValueParsers {

    private HudValueParsers() {}

    // -----------------------------
    // Member access
    // -----------------------------

    static Object member(Object obj, String key) {
        if (obj == null || key == null) return null;

        if (obj instanceof Value v) {
            try {
                if (v.hasMember(key)) {
                    Value m = v.getMember(key);
                    if (m == null || m.isNull()) return null;
                    return m;
                }

                // numeric index access for array-like Values
                // allow key = "0", "1", ...
                if (isIntString(key) && v.hasArrayElements()) {
                    long idx = Long.parseLong(key);
                    if (idx >= 0 && idx < v.getArraySize()) {
                        Value e = v.getArrayElement(idx);
                        if (e == null || e.isNull()) return null;
                        return e;
                    }
                }
            } catch (Throwable ignored) {}
            return null;
        }

        if (obj instanceof Map<?, ?> m) {
            try {
                Object v = m.get(key);
                return v;
            } catch (Throwable ignored) {
                return null;
            }
        }

        return null;
    }

    /**
     * Dot-path access:
     *  memberPath(data, "player.hp") -> data.player.hp
     *  memberPath(data, "items.0.name") -> items[0].name
     */
    static Object memberPath(Object obj, String path) {
        if (obj == null || path == null) return null;
        String p = path.trim();
        if (p.isEmpty()) return null;

        Object cur = obj;
        int i = 0;
        int n = p.length();

        while (i < n) {
            int dot = p.indexOf('.', i);
            String key = (dot < 0) ? p.substring(i) : p.substring(i, dot);
            key = key.trim();
            if (key.isEmpty()) return null;

            cur = member(cur, key);
            if (cur == null) return null;

            if (dot < 0) break;
            i = dot + 1;
        }

        return cur;
    }

    // -----------------------------
    // Type checks
    // -----------------------------

    static boolean isNull(Object v) {
        if (v == null) return true;
        if (v instanceof Value val) {
            try { return val.isNull(); } catch (Throwable ignored) {}
        }
        return false;
    }

    static boolean isNumber(Object v) {
        if (v == null) return false;
        if (v instanceof Number) return true;
        if (v instanceof Value val) {
            try { return val.isNumber(); } catch (Throwable ignored) {}
        }
        // allow numeric strings
        if (v instanceof String s) return tryParseDouble(s) != null;
        return false;
    }

    static boolean isString(Object v) {
        if (v == null) return false;
        if (v instanceof String) return true;
        if (v instanceof Value val) {
            try { return val.isString(); } catch (Throwable ignored) {}
        }
        return false;
    }

    static boolean isBoolean(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean) return true;
        if (v instanceof Value val) {
            try { return val.isBoolean(); } catch (Throwable ignored) {}
        }
        if (v instanceof String s) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            return "true".equals(t) || "false".equals(t) || "1".equals(t) || "0".equals(t) ||
                    "yes".equals(t)  || "no".equals(t)    || "on".equals(t) || "off".equals(t);
        }
        return false;
    }

    // Back-compat alias (some code may call isBool)
    static boolean isBool(Object v) {
        return isBoolean(v);
    }

    static boolean isMapLike(Object v) {
        if (v == null) return false;
        if (v instanceof Map) return true;
        if (v instanceof Value val) {
            try { return val.hasMembers() || val.hasArrayElements(); } catch (Throwable ignored) {}
        }
        return false;
    }

    // -----------------------------
    // Numeric parsing
    // -----------------------------

    static double asNum(Object v, double def) {
        if (v == null) return def;

        if (v instanceof Number n) return n.doubleValue();

        if (v instanceof Value val) {
            try {
                if (val.isNumber()) return val.asDouble();
                if (val.isBoolean()) return val.asBoolean() ? 1.0 : 0.0;
                if (val.isString()) {
                    Double d = tryParseDouble(val.asString());
                    return d != null ? d : def;
                }
            } catch (Throwable ignored) {}
        }

        if (v instanceof String s) {
            Double d = tryParseDouble(s);
            return d != null ? d : def;
        }

        if (v instanceof Boolean b) return b ? 1.0 : 0.0;

        return def;
    }

    static float asFloat(Object v, float def) {
        double d = asNum(v, def);
        if (!Double.isFinite(d)) return def;
        return (float) d;
    }

    static int asInt(Object v, int def) {
        if (v == null) return def;

        if (v instanceof Number n) return n.intValue();

        if (v instanceof Value val) {
            try {
                if (val.isNumber()) return (int) Math.round(val.asDouble());
                if (val.isBoolean()) return val.asBoolean() ? 1 : 0;
                if (val.isString()) {
                    Double d = tryParseDouble(val.asString());
                    return d != null ? (int) Math.round(d) : def;
                }
            } catch (Throwable ignored) {}
        }

        if (v instanceof String s) {
            Double d = tryParseDouble(s);
            return d != null ? (int) Math.round(d) : def;
        }

        if (v instanceof Boolean b) return b ? 1 : 0;

        return def;
    }

    static long asLong(Object v, long def) {
        if (v == null) return def;

        if (v instanceof Number n) return n.longValue();

        if (v instanceof Value val) {
            try {
                if (val.isNumber()) return Math.round(val.asDouble());
                if (val.isBoolean()) return val.asBoolean() ? 1L : 0L;
                if (val.isString()) {
                    Double d = tryParseDouble(val.asString());
                    return d != null ? Math.round(d) : def;
                }
            } catch (Throwable ignored) {}
        }

        if (v instanceof String s) {
            Double d = tryParseDouble(s);
            return d != null ? Math.round(d) : def;
        }

        if (v instanceof Boolean b) return b ? 1L : 0L;

        return def;
    }

    static boolean asBool(Object v, boolean def) {
        if (v == null) return def;

        if (v instanceof Boolean b) return b;

        if (v instanceof Number n) return n.doubleValue() != 0.0;

        if (v instanceof Value val) {
            try {
                if (val.isBoolean()) return val.asBoolean();
                if (val.isNumber()) return val.asDouble() != 0.0;
                if (val.isString()) return parseBool(val.asString(), def);
            } catch (Throwable ignored) {}
        }

        if (v instanceof String s) return parseBool(s, def);

        return def;
    }

    static String asString(Object v, String def) {
        if (v == null) return def;

        if (v instanceof String s) return s;

        if (v instanceof Value val) {
            try {
                if (val.isString()) return val.asString();
                if (val.isNumber()) return stripTrailingZeros(val.asDouble());
                if (val.isBoolean()) return String.valueOf(val.asBoolean());
                // useful fallback for objects: toString()
                return safeToString(val);
            } catch (Throwable ignored) {}
        }

        if (v instanceof Number n) return stripTrailingZeros(n.doubleValue());
        if (v instanceof Boolean b) return String.valueOf(b);

        return safeToString(v, def);
    }

    // -----------------------------
    // Color parsing
    // -----------------------------

    /**
     * Supports:
     * - Hex: "#RGB", "#RGBA", "#RRGGBB", "#RRGGBBAA", "0xRRGGBB", "0xRRGGBBAA"
     * - rgb(r,g,b), rgba(r,g,b,a)
     * - Named: "white", "black", "red", "green", "blue", "cyan", "magenta", "yellow", "transparent"
     * - Object/map/value: {r,g,b,a} in 0..1 OR 0..255 (also {x,y,z,w})
     */
    static ColorRGBA asColor(Object v, ColorRGBA def) {
        if (v == null) return def;

        if (v instanceof ColorRGBA c) return c.clone();

        if (v instanceof String s) {
            ColorRGBA c = parseColorString(s);
            return c != null ? c : def;
        }

        if (v instanceof Value val) {
            try {
                if (val.isString()) {
                    ColorRGBA c = parseColorString(val.asString());
                    return c != null ? c : def;
                }
                if (val.hasMembers()) {
                    return parseColorMembers(val, def);
                }
            } catch (Throwable ignored) {}
        }

        if (v instanceof Map<?, ?>) {
            return parseColorMembers(v, def);
        }

        return def;
    }

    private static ColorRGBA parseColorMembers(Object obj, ColorRGBA def) {
        Object rr = member(obj, "r");
        Object gg = member(obj, "g");
        Object bb = member(obj, "b");
        Object aa = member(obj, "a");

        // allow {x,y,z,w} too
        if (rr == null && gg == null && bb == null) {
            rr = member(obj, "x");
            gg = member(obj, "y");
            bb = member(obj, "z");
            aa = member(obj, "w");
        }

        if (rr == null && gg == null && bb == null && aa == null) return def;

        float r = asFloat(rr, def != null ? def.r : 1f);
        float g = asFloat(gg, def != null ? def.g : 1f);
        float b = asFloat(bb, def != null ? def.b : 1f);
        float a = asFloat(aa, def != null ? def.a : 1f);

        // if someone passed 0..255
        if (r > 1.01f || g > 1.01f || b > 1.01f || a > 1.01f) {
            r = clamp01(r / 255f);
            g = clamp01(g / 255f);
            b = clamp01(b / 255f);
            a = clamp01(a / 255f);
        } else {
            r = clamp01(r);
            g = clamp01(g);
            b = clamp01(b);
            a = clamp01(a);
        }

        return new ColorRGBA(r, g, b, a);
    }

    private static ColorRGBA parseColorString(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return null;

        switch (t) {
            case "white": return ColorRGBA.White.clone();
            case "black": return ColorRGBA.Black.clone();
            case "red": return ColorRGBA.Red.clone();
            case "green": return ColorRGBA.Green.clone();
            case "blue": return ColorRGBA.Blue.clone();
            case "yellow": return ColorRGBA.Yellow.clone();
            case "cyan": return ColorRGBA.Cyan.clone();
            case "magenta": return ColorRGBA.Magenta.clone();
            case "transparent": return new ColorRGBA(0, 0, 0, 0);
        }

        // rgb()/rgba()
        if (t.startsWith("rgb(") && t.endsWith(")")) {
            String inner = t.substring(4, t.length() - 1);
            return parseRgbFunc(inner, false);
        }
        if (t.startsWith("rgba(") && t.endsWith(")")) {
            String inner = t.substring(5, t.length() - 1);
            return parseRgbFunc(inner, true);
        }

        // hex
        String hex = t;
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.startsWith("0x")) hex = hex.substring(2);

        // #RGB / #RGBA
        if (hex.length() == 3 || hex.length() == 4) {
            int r = hexNibble(hex.charAt(0)); if (r < 0) return null;
            int g = hexNibble(hex.charAt(1)); if (g < 0) return null;
            int b = hexNibble(hex.charAt(2)); if (b < 0) return null;
            int a = (hex.length() == 4) ? hexNibble(hex.charAt(3)) : 15;
            if (a < 0) return null;
            // expand nibble: x -> xx
            r = r * 17; g = g * 17; b = b * 17; a = a * 17;
            return new ColorRGBA(r / 255f, g / 255f, b / 255f, a / 255f);
        }

        // #RRGGBB / #RRGGBBAA
        if (hex.length() == 6 || hex.length() == 8) {
            try {
                long n = Long.parseLong(hex, 16);
                int r = (int) ((n >> (hex.length() == 8 ? 24 : 16)) & 0xFF);
                int g = (int) ((n >> (hex.length() == 8 ? 16 : 8)) & 0xFF);
                int b = (int) ((n >> (hex.length() == 8 ? 8 : 0)) & 0xFF);
                int a = (hex.length() == 8) ? (int) (n & 0xFF) : 255;
                return new ColorRGBA(r / 255f, g / 255f, b / 255f, a / 255f);
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static ColorRGBA parseRgbFunc(String inner, boolean hasAlpha) {
        if (inner == null) return null;
        String[] parts = inner.split(",");
        if ((!hasAlpha && parts.length != 3) || (hasAlpha && parts.length != 4)) return null;

        Float r = parse255(parts[0]);
        Float g = parse255(parts[1]);
        Float b = parse255(parts[2]);
        if (r == null || g == null || b == null) return null;

        float a = 1f;
        if (hasAlpha) {
            String pa = parts[3].trim();
            Double da = tryParseDouble(pa);
            if (da == null) return null;
            a = (float) da.doubleValue();
            // allow 0..255 alpha too
            if (a > 1.01f) a = a / 255f;
            a = clamp01(a);
        }

        return new ColorRGBA(clamp01(r / 255f), clamp01(g / 255f), clamp01(b / 255f), a);
    }

    private static Float parse255(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.endsWith("%")) {
            Double d = tryParseDouble(t.substring(0, t.length() - 1));
            if (d == null) return null;
            return (float) (clamp01((float) (d / 100.0)) * 255f);
        }
        Double d = tryParseDouble(t);
        if (d == null) return null;
        return (float) d.doubleValue();
    }

    // -----------------------------
    // Vector parsing
    // -----------------------------

    static Vector2f asVec2(Object v, Vector2f def) {
        if (v == null) return def;
        if (v instanceof Vector2f vv) return vv.clone();

        Object x = member(v, "x");
        Object y = member(v, "y");
        if (x == null && y == null) {
            x = member(v, "u");
            y = member(v, "v");
        }
        if (x == null && y == null) return def;

        return new Vector2f(
                asFloat(x, def != null ? def.x : 0f),
                asFloat(y, def != null ? def.y : 0f)
        );
    }

    static Vector3f asVec3(Object v, Vector3f def) {
        if (v == null) return def;
        if (v instanceof Vector3f vv) return vv.clone();

        Object x = member(v, "x");
        Object y = member(v, "y");
        Object z = member(v, "z");
        if (x == null && y == null && z == null) return def;

        return new Vector3f(
                asFloat(x, def != null ? def.x : 0f),
                asFloat(y, def != null ? def.y : 0f),
                asFloat(z, def != null ? def.z : 0f)
        );
    }

    // -----------------------------
    // Helpers
    // -----------------------------

    private static boolean parseBool(String s, boolean def) {
        if (s == null) return def;
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return def;
        if ("true".equals(t) || "1".equals(t) || "yes".equals(t) || "y".equals(t) || "on".equals(t)) return true;
        if ("false".equals(t) || "0".equals(t) || "no".equals(t) || "n".equals(t) || "off".equals(t)) return false;
        return def;
    }

    private static Double tryParseDouble(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try { return Double.parseDouble(t); } catch (Throwable ignored) { return null; }
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static boolean isIntString(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static int hexNibble(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return 10 + (c - 'a');
        if (c >= 'A' && c <= 'F') return 10 + (c - 'A');
        return -1;
    }

    private static String stripTrailingZeros(double d) {
        if (!Double.isFinite(d)) return "0";
        long li = (long) d;
        if (d == (double) li) return Long.toString(li);
        String s = Double.toString(d);
        // keep as-is (fast); avoid heavy DecimalFormat
        return s;
    }

    private static String safeToString(Object o, String def) {
        try {
            String s = String.valueOf(o);
            return s != null ? s : def;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static String safeToString(Value v) {
        try {
            // Value.toString() can be expensive but is fine for HUD occasional debug.
            return v.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }
}