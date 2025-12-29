package org.foxesworld.kalitech.engine.api.impl.material;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jme3.asset.AssetManager;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.foxesworld.kalitech.engine.api.util.JsValueUtils.*;

/**
 * Thread-friendly material param applier with STRICT safety:
 * - If render-thread scheduler is NOT available -> NO async swap, only SYNC load (to prevent white materials)
 * - If scheduler is available -> placeholder + async load + render-thread swap
 */
public final class MaterialUtils {
    private static final Logger log = LogManager.getLogger(MaterialUtils.class);

    private MaterialUtils() {}

    // ---------------------------------
    // Debug / switches
    // ---------------------------------

    private static volatile boolean DEBUG = false;

    /** If true: always sync load textures (disables async entirely). */
    private static volatile boolean FORCE_SYNC_TEXTURES = false;

    /** If true: async texture load is allowed only when we have a CONFIRMED scheduler. */
    private static volatile boolean REQUIRE_SCHEDULER_FOR_ASYNC = true;

    public static void setDebug(boolean enabled) {
        DEBUG = enabled;
        log.warn("[MAT] debug=" + (DEBUG ? "ON" : "OFF"));
    }

    public static void forceSyncTextures(boolean enabled) {
        FORCE_SYNC_TEXTURES = enabled;
        log.warn("[MAT] forceSyncTextures=" + (enabled ? "ON" : "OFF"));
    }

    public static void requireSchedulerForAsync(boolean enabled) {
        REQUIRE_SCHEDULER_FOR_ASYNC = enabled;
        log.warn("[MAT] requireSchedulerForAsync=" + (enabled ? "ON" : "OFF"));
    }

    // ---------------------------------
    // Engine hooks (render thread)
    // ---------------------------------

    private static volatile AssetManager assets;

    /**
     * Render-thread scheduler hook.
     * We treat it as "confirmed" only if we actually bound to a real engine method.
     */
    private static volatile RenderThreadScheduler scheduler;
    private static volatile boolean schedulerConfirmed = false;

    private record RenderThreadScheduler(Consumer<Runnable> enqueue) {
        void onRenderThread(Runnable r) { enqueue.accept(r); }
    }

    /**
     * Must be called from MaterialApiImpl once.
     */
    static void init(Object engineApiImpl, AssetManager am) {
        assets = am;
        scheduler = null;
        schedulerConfirmed = false;

        if (assets == null) {
            log.error("[MAT] init: AssetManager is null");
            return;
        }

        // Bind scheduler using reflection to avoid hard dependency,
        // but CONFIRM only if we found a real method.
        try {
            Method m = engineApiImpl.getClass().getMethod("runOnRenderThread", Runnable.class);
            scheduler = new RenderThreadScheduler(r -> {
                try { m.invoke(engineApiImpl, r); } catch (Throwable e) {
                    log.error("[MAT] runOnRenderThread invoke failed: {}", e.toString(), e);
                }
            });
            schedulerConfirmed = true;
            log.info("[MAT] scheduler bound: EngineApiImpl.runOnRenderThread(Runnable)");
        } catch (Throwable ignored) {}

        if (!schedulerConfirmed) {
            try {
                Method m = engineApiImpl.getClass().getMethod("enqueue", Runnable.class);
                scheduler = new RenderThreadScheduler(r -> {
                    try { m.invoke(engineApiImpl, r); } catch (Throwable e) {
                        log.error("[MAT] enqueue invoke failed: {}", e.toString(), e);
                    }
                });
                schedulerConfirmed = true;
                log.info("[MAT] scheduler bound: EngineApiImpl.enqueue(Runnable)");
            } catch (Throwable ignored) {}
        }

        if (!schedulerConfirmed) {
            // No scheduler. We WILL NOT do async swap.
            log.warn("[MAT] scheduler NOT found (runOnRenderThread/enqueue). " +
                    "Async texture swaps DISABLED -> using SYNC texture load fallback.");
        }

        if (DEBUG) {
            log.info("[MAT] init ok. assets=true schedulerConfirmed={}", schedulerConfirmed);
        }
    }

    private static boolean canAsyncTextures() {
        if (FORCE_SYNC_TEXTURES) return false;
        if (assets == null) return false;
        if (!REQUIRE_SCHEDULER_FOR_ASYNC) return true; // you can allow unsafe mode if you want
        return schedulerConfirmed && scheduler != null;
    }

    // ---------------------------------
    // Background texture loading
    // ---------------------------------

