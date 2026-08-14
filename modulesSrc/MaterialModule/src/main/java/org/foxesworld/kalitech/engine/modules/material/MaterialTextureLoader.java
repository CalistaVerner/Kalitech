/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.benmanes.caffeine.cache.Cache
 *  com.github.benmanes.caffeine.cache.Caffeine
 *  com.jme3.asset.AssetManager
 *  com.jme3.material.Material
 *  com.jme3.texture.Image
 *  com.jme3.texture.Image$Format
 *  com.jme3.texture.Texture
 *  com.jme3.texture.Texture$MagFilter
 *  com.jme3.texture.Texture$MinFilter
 *  com.jme3.texture.Texture$WrapMode
 *  com.jme3.texture.Texture2D
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package org.foxesworld.kalitech.engine.modules.material;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.material.MaterialTypes;

final class MaterialTextureLoader {
    private static final Logger log = LogManager.getLogger(MaterialTextureLoader.class);
    private static final ExecutorService TEX_IO = Executors.newFixedThreadPool(Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() / 2)), r -> {
        Thread t = new Thread(r, "kalitech-tex-io");
        t.setDaemon(true);
        t.setPriority(4);
        return t;
    });
    private static final Cache<MaterialTypes.TextureKey, Texture> textureCache = Caffeine.newBuilder().maximumSize(8192L).softValues().build();
    private static final ConcurrentHashMap<MaterialTypes.TextureKey, CompletableFuture<Texture>> inFlight = new ConcurrentHashMap();
    private static final Object PLACEHOLDER_LOCK = new Object();
    private static volatile boolean DEBUG = false;
    private static volatile boolean FORCE_SYNC_TEXTURES = false;
    private static volatile boolean REQUIRE_SCHEDULER_FOR_ASYNC = true;
    private static volatile AssetManager assets;
    private static volatile MaterialTypes.RenderThreadScheduler scheduler;
    private static volatile boolean schedulerConfirmed;
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
        Method bound = MaterialTextureLoader.tryBindScheduler(engineApiImpl, "runOnRenderThread");
        if (bound == null) {
            bound = MaterialTextureLoader.tryBindScheduler(engineApiImpl, "enqueue");
        }
        if (bound == null) {
            log.warn("[MAT] scheduler NOT found (runOnRenderThread/enqueue). Async texture swaps DISABLED -> SYNC load fallback.");
        } else if (DEBUG) {
            log.info("[MAT] scheduler confirmed via {}", (Object)bound.getName());
        }
    }

    private static Method tryBindScheduler(Object engineApiImpl, String methodName) {
        try {
            Method m = engineApiImpl.getClass().getMethod(methodName, Runnable.class);
            m.setAccessible(true);
            scheduler = new MaterialTypes.RenderThreadScheduler(r -> {
                try {
                    m.invoke(engineApiImpl, r);
                }
                catch (Throwable e) {
                    log.error("[MAT] {} invoke failed: {}", (Object)methodName, (Object)e.toString(), (Object)e);
                }
            });
            schedulerConfirmed = true;
            log.info("[MAT] scheduler bound: {}.{}(Runnable)", (Object)engineApiImpl.getClass().getSimpleName(), (Object)methodName);
            return m;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean canAsyncTextures() {
        if (FORCE_SYNC_TEXTURES) {
            return false;
        }
        if (assets == null) {
            return false;
        }
        if (scheduler == null) {
            return false;
        }
        if (!REQUIRE_SCHEDULER_FOR_ASYNC) {
            return true;
        }
        return schedulerConfirmed;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static Texture placeholder() {
        Texture p = PLACEHOLDER;
        if (p != null) {
            return p;
        }
        Object object = PLACEHOLDER_LOCK;
        synchronized (object) {
            p = PLACEHOLDER;
            if (p != null) {
                return p;
            }
            try {
                ByteBuffer buf = ByteBuffer.allocateDirect(4);
                buf.put((byte)-1).put((byte)-1).put((byte)-1).put((byte)-1);
                buf.flip();
                Image img = new Image(Image.Format.RGBA8, 1, 1, buf);
                Texture2D tex = new Texture2D(img);
                tex.setWrap(Texture.WrapMode.EdgeClamp);
                tex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
                tex.setMagFilter(Texture.MagFilter.Bilinear);
                PLACEHOLDER = tex;
                log.info("[MAT] placeholder created");
                return tex;
            }
            catch (Throwable e) {
                log.error("[MAT] placeholder create failed: {}", (Object)e.toString(), (Object)e);
                return null;
            }
        }
    }

    static void setTextureSafe(Material m, String param, MaterialTypes.TextureDesc td) {
        MaterialTypes.TextureKey key;
        Texture cached;
        boolean hasOverrides;
        if (td == null || td.texture() == null || td.texture().isBlank()) {
            if (DEBUG) {
                log.warn("[MAT] setTexture: empty texture param='{}'", (Object)param);
            }
            return;
        }
        if (assets == null) {
            log.error("[MAT] setTexture: AssetManager is null (init not called?) param='{}' tex='{}'", (Object)param, (Object)td.texture());
            return;
        }
        String path = td.texture().trim();
        boolean bl = hasOverrides = td.wrap() != null || td.minFilter() != null || td.magFilter() != null || td.anisotropy() > 0;
        if (!MaterialTextureLoader.canAsyncTextures()) {
            block15: {
                if (DEBUG) {
                    log.warn("[MAT] setTexture SYNC fallback param='{}' tex='{}' overrides={}", (Object)param, (Object)path, (Object)(hasOverrides ? 1 : 0));
                }
                try {
                    Texture t = assets.loadTexture(path);
                    if (hasOverrides) {
                        t = MaterialTextureLoader.cloneWithOverrides(t, td);
                    }
                    m.setTexture(param, t);
                }
                catch (Throwable e) {
                    log.error("[MAT] SYNC texture load FAILED: param='{}' tex='{}' err={}", (Object)param, (Object)path, (Object)e.toString(), (Object)e);
                    Texture ph2 = MaterialTextureLoader.placeholder();
                    if (ph2 == null) break block15;
                    try {
                        m.setTexture(param, ph2);
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                }
            }
            return;
        }
        Texture ph = MaterialTextureLoader.placeholder();
        if (ph != null) {
            try {
                m.setTexture(param, ph);
            }
            catch (Throwable ph2) {
                // empty catch block
            }
        }
        if ((cached = textureCache.getIfPresent(key = MaterialTypes.TextureKey.of(td))) != null) {
            if (DEBUG) {
                log.info("[MAT] tex cache HIT param='{}' tex='{}'", (Object)param, (Object)path);
            }
            scheduler.onRenderThread(() -> MaterialTextureLoader.safeSetTex(m, param, cached, "cache-hit"));
            return;
        }
        CompletableFuture<Texture> fut = inFlight.computeIfAbsent(key, k -> {
            if (DEBUG) {
                log.info("[MAT] tex inflight start: {}", (Object)k.path());
            }
            return CompletableFuture.supplyAsync(() -> {
                Texture base = assets.loadTexture(path);
                if (!hasOverrides) {
                    return base;
                }
                return MaterialTextureLoader.cloneWithOverrides(base, td);
            }, TEX_IO).whenComplete((tex, err) -> {
                inFlight.remove(k);
                if (err != null) {
                    log.warn("[MAT] tex inflight complete: FAILED tex='{}' err={}", (Object)k.path(), (Object)err.toString());
                    return;
                }
                if (tex != null) {
                    textureCache.put(k, tex);
                    if (DEBUG) {
                        log.info("[MAT] tex inflight complete: OK tex='{}'", (Object)k.path());
                    }
                } else {
                    log.warn("[MAT] tex inflight complete: NULL tex='{}'", (Object)k.path());
                }
            });
        });
        fut.thenAccept(tex -> {
            if (tex == null) {
                log.warn("[MAT] ASYNC result NULL (keeping placeholder) param='{}' tex='{}'", (Object)param, (Object)path);
                return;
            }
            scheduler.onRenderThread(() -> MaterialTextureLoader.safeSetTex(m, param, tex, "async-ready"));
        }).exceptionally(err -> {
            log.warn("[MAT] ASYNC swap skipped param='{}' tex='{}' err={}", (Object)param, (Object)path, (Object)err.toString());
            return null;
        });
    }

    private static Texture cloneWithOverrides(Texture base, MaterialTypes.TextureDesc td) {
        boolean need;
        if (base == null) {
            return null;
        }
        boolean bl = need = td.wrap() != null || td.minFilter() != null || td.magFilter() != null || td.anisotropy() > 0;
        if (!need) {
            return base;
        }
        try {
            Texture copy = base.clone();
            if (td.wrap() != null) {
                copy.setWrap(td.wrap());
            }
            if (td.minFilter() != null) {
                copy.setMinFilter(td.minFilter());
            }
            if (td.magFilter() != null) {
                copy.setMagFilter(td.magFilter());
            }
            if (td.anisotropy() > 0) {
                copy.setAnisotropicFilter(td.anisotropy());
            }
            return copy;
        }
        catch (Throwable e) {
            log.warn("[MAT] texture clone overrides failed (use base) err={}", (Object)e.toString());
            return base;
        }
    }

    private static void safeSetTex(Material m, String param, Texture t, String reason) {
        try {
            m.setTexture(param, t);
            if (DEBUG) {
                log.info("[MAT] texture set OK param='{}' reason={} texClass={}", (Object)param, (Object)reason, (Object)t.getClass().getSimpleName());
            }
        }
        catch (Throwable e) {
            log.error("[MAT] texture set FAILED param='{}' reason={} err={}", (Object)param, (Object)reason, (Object)e.toString(), (Object)e);
        }
    }

    static {
        schedulerConfirmed = false;
    }
}

