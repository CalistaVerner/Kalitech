// FILE: org/foxesworld/kalitech/engine/modules/render/sky/SkyTextureUtil.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.sky;

import com.jme3.texture.Texture;

/**
 * Texture configuration tailored for sky rendering stability.
 */
public final class SkyTextureUtil {

    private SkyTextureUtil() {
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