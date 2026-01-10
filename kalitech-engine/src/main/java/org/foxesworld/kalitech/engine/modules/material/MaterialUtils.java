// FILE: org/foxesworld/kalitech/engine/modules/material/MaterialUtils.java
package org.foxesworld.kalitech.engine.modules.material;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

/**
 * MaterialUtils facade (public API).
 * Internals are split into small units inside this package.
 */
public final class MaterialUtils {

    private static final Logger log = LogManager.getLogger(MaterialUtils.class);

    private static volatile boolean DEBUG = false;
    private static volatile boolean FORCE_SYNC_TEXTURES = false;
    private static volatile boolean REQUIRE_SCHEDULER_FOR_ASYNC = true;

    private MaterialUtils() {
    }

    public static void setDebug(boolean enabled) {
        DEBUG = enabled;
        MaterialParsers.setDebug(enabled);
        MaterialTextureLoader.setDebug(enabled);
        log.warn("[MAT] debug={}", (enabled ? "ON" : "OFF"));
    }

    public static void forceSyncTextures(boolean enabled) {
        FORCE_SYNC_TEXTURES = enabled;
        MaterialTextureLoader.setForceSync(enabled);
        log.warn("[MAT] forceSyncTextures={}", (enabled ? "ON" : "OFF"));
    }

    public static void requireSchedulerForAsync(boolean enabled) {
        REQUIRE_SCHEDULER_FOR_ASYNC = enabled;
        MaterialTextureLoader.setRequireSchedulerForAsync(enabled);
        log.warn("[MAT] requireSchedulerForAsync={}", (enabled ? "ON" : "OFF"));
    }

    /**
     * Must be called once from MaterialApiImpl (or engine bootstrap).
     * Can be called again (re-init) safely.
     */
    public static void init(Object engineApiImpl, AssetManager am) {
        MaterialTextureLoader.init(engineApiImpl, am);
    }

    /**
     * Apply param by declared type if exists, else infer type:
     * texture / color / vec2 / vec3 / vec4 / boolean / number / string(texture shorthand).
     */
    public static boolean applyParamAsync(Material m, String name, Value v) {
        return MaterialParamApplier.applyParamAsync(m, name, v);
    }

    // ---------------------------------------------------------------------
    // Re-export parsing helpers (keeps old call sites stable)
    // ---------------------------------------------------------------------

    public static MaterialTypes.ParsedTex parseTextureShorthand(String s) {
        return MaterialParsers.parseTextureShorthand(s);
    }

    public static com.jme3.texture.Texture.WrapMode parseWrap(String s) {
        return MaterialParsers.parseWrap(s);
    }

    public static com.jme3.texture.Texture.MinFilter parseMinFilter(String s) {
        return MaterialParsers.parseMinFilter(s);
    }

    public static com.jme3.texture.Texture.MagFilter parseMagFilter(String s) {
        return MaterialParsers.parseMagFilter(s);
    }

    public static MaterialTypes.TextureDesc parseTextureDesc(Value v) {
        return MaterialParsers.parseTextureDesc(v);
    }

    public static boolean isProbablyUnknownParam(Material m, String name) {
        return MaterialUnknownParam.isProbablyUnknownParam(m, name);
    }

    // Internal flags access
    static boolean debug() {
        return DEBUG;
    }

    static boolean forceSyncTextures() {
        return FORCE_SYNC_TEXTURES;
    }

    static boolean requireSchedulerForAsync() {
        return REQUIRE_SCHEDULER_FOR_ASYNC;
    }
}