package org.foxesworld.kalitech.engine.asset;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Single source of truth for reading raw bytes/text from jME assets.
 *
 * Rules:
 *  - Never assume File (can be classpath/jar/remote/etc)
 *  - Always read through AssetInfo.openStream()
 */
public final class AssetIO {

    private AssetIO() {}

    // -------------------- open --------------------

    public static AssetInfo open(AssetManager am, String name) {
        if (am == null) throw new IllegalArgumentException("AssetManager is null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Asset name is blank");
        return am.locateAsset(new AssetKey<>(name));
    }

    public static AssetInfo open(AssetManager am, AssetKey<?> key) {
        if (am == null) throw new IllegalArgumentException("AssetManager is null");
        if (key == null) throw new IllegalArgumentException("AssetKey is null");
        return am.locateAsset(key);
    }

    // -------------------- bytes --------------------

    public static byte[] readBytes(AssetManager am, String name) {
        AssetInfo info = open(am, name);
        if (info == null) throw new AssetNotFoundException("Asset not found: " + name);
        return readBytes(info);
    }

    public static byte[] readBytes(AssetInfo info) {
        if (info == null) throw new IllegalArgumentException("AssetInfo is null");
        try (InputStream in = info.openStream()) {
            if (in == null) throw new AssetNotFoundException("Asset stream is null: " + info.getKey());
            return in.readAllBytes();
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to read bytes: " + describe(info), t);
        }
    }

    // -------------------- text --------------------

    public static String readTextUtf8(AssetManager am, String name) {
        return readText(am, name, StandardCharsets.UTF_8);
    }

    public static String readText(AssetManager am, String name, Charset cs) {
        AssetInfo info = open(am, name);
        if (info == null) throw new AssetNotFoundException("Asset not found: " + name);
        return readText(info, cs);
    }

    public static String readTextUtf8(AssetInfo info) {
        return readText(info, StandardCharsets.UTF_8);
    }

    public static String readText(AssetInfo info, Charset cs) {
        if (info == null) throw new IllegalArgumentException("AssetInfo is null");
        try (InputStream in = info.openStream()) {
            if (in == null) throw new AssetNotFoundException("Asset stream is null: " + info.getKey());
            byte[] bytes = in.readAllBytes();
            return new String(bytes, cs == null ? StandardCharsets.UTF_8 : cs);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to read text: " + describe(info), t);
        }
    }

    // -------------------- debug --------------------

    public static String describe(AssetInfo info) {
        if (info == null) return "AssetInfo=null";
        String src = info.getClass().getSimpleName(); // FileAssetInfo / ClasspathAssetInfo / etc
        String key = (info.getKey() != null) ? info.getKey().getName() : "null";
        return "key='" + key + "' source=" + src;
    }
}