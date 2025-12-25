// FILE: UiApiImpl.java
// Author: Calista Verner
/*
package org.foxesworld.kalitech.engine.api.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.api.UiApi;
import org.foxesworld.kalitech.engine.ui.JcefUiBackend;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class UiApiImpl implements UiApi {

    private static final Logger log = LogManager.getLogger(UiApiImpl.class);

    // Backend singleton (survives world rebuilds)
    private static final JcefUiBackend BACKEND = new JcefUiBackend();
    private static final Object BACKEND_LOCK = new Object();
    private static volatile boolean backendStarted = false;

    private final AtomicInteger nextHandle = new AtomicInteger(1);
    private final Map<Integer, JcefUiBackend.JcefSurface> surfaces = new ConcurrentHashMap<>();

    // "active" surface for 1-arg navigate/send usage
    private volatile int activeHandle = -1;

    // JS event name used for send()
    private static final String UI_EVENT = "kalitech:ui-message";

    @Override
    @HostAccess.Export
    public String ping() {
        return "ui:ok";
    }

    /** Pump Chromium in custom engine loops (call each frame or 30-60Hz).
    @Override
    @HostAccess.Export
    public void tick() {
        ensureBackend();
        try { BACKEND.tick(); } catch (Throwable ignored) {}
    }

    @Override
    @HostAccess.Export
    public int createSurface(Object cfg) {
        ensureBackend();

        final Value v = (cfg instanceof Value) ? (Value) cfg : null;

        // allocate handle first so default id matches handle deterministically
        final int handle = nextHandle.getAndIncrement();

        String id = readString(v, "id", "surface-" + handle);
        int w = clamp(readInt(v, "width", 1280), 64, 16384);
        int h = clamp(readInt(v, "height", 720), 64, 16384);
        String url = readString(v, "url", "about:blank");

        JcefUiBackend.JcefSurface s = BACKEND.createSurface(id, w, h, url);
        surfaces.put(handle, s);

        // set as active by default
        activeHandle = handle;

        log.info("[ui] createSurface handle={} id={} {}x{} url={}", handle, id, w, h, url);
        return handle;
    }

    // ------------------------------------------------------------
    // ACTIVE SURFACE HELPERS
    // ------------------------------------------------------------

    @HostAccess.Export
    public int getActive() {
        return activeHandle;
    }

    @HostAccess.Export
    public void setActive(int handle) {
        if (!surfaces.containsKey(handle)) {
            log.warn("[ui] setActive: invalid handle={}", handle);
            return;
        }
        activeHandle = handle;
    }

    @HostAccess.Export
    public boolean exists(int handle) {
        return surfaces.containsKey(handle);
    }

    // ------------------------------------------------------------
    // NAVIGATE overloads (JS-friendly arity)
    // ------------------------------------------------------------

    /** Primary: explicit handle.
    @Override
    @HostAccess.Export
    public void navigate(int handle, String url) {
        ensureBackend();

        JcefUiBackend.JcefSurface s = surfaces.get(handle);
        if (s == null) {
            log.warn("[ui] navigate: invalid handle={}", handle);
            return;
        }
        if (!s.isAlive()) {
            surfaces.remove(handle);
            if (activeHandle == handle) activeHandle = 0;
            log.warn("[ui] navigate: dead surface handle={}", handle);
            return;
        }

        String target = (url == null || url.isBlank()) ? "about:blank" : url.trim();
        try {
            s.navigate(target);
            activeHandle = handle;
            log.info("[ui] navigate handle={} -> {}", handle, target);
        } catch (Throwable t) {
            log.warn("[ui] navigate failed handle={} url={}", handle, target, t);
        }
    }


    /** Convenience: navigate active surface (JS can call ui.navigate("...")).
    @HostAccess.Export
    public void navigate(String url) {
        int h = resolveActiveHandleOrSingle();
        if (h <= 0) {
            log.warn("[ui] navigate(url): no active surface. Create surface first.");
            return;
        }
        navigate(h, url);
    }

    // ------------------------------------------------------------
    // SEND (deliver message into page as CustomEvent)
    // ------------------------------------------------------------

    /**
     * Send a message to the UI page running inside Chromium.
     * Delivery mechanism: dispatch CustomEvent on window:
     *
     * window.addEventListener("kalitech:ui-message", (e) => console.log(e.detail))
     *
     * Detail is:
     *  - if msg is object/array/primitive -> JSON
     *  - if msg is string -> that string

    @Override
    @HostAccess.Export
    public void send(int handle, Object msg) {
        ensureBackend();

        JcefUiBackend.JcefSurface s = surfaces.get(handle);
        if (s == null) {
            log.warn("[ui] send: invalid handle={}", handle);
            return;
        }

        String js = buildDispatchJs(msg);
        try {
            s.exec(js);
            activeHandle = handle;
            log.debug("[ui] send handle={} bytes={}", handle, js.length());
        } catch (Throwable t) {
            log.warn("[ui] send failed handle={}", handle, t);
        }
    }

    /** Convenience: send to active surface (JS can call ui.send({...})).
    @HostAccess.Export
    public void send(Object msg) {
        int h = resolveActiveHandleOrSingle();
        if (h <= 0) {
            log.warn("[ui] send(msg): no active surface. Create surface first.");
            return;
        }
        send(h, msg);
    }

    @HostAccess.Export
    public void destroySurface(int handle) {
        ensureBackend();

        JcefUiBackend.JcefSurface s = surfaces.remove(handle);
        if (s == null) return;

        try {
            s.destroy();
        } catch (Throwable t) {
            log.warn("[ui] destroySurface error handle={}", handle, t);
        } finally {
            if (activeHandle == handle) activeHandle = -1;
            log.info("[ui] destroySurface handle={}", handle);
        }
    }

    /** FULL shutdown (only on application exit).
    @HostAccess.Export
    public void shutdown() {
        for (Integer h : new ArrayList<>(surfaces.keySet())) {
            try { destroySurface(h); } catch (Throwable ignored) {}
        }
        surfaces.clear();

        try {
            BACKEND.close();
        } catch (Throwable t) {
            log.warn("[ui] shutdown error", t);
        } finally {
            backendStarted = false;
            activeHandle = -1;
            log.info("[ui] shutdown complete");
        }
    }

    // ---- internal ----

    private static void ensureBackend() {
        if (backendStarted) return;
        synchronized (BACKEND_LOCK) {
            if (backendStarted) return;
            try {
                BACKEND.start();
                backendStarted = true;
                log.info("[ui] backend started");
            } catch (Throwable t) {
                backendStarted = false;
                throw new RuntimeException("Failed to start UI backend", t);
            }
        }
    }

    private int resolveActiveHandleOrSingle() {
        int h = activeHandle;
        if (h > 0 && surfaces.containsKey(h)) return h;

        // fallback: if exactly 1 surface exists, use it
        if (surfaces.size() == 1) {
            h = surfaces.keySet().iterator().next();
            activeHandle = h;
            return h;
        }
        return -1;
    }

    // ---- helpers (cfg reading) ----

    private static String readString(Value v, String key, String def) {
        try {
            if (v != null && v.hasMember(key)) {
                Value m = v.getMember(key);
                if (m == null || m.isNull()) return def;
                if (m.isString()) return m.asString();
                if (m.isHostObject()) return String.valueOf(m.asHostObject());
                return m.toString();
            }
        } catch (Throwable ignored) {}
        return def;
    }

    private static int readInt(Value v, String key, int def) {
        try {
            if (v != null && v.hasMember(key)) {
                Value m = v.getMember(key);
                if (m == null || m.isNull()) return def;
                if (m.fitsInInt()) return m.asInt();
                if (m.isNumber()) return (int) m.asDouble();
            }
        } catch (Throwable ignored) {}
        return def;
    }

    private static int clamp(int x, int lo, int hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    // ---- helpers (message delivery) ----

    private static String buildDispatchJs(Object msg) {
        // We generate JS that dispatches a CustomEvent with detail.
        // detail is either a string (escaped) or JSON value.
        String detailExpr;

        if (msg instanceof Value) {
            Value v = (Value) msg;
            if (v.isString()) {
                detailExpr = "\"" + escapeJs(v.asString()) + "\"";
            } else {
                detailExpr = toJson(v);
            }
        } else if (msg instanceof String) {
            detailExpr = "\"" + escapeJs((String) msg) + "\"";
        } else if (msg == null) {
            detailExpr = "null";
        } else if (msg instanceof Number || msg instanceof Boolean) {
            detailExpr = String.valueOf(msg);
        } else {
            // best effort: treat as string
            detailExpr = "\"" + escapeJs(String.valueOf(msg)) + "\"";
        }

        // Wrap in try/catch to avoid breaking UI on injection issues.
        return ""
                + "(function(){"
                + "try{"
                + "var ev=new CustomEvent(\"" + UI_EVENT + "\",{detail:" + detailExpr + "});"
                + "window.dispatchEvent(ev);"
                + "}catch(e){/*silence}"
                + "})();";
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Minimal JSON serializer for Graal Value (objects/arrays/primitives).
     * No external deps; safe for UI message passing.

    private static String toJson(Value v) {
        try {
            if (v == null || v.isNull()) return "null";
            if (v.isBoolean()) return v.asBoolean() ? "true" : "false";
            if (v.isNumber()) {
                // Keep as double if needed, but avoid NaN/Infinity
                double d = v.asDouble();
                if (Double.isNaN(d) || Double.isInfinite(d)) return "null";
                // If fits int, render int-like (smaller JSON)
                if (v.fitsInInt()) return Integer.toString(v.asInt());
                return Double.toString(d);
            }
            if (v.isString()) return "\"" + escapeJs(v.asString()) + "\"";

            if (v.hasArrayElements()) {
                long n = v.getArraySize();
                StringBuilder sb = new StringBuilder();
                sb.append('[');
                for (long i = 0; i < n; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(toJson(v.getArrayElement(i)));
                }
                sb.append(']');
                return sb.toString();
            }

            if (v.hasMembers()) {
                StringBuilder sb = new StringBuilder();
                sb.append('{');
                boolean first = true;
                for (String k : v.getMemberKeys()) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append('"').append(escapeJs(k)).append('"').append(':');
                    sb.append(toJson(v.getMember(k)));
                }
                sb.append('}');
                return sb.toString();
            }

            // Fallback: string
            return "\"" + escapeJs(v.toString()) + "\"";
        } catch (Throwable t) {
            return "null";
        }
    }
} */