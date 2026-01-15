// FILE: org/foxesworld/kalitech/engine/modules/render/sky/SkyTextureUtil.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.sky;

import com.jme3.texture.Texture;

/**
 * Texture configuration tailored for sky rendering stability.
 */
public final class SkyTextureUtil {

    private static final String SKY_ROOT = "Textures/Sky/";
    private static final String SKY_CLOUDS_ROOT = "Textures/Sky/clouds/";

    private SkyTextureUtil() {
    }

    /**
     * Resolves a sky texture asset path using the "clouds" directory convention.
     * <p>
     * Rules:
     * <ul>
     *   <li>If asset contains ':' (e.g. domain:...), it is returned as-is.</li>
     *   <li>If asset starts with "Textures/" or "MatDefs/" or "Models/", it is returned as-is.</li>
     *   <li>If asset has no slashes, it is resolved to {@code Textures/Sky/clouds/<asset>}.</li>
     *   <li>If asset starts with "clouds/", it is resolved to {@code Textures/Sky/<asset>}.</li>
     *   <li>Otherwise it is resolved to {@code Textures/Sky/<asset>}.</li>
     * </ul>
     *
     * @param asset raw asset reference (can be short name, relative path, or fully qualified)
     * @return resolved path to pass into AssetManager
     */
    public static String resolveSkyAsset(String asset) {
        if (asset == null) return null;

        String s = asset.trim();
        if (s.isEmpty()) return s;

        s = s.replace('\\', '/');

        if (s.indexOf(':') >= 0) return s;

        if (startsWithAny(s, "Textures/", "MatDefs/", "Models/")) return s;

        boolean hasSlash = s.indexOf('/') >= 0;

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

    /**
     * Configures filtering/wrap/aniso for sky textures.
     *
     * @param t    texture
     * @param cube true for cubemap textures
     */
    public static void configureSkyTexture(Texture t, boolean cube) {
        if (t == null) return;

        if (!cube) {
            t.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        } else if (t.getMinFilter() != Texture.MinFilter.NearestNoMipMaps
                && t.getMinFilter() != Texture.MinFilter.BilinearNoMipMaps) {
            t.setMinFilter(Texture.MinFilter.Trilinear);
        }

        t.setMagFilter(Texture.MagFilter.Bilinear);
        t.setWrap(Texture.WrapMode.EdgeClamp);

        if (t.getAnisotropicFilter() < 8) {
            t.setAnisotropicFilter(8);
        }
    }
}