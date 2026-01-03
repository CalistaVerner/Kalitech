// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudSdfFontManager.java
package org.foxesworld.kalitech.engine.api.impl.hud.font;

import com.jme3.asset.AssetManager;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class HudSdfFontManager {

    private final AssetManager assets;
    private final Map<HudSdfFontAtlas.Key, HudSdfFontAtlas> cache = new ConcurrentHashMap<>();

    // defaults
    final int bakePx;
    final int spreadPx;
    final HudSdfFontAtlas.IntSet charset;

    public HudSdfFontManager(AssetManager assets) {
        this.assets = assets;

        this.bakePx = Integer.parseInt(System.getProperty("hud.ttf.bakePx", "64"));
        this.spreadPx = Integer.parseInt(System.getProperty("hud.ttf.spreadPx", "12"));

        // Default charset: ASCII + CYRILLIC (basic)
        this.charset = new HudSdfFontAtlas.IntSet(buildDefaultCharset());
    }

    public HudSdfFontAtlas get(String ttfPath) {
        int chHash = charset.hashCodeOfSet();
        var key = new HudSdfFontAtlas.Key(ttfPath, bakePx, spreadPx, chHash);
        return cache.computeIfAbsent(key, k -> HudSdfFontAtlas.build(assets, ttfPath, bakePx, spreadPx, charset));
    }

    // ASCII + basic Cyrillic
    private static int[] buildDefaultCharset() {
        int[] ascii = new int[95]; // 32..126
        for (int i = 0; i < 95; i++) ascii[i] = 32 + i;

        // Cyrillic: U+0400..U+04FF (256 chars)
        int[] cyr = new int[256];
        for (int i = 0; i < 256; i++) cyr[i] = 0x0400 + i;

        int[] out = new int[ascii.length + cyr.length];
        System.arraycopy(ascii, 0, out, 0, ascii.length);
        System.arraycopy(cyr, 0, out, ascii.length, cyr.length);

        // stable order
        Arrays.sort(out);
        return out;
    }
}