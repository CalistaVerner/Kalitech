// FILE: org/foxesworld/kalitech/engine/modules/material/MaterialTextureLoader.java
package org.foxesworld.kalitech.engine.modules.material;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class MaterialTextureLoader {

    private static final Logger log = LogManager.getLogger(MaterialTextureLoader.class);

    private static final ExecutorService TEX_IO = Executors.newFixedThreadPool(
            Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() / 2)),
            r -> {
                Thread t = new Thread(r, "kalitech-tex-io");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
    );

    private static final Cache<MaterialTypes.TextureKey, Texture> textureCache = Caffeine.newBuilder()
            .maximumSize(8192)
            .softValues()
            .build();

    private static final ConcurrentHashMap<MaterialTypes.TextureKey, CompletableFuture<Texture>> inFlight = new ConcurrentHashMap<>();
    private static final Object PLACEHOLDER_LOCK = new Object();
    private static volatile boolean DEBUG = false;
    private static volatile boolean FORCE_SYNC_TEXTURES = false;
    private static volatile boolean REQUIRE_SCHEDULER_FOR_ASYNC = true;
    private static volatile AssetManager assets;
    private static volatile MaterialTypes.RenderThreadScheduler scheduler;
    private static volatile boolean schedulerConfirmed = false;
    private static volatile Texture PLACEHOLDER;

    private MaterialTextureLoader() {
    }

    static void setDebug(boolean enabled) {
        DEBUG = enabled;
    }

    static void setForceSync(boolean enabled) {
        FORCE_SYNC_TEXTURES = enabled;
    }

    static void setRequireSchedulerForAsync(boolean enabled) {
        REQUIRE_SCHEDULER_FOR_ASYNC = enabled;
    }

    static void init(Object engineApiImpl, AssetManager am) {
        assets = am;
        scheduler = null;
        schedulerConfirmed = false;

        if (assets == null) {
            log.error("[MAT] init: AssetManager is null");
            return;
        }

        if (engineApiImpl == null) {
            log.warn("[MAT] init: engineApiImpl is null -> scheduler NOT bound, async swaps DISABLED");
            return;
        }

        Method bound = tryBindScheduler(engineApiImpl, "runOnRenderThread");
        if (bound == null) bound = tryBindScheduler(engineApiImpl, "enqueue");

        if (bound == null) {
            log.warn("[MAT] scheduler NOT found (runOnRenderThread/enqueue). Async texture swaps DISABLED -> SYNC load fallback.");
        } else if (DEBUG) {
            log.info("[MAT] scheduler confirmed via {}", bound.getName());
        }
    }

    private static Method tryBindScheduler(Object engineApiImpl, String methodName) {
        try {
            Method m = engineApiImpl.getClass().getMethod(methodName, Runnable.class);
            m.setAccessible(true);
            scheduler = new MaterialTypes.RenderThreadScheduler(r -> {
                try {
                    m.invoke(engineApiImpl, r);
                } catch (Throwable e) {
                    log.error("[MAT] {} invoke failed: {}", methodName, e.toString(), e);
                }
            });
            schedulerConfirmed = true;
            log.info("[MAT] scheduler bound: {}.{}(Runnable)", engineApiImpl.getClass().getSimpleName(), methodName);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean canAsyncTextures() {
        if (FORCE_SYNC_TEXTURES) return false;
        if (assets == null) return false;
        if (scheduler == null) return false; // MUST have scheduler for render-thread swap
        if (!REQUIRE_SCHEDULER_FOR_ASYNC) return true;
        return schedulerConfirmed;
    }

    private static Texture placeholder() {
        Texture p = PLACEHOLDER;
        if (p != null) return p;

        synchronized (PLACEHOLDER_LOCK) {
            p = PLACEHOLDER;
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
    }

    static void setTextureSafe(Material m, String param, MaterialTypes.TextureDesc td) {
        if (td == null || td.texture() == null || td.texture().isBlank()) {
            if (DEBUG) log.warn("[MAT] setTexture: empty texture param='{}'", param);
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

        // 1) Strict SYNC fallback
        if (!canAsyncTextures()) {
            if (DEBUG)
                log.warn("[MAT] setTexture SYNC fallback param='{}' tex='{}' overrides={}", param, path, hasOverrides ? 1 : 0);
            try {
                Texture t = assets.loadTexture(path);
                if (hasOverrides) t = cloneWithOverrides(t, td);
                m.setTexture(param, t);
            } catch (Throwable e) {
                log.error("[MAT] SYNC texture load FAILED: param='{}' tex='{}' err={}", param, path, e.toString(), e);
                Texture ph = placeholder();
                if (ph != null) {
                    try {
                        m.setTexture(param, ph);
                    } catch (Throwable ignored) {
                    }
                }
            }
            return;
        }

        // 2) Async path: placeholder first
        Texture ph = placeholder();
        if (ph != null) {
            try {
                m.setTexture(param, ph);
            } catch (Throwable ignored) {
            }
        }

        MaterialTypes.TextureKey key = MaterialTypes.TextureKey.of(td);

        Texture cached = textureCache.getIfPresent(key);
        if (cached != null) {
            if (DEBUG) log.info("[MAT] tex cache HIT param='{}' tex='{}'", param, path);
            scheduler.onRenderThread(() -> safeSetTex(m, param, cached, "cache-hit"));
            return;
        }

        CompletableFuture<Texture> fut = inFlight.computeIfAbsent(key, k -> {
            if (DEBUG) log.info("[MAT] tex inflight start: {}", k.path());
            return CompletableFuture.supplyAsync(() -> {
                Texture base = assets.loadTexture(path);
                if (!hasOverrides) return base;
                return cloneWithOverrides(base, td);
            }, TEX_IO).whenComplete((tex, err) -> {
                inFlight.remove(k);
                if (err != null) {
                    log.warn("[MAT] tex inflight complete: FAILED tex='{}' err={}", k.path(), err.toString());
                    return;
                }
                if (tex != null) {
                    textureCache.put(k, tex);
                    if (DEBUG) log.info("[MAT] tex inflight complete: OK tex='{}'", k.path());
                } else {
                    log.warn("[MAT] tex inflight complete: NULL tex='{}'", k.path());
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

    private static Texture cloneWithOverrides(Texture base, MaterialTypes.TextureDesc td) {
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
            if (DEBUG)
                log.info("[MAT] texture set OK param='{}' reason={} texClass={}", param, reason, t.getClass().getSimpleName());
        } catch (Throwable e) {
            log.error("[MAT] texture set FAILED param='{}' reason={} err={}", param, reason, e.toString(), e);
        }
    }
}