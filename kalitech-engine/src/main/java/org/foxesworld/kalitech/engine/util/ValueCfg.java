package org.foxesworld.kalitech.engine.util;

import org.graalvm.polyglot.Value;

public final class ValueCfg {
    private ValueCfg() {}

    public static boolean bool(Value cfg, String key, boolean def) {
        if (cfg == null || cfg.isNull() || !cfg.hasMember(key)) return def;
        Value v = cfg.getMember(key);
        if (v == null || v.isNull()) return def;
        try { return v.asBoolean(); } catch (Exception ignored) { return def; }
    }

    public static int i32(Value cfg, String key, int def) {
        if (cfg == null || cfg.isNull() || !cfg.hasMember(key)) return def;
        Value v = cfg.getMember(key);
        if (v == null || v.isNull()) return def;
        try { return v.asInt(); } catch (Exception ignored) { return def; }
    }

    public static double f64(Value cfg, String key, double def) {
        if (cfg == null || cfg.isNull() || !cfg.hasMember(key)) return def;
        Value v = cfg.getMember(key);
        if (v == null || v.isNull()) return def;
        try { return v.asDouble(); } catch (Exception ignored) { return def; }
    }

    public static String str(Value cfg, String key, String def) {
        if (cfg == null || cfg.isNull() || !cfg.hasMember(key)) return def;
        Value v = cfg.getMember(key);
        if (v == null || v.isNull()) return def;
        try { return v.asString(); } catch (Exception ignored) { return def; }
    }
}