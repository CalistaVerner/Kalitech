/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.asset.AssetManager
 *  com.jme3.material.Material
 *  com.jme3.texture.Texture$MagFilter
 *  com.jme3.texture.Texture$MinFilter
 *  com.jme3.texture.Texture$WrapMode
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.material;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.texture.Texture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.material.MaterialParamApplier;
import org.foxesworld.kalitech.engine.modules.material.MaterialParsers;
import org.foxesworld.kalitech.engine.modules.material.MaterialTextureLoader;
import org.foxesworld.kalitech.engine.modules.material.MaterialTypes;
import org.foxesworld.kalitech.engine.modules.material.MaterialUnknownParam;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

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
        if (enabled) {
            log.info("[MAT] debug=ON");
        }
    }

    public static void forceSyncTextures(boolean enabled) {
        FORCE_SYNC_TEXTURES = enabled;
        MaterialTextureLoader.setForceSync(enabled);
        log.warn("[MAT] forceSyncTextures={}", (Object)(enabled ? "ON" : "OFF"));
    }

    public static void requireSchedulerForAsync(boolean enabled) {
        REQUIRE_SCHEDULER_FOR_ASYNC = enabled;
        MaterialTextureLoader.setRequireSchedulerForAsync(enabled);
        log.warn("[MAT] requireSchedulerForAsync={}", (Object)(enabled ? "ON" : "OFF"));
    }

    public static void init(Object engineApiImpl, AssetManager am) {
        MaterialTextureLoader.init(engineApiImpl, am);
    }

    public static boolean applyParamAsync(Material m, String name, LuaValueRef v) {
        return MaterialParamApplier.applyParamAsync(m, name, v);
    }

    public static MaterialTypes.ParsedTex parseTextureShorthand(String s) {
        return MaterialParsers.parseTextureShorthand(s);
    }

    public static Texture.WrapMode parseWrap(String s) {
        return MaterialParsers.parseWrap(s);
    }

    public static Texture.MinFilter parseMinFilter(String s) {
        return MaterialParsers.parseMinFilter(s);
    }

    public static Texture.MagFilter parseMagFilter(String s) {
        return MaterialParsers.parseMagFilter(s);
    }

    public static MaterialTypes.TextureDesc parseTextureDesc(LuaValueRef v) {
        return MaterialParsers.parseTextureDesc(v);
    }

    public static boolean isProbablyUnknownParam(Material m, String name) {
        return MaterialUnknownParam.isProbablyUnknownParam(m, name);
    }

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