    private static final ExecutorService TEX_IO = Executors.newFixedThreadPool(
            Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() / 2)),
            r -> {
                Thread t = new Thread(r, "kalitech-tex-io");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
    );

    private static final Cache<TextureKey, Texture> textureCache = Caffeine.newBuilder()
            .maximumSize(8192)
            .softValues()
            .recordStats()
            .build();

    /** Dedupe in-flight loads. */
    private static final ConcurrentHashMap<TextureKey, CompletableFuture<Texture>> inFlight = new ConcurrentHashMap<>();

    // ---------------------------------
    // Placeholder
    // ---------------------------------

    private static volatile Texture PLACEHOLDER;

    private static Texture placeholder() {
        Texture p = PLACEHOLDER;
        if (p != null) return p;

        try {
            ByteBuffer buf = ByteBuffer.allocateDirect(4);
            buf.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF);
            buf.flip();

            Image img = new Image(Image.Format.RGBA8, 1, 1, buf);
            Texture2D tex = new Texture2D(img);
            tex.setWrap(Texture.WrapMode.EdgeClamp);
            tex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
            tex.setMagFilter(Texture.MagFilter.Bilinear);

            PLACEHOLDER = tex;
            log.info("[MAT] placeholder created");
            return tex;
        } catch (Throwable e) {
            log.error("[MAT] placeholder create failed: {}", e.toString(), e);
            return null;
        }
    }

    // ---------------------------------
    // Data records
    // ---------------------------------

    private record TextureKey(
            String path,
            Texture.WrapMode wrap,
            Texture.MinFilter min,
            Texture.MagFilter mag,
            int aniso,
            int hash
    ) {
        static TextureKey of(TextureDesc td) {
            String p = td.texture().trim();
            Texture.WrapMode w = td.wrap();
            Texture.MinFilter mi = td.minFilter();
            Texture.MagFilter ma = td.magFilter();
            int a = Math.max(0, td.anisotropy());
            int h = Objects.hash(p, w, mi, ma, a);
            return new TextureKey(p, w, mi, ma, a, h);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof TextureKey k)) return false;
            return hash == k.hash &&
                    aniso == k.aniso &&
                    Objects.equals(path, k.path) &&
                    wrap == k.wrap &&
                    min == k.min &&
                    mag == k.mag;
        }
    }

    public record TileWorld(float x, float z) {}

    public record TextureDesc(
            String texture,
            Texture.WrapMode wrap,
            Texture.MinFilter minFilter,
            Texture.MagFilter magFilter,
            int anisotropy,
            TileWorld tileWorld
    ) {}

    public record ParsedTex(String path, Texture.WrapMode wrap) {}

    // ---------------------------------
    // Public API: apply param (safe)
    // ---------------------------------

    public static boolean applyParamAsync(Material m, String name, Value v) {
        if (m == null || name == null || name.isBlank() || isNull(v)) return false;

        MatParam declared = m.getParam(name);
        if (declared != null && applyByDeclared(m, name, declared, v)) return true;

        // Inference
        TextureDesc td = parseTextureDesc(v);
        if (td != null) { setTextureSafe(m, name, td); return true; }

        ColorRGBA c = parseColor(v);
        if (c != null) { m.setColor(name, c); return true; }

        Vector2f v2 = parseVec2(v);
        if (v2 != null) { m.setVector2(name, v2); return true; }

        Vector3f v3 = parseVec3(v);
        if (v3 != null) { m.setVector3(name, v3); return true; }

        Vector4f v4 = parseVec4(v);
        if (v4 != null) { m.setVector4(name, v4); return true; }

        if (v.isBoolean()) { m.setBoolean(name, v.asBoolean()); return true; }
        if (v.isNumber())  { m.setFloat(name, (float) v.asDouble()); return true; }

        if (v.isString()) {
            ParsedTex pt = parseTextureShorthand(v.asString());
            if (pt.path() != null && !pt.path().isBlank()) {
                setTextureSafe(m, name, new TextureDesc(pt.path(), pt.wrap(), null, null, 0, null));
                return true;
            }
        }

        if (DEBUG) log.warn("[MAT] applyParam: could not apply param='{}' type={}", name, safeType(v));
        return false;
    }

    private static boolean applyByDeclared(Material m, String name, MatParam declared, Value v) {
        String type = declared.getVarType().name();
        try {
            switch (type) {
                case "Boolean" -> {
                    if (v.isBoolean()) { m.setBoolean(name, v.asBoolean()); return true; }
                    if (v.isNumber())  { m.setBoolean(name, v.asDouble() != 0.0); return true; }
                }
                case "Int" -> { if (v.isNumber()) { m.setInt(name, (int) Math.round(v.asDouble())); return true; } }
                case "Float" -> { if (v.isNumber()) { m.setFloat(name, (float) v.asDouble()); return true; } }
                case "Color" -> { ColorRGBA c = parseColor(v); if (c != null) { m.setColor(name, c); return true; } }
                case "Vector2" -> { Vector2f vv = parseVec2(v); if (vv != null) { m.setVector2(name, vv); return true; } }
                case "Vector3" -> { Vector3f vv = parseVec3(v); if (vv != null) { m.setVector3(name, vv); return true; } }
                case "Vector4" -> { Vector4f vv = parseVec4(v); if (vv != null) { m.setVector4(name, vv); return true; } }
                case "Texture2D", "Texture3D", "TextureCubeMap" -> {
                    TextureDesc td = parseTextureDesc(v);
                    if (td != null) { setTextureSafe(m, name, td); return true; }
                }
            }
        } catch (Throwable e) {
            log.warn("[MAT] applyByDeclared failed param='{}' declaredType={} err={}", name, type, e.toString());
        }
        return false;
    }

    // ---------------------------------
    // Texture apply (STRICT SAFE)
    // ---------------------------------

    private static void setTextureSafe(Material m, String param, TextureDesc td) {
        if (td == null || td.texture() == null || td.texture().isBlank()) {
            log.warn("[MAT] setTexture: empty texture param='{}'", param);
            return;
        }
        if (assets == null) {
            log.error("[MAT] setTexture: AssetManager is null (init not called?) param='{}' tex='{}'", param, td.texture());
            return;
        }

        final String path = td.texture().trim();
        final boolean hasOverrides =
                td.wrap() != null ||
                        td.minFilter() != null ||
                        td.magFilter() != null ||
                        td.anisotropy() > 0;

        // 1) If async is not allowed -> SYNC load (prevents white)
        if (!canAsyncTextures()) {
            if (DEBUG) log.warn("[MAT] setTexture SYNC fallback param='{}' tex='{}' overrides={}", param, path, hasOverrides ? 1 : 0);
            try {
                Texture t = assets.loadTexture(path);
                if (hasOverrides) t = cloneWithOverrides(t, td);
                m.setTexture(param, t);
                return;
            } catch (Throwable e) {
                log.error("[MAT] SYNC texture load FAILED: param='{}' tex='{}' err={}", param, path, e.toString(), e);
                Texture ph = placeholder();
                if (ph != null) {
                    try { m.setTexture(param, ph); } catch (Throwable ignored) {}
                }
                return;
            }
        }

        // 2) Async path
        Texture ph = placeholder();
        if (ph != null) {
            try { m.setTexture(param, ph); } catch (Throwable ignored) {}
        }

        TextureKey key = TextureKey.of(td);

        Texture cached = textureCache.getIfPresent(key);
        if (cached != null) {
            if (DEBUG) log.info("[MAT] tex cache HIT param='{}' tex='{}'", param, path);
            scheduler.onRenderThread(() -> safeSetTex(m, param, cached, "cache-hit"));
            return;
        }

        CompletableFuture<Texture> fut = inFlight.computeIfAbsent(key, k -> {
            log.info("[MAT] tex inflight start: {}", k.path);

            return CompletableFuture.supplyAsync(() -> {
                try {
                    Texture base = assets.loadTexture(path);
                    if (!hasOverrides) return base;
                    return cloneWithOverrides(base, td);
                } catch (Throwable e) {
                    log.error("[MAT] ASYNC texture load FAILED: param='{}' tex='{}' err={}", param, path, e.toString(), e);
                    throw e;
                }
            }, TEX_IO).whenComplete((tex, err) -> {
                inFlight.remove(k);
                if (err != null) {
                    log.warn("[MAT] tex inflight complete: FAILED tex='{}' err={}", k.path, err.toString());
                    return;
                }
                if (tex != null) {
                    textureCache.put(k, tex);
                    log.info("[MAT] tex inflight complete: OK tex='{}'", k.path);
                } else {
                    log.warn("[MAT] tex inflight complete: NULL tex='{}'", k.path);
                }
            });
        });

        fut.thenAccept(tex -> {
            if (tex == null) {
                log.warn("[MAT] ASYNC result NULL (keeping placeholder) param='{}' tex='{}'", param, path);
                return;
            }
            scheduler.onRenderThread(() -> safeSetTex(m, param, tex, "async-ready"));
        }).exceptionally(err -> {
            log.warn("[MAT] ASYNC swap skipped param='{}' tex='{}' err={}", param, path, err.toString());
            return null;
        });
    }

    private static Texture cloneWithOverrides(Texture base, TextureDesc td) {
        if (base == null) return null;

        boolean need =
                td.wrap() != null ||
                        td.minFilter() != null ||
                        td.magFilter() != null ||
                        td.anisotropy() > 0;

        if (!need) return base;

        try {
            Texture copy = base.clone();
            if (td.wrap() != null) copy.setWrap(td.wrap());
            if (td.minFilter() != null) copy.setMinFilter(td.minFilter());
            if (td.magFilter() != null) copy.setMagFilter(td.magFilter());
            if (td.anisotropy() > 0) copy.setAnisotropicFilter(td.anisotropy());
            return copy;
        } catch (Throwable e) {
            log.warn("[MAT] texture clone overrides failed (use base) err={}", e.toString());
            return base;
        }
    }

    private static void safeSetTex(Material m, String param, Texture t, String reason) {
        try {
            m.setTexture(param, t);
            if (DEBUG) log.info("[MAT] texture set OK param='{}' reason={} texClass={}", param, reason, t.getClass().getSimpleName());
        } catch (Throwable e) {
            log.error("[MAT] texture set FAILED param='{}' reason={} err={}", param, reason, e.toString(), e);
        }
    }

    // ---------------------------------
    // Parsing helpers
    // ---------------------------------

    private static boolean isNull(Value v) { return v == null || v.isNull(); }

    private static String safeType(Value v) {
        if (v == null) return "null";
        try {
            if (v.isNull()) return "null";
            if (v.isBoolean()) return "boolean";
            if (v.isNumber()) return "number";
            if (v.isString()) return "string";
            if (v.hasArrayElements()) return "array";
            if (v.hasMembers()) return "object";
        } catch (Throwable ignored) {}
        return "unknown";
    }

    public static ParsedTex parseTextureShorthand(String s) {
        if (s == null) return new ParsedTex(null, null);
        String t = s.trim();
        if (t.isEmpty()) return new ParsedTex(null, null);

        String[] parts = t.split("\\|");
        String path = parts[0].trim();
        Texture.WrapMode wrap = null;
        if (parts.length >= 2) wrap = parseWrap(parts[1].trim());
        return new ParsedTex(path, wrap);
    }

    public static Texture.WrapMode parseWrap(String s) {
        if (s == null || s.isBlank()) return null;
        String w = s.trim().toLowerCase();

        if (w.equals("repeat") || w.equals("tile") || w.equals("tiled"))
            return Texture.WrapMode.Repeat;

        if (w.equals("clamp") || w.equals("edge") || w.equals("edgeclamp") || w.equals("edge_clamp") || w.equals("clamp_to_edge"))
            return Texture.WrapMode.EdgeClamp;

        if (w.equals("mirror") || w.equals("mirrored") || w.equals("mirroredrepeat") || w.equals("mirrored_repeat"))
            return Texture.WrapMode.MirroredRepeat;

        for (Texture.WrapMode wm : Texture.WrapMode.values()) {
            if (wm.name().equalsIgnoreCase(s.trim())) return wm;
        }
        return null;
    }

    public static Texture.MinFilter parseMinFilter(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        for (Texture.MinFilter f : Texture.MinFilter.values()) {
            if (f.name().equalsIgnoreCase(t)) return f;
        }
        String k = t.toLowerCase();
        if (k.equals("nearest")) return Texture.MinFilter.NearestNoMipMaps;
        if (k.equals("bilinear")) return Texture.MinFilter.BilinearNoMipMaps;
        if (k.equals("trilinear")) return Texture.MinFilter.Trilinear;
        return null;
    }

    public static Texture.MagFilter parseMagFilter(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        for (Texture.MagFilter f : Texture.MagFilter.values()) {
            if (f.name().equalsIgnoreCase(t)) return f;
        }
        String k = t.toLowerCase();
        if (k.equals("nearest")) return Texture.MagFilter.Nearest;
        if (k.equals("bilinear") || k.equals("linear")) return Texture.MagFilter.Bilinear;
        return null;
    }

    public static TextureDesc parseTextureDesc(Value v) {
        if (v == null || v.isNull()) return null;

        if (v.isString()) {
            ParsedTex pt = parseTextureShorthand(v.asString());
            if (pt.path() == null || pt.path().isBlank()) return null;
            return new TextureDesc(pt.path(), pt.wrap(), null, null, 0, null);
        }

        if (v.hasMembers() && v.hasMember("texture")) {
            String path = str(v, "texture", null);
            if (path == null || path.isBlank()) return null;

            String wrapS = str(v, "wrap", null);
            if (wrapS == null) wrapS = str(v, "type", null);

            String minS = str(v, "min", null);
            if (minS == null) minS = str(v, "minFilter", null);

            String magS = str(v, "mag", null);
            if (magS == null) magS = str(v, "magFilter", null);

            int aniso = 0;
            if (v.hasMember("anisotropy")) {
                try {
                    Value a = v.getMember("anisotropy");
                    if (a != null && !a.isNull() && a.isNumber()) aniso = Math.max(0, a.asInt());
                } catch (Throwable ignored) {}
            }

            TileWorld tw = null;
            if (v.hasMember("tileWorld")) {
                try {
                    Value t = v.getMember("tileWorld");
                    if (t != null && !t.isNull() && t.hasMembers()) {
                        float x = (float) num(t, "x", 0.0);
                        float z = (float) num(t, "z", 0.0);
                        if (x > 0f && z > 0f) tw = new TileWorld(x, z);
                    }
                } catch (Throwable ignored) {}
            }

            return new TextureDesc(
                    path.trim(),
                    parseWrap(wrapS),
                    parseMinFilter(minS),
                    parseMagFilter(magS),
                    aniso,
                    tw
            );
        }

        return null;
    }

    private static ColorRGBA parseColor(Value v) {
        if (v == null || v.isNull()) return null;

        if (v.hasArrayElements()) {
            long n = v.getArraySize();
            if (n >= 3) {
                float r = (float) v.getArrayElement(0).asDouble();
                float g = (float) v.getArrayElement(1).asDouble();
                float b = (float) v.getArrayElement(2).asDouble();
                float a = (n >= 4) ? (float) v.getArrayElement(3).asDouble() : 1f;
                return new ColorRGBA(r, g, b, a);
            }
        }

        if (v.hasMembers() && (v.hasMember("r") || v.hasMember("g") || v.hasMember("b"))) {
            float r = (float) num(v, "r", 1.0);
            float g = (float) num(v, "g", 1.0);
            float b = (float) num(v, "b", 1.0);
            float a = (float) num(v, "a", 1.0);
            return new ColorRGBA(r, g, b, a);
        }

        return null;
    }

    private static Vector2f parseVec2(Value v) {
        if (v == null || v.isNull()) return null;
        if (v.hasArrayElements() && v.getArraySize() >= 2) {
            return new Vector2f(
                    (float) v.getArrayElement(0).asDouble(),
                    (float) v.getArrayElement(1).asDouble()
            );
        }
        if (v.hasMembers() && (v.hasMember("x") || v.hasMember("y"))) {
            return new Vector2f((float) num(v, "x", 0.0), (float) num(v, "y", 0.0));
        }
        return null;
    }

    private static Vector3f parseVec3(Value v) {
        if (v == null || v.isNull()) return null;
        if (v.hasArrayElements() && v.getArraySize() >= 3) {
            return new Vector3f(
                    (float) v.getArrayElement(0).asDouble(),
                    (float) v.getArrayElement(1).asDouble(),
                    (float) v.getArrayElement(2).asDouble()
            );
        }
        if (v.hasMembers() && (v.hasMember("x") || v.hasMember("y") || v.hasMember("z"))) {
            return new Vector3f(
                    (float) num(v, "x", 0.0),
                    (float) num(v, "y", 0.0),
                    (float) num(v, "z", 0.0)
            );
        }
        return null;
    }

    private static Vector4f parseVec4(Value v) {
        if (v == null || v.isNull()) return null;
        if (v.hasArrayElements() && v.getArraySize() >= 4) {
            return new Vector4f(
                    (float) v.getArrayElement(0).asDouble(),
                    (float) v.getArrayElement(1).asDouble(),
                    (float) v.getArrayElement(2).asDouble(),
                    (float) v.getArrayElement(3).asDouble()
            );
        }
        if (v.hasMembers() && (v.hasMember("x") || v.hasMember("y") || v.hasMember("z") || v.hasMember("w"))) {
            return new Vector4f(
                    (float) num(v, "x", 0.0),
                    (float) num(v, "y", 0.0),
                    (float) num(v, "z", 0.0),
                    (float) num(v, "w", 1.0)
            );
        }
        return null;
    }

    // ---------------------------------
    // Unknown-param heuristic
    // ---------------------------------

    public static boolean isProbablyUnknownParam(Material m, String name) {
        if (m == null || name == null || name.isBlank()) return false;
        if (name.equals("Time") || name.equals("T") || name.equals("Debug")) return false;

        try {
            Object def = m.getMaterialDef();
            if (def == null) return false;
            Method meth = def.getClass().getMethod("getMaterialParam", String.class);
            Object mp = meth.invoke(def, name);
            return mp == null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}