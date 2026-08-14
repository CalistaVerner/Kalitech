/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.texture.Texture
 *  com.jme3.texture.Texture$MagFilter
 *  com.jme3.texture.Texture$MinFilter
 *  com.jme3.texture.Texture$WrapMode
 */
package org.foxesworld.kalitech.engine.modules.render.sky;

import com.jme3.texture.Texture;

public final class SkyTextureUtil {
    private static final String SKY_ROOT = "Textures/Sky/";
    private static final String SKY_CLOUDS_ROOT = "Textures/Sky/clouds/";

    private SkyTextureUtil() {
    }

    public static String resolveSkyAsset(String asset) {
        boolean hasSlash;
        if (asset == null) {
            return null;
        }
        String s = asset.trim();
        if (s.isEmpty()) {
            return s;
        }
        if ((s = s.replace('\\', '/')).indexOf(58) >= 0) {
            return s;
        }
        if (SkyTextureUtil.startsWithAny(s, "Textures/", "MatDefs/", "Models/")) {
            return s;
        }
        boolean bl = hasSlash = s.indexOf(47) >= 0;
        if (!hasSlash) {
            return SKY_CLOUDS_ROOT + s;
        }
        if (s.startsWith("clouds/")) {
            return SKY_ROOT + s;
        }
        return SKY_ROOT + s;
    }

    private static boolean startsWithAny(String s, String a, String b, String c) {
        return s.startsWith(a) || s.startsWith(b) || s.startsWith(c);
    }

    public static void configureSkyTexture(Texture t, boolean cube) {
        if (t == null) {
            return;
        }
        if (!cube) {
            t.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        } else if (t.getMinFilter() != Texture.MinFilter.NearestNoMipMaps && t.getMinFilter() != Texture.MinFilter.BilinearNoMipMaps) {
            t.setMinFilter(Texture.MinFilter.Trilinear);
        }
        t.setMagFilter(Texture.MagFilter.Bilinear);
        t.setWrap(Texture.WrapMode.EdgeClamp);
        if (t.getAnisotropicFilter() < 8) {
            t.setAnisotropicFilter(8);
        }
    }
}

