// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudSvgRasterizer.java
package org.foxesworld.kalitech.engine.api.impl.hud;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SVG -> Texture2D rasterizer backed by Apache Batik.
 *
 * Usage: HudSvgRasterizer.loadSvgTexture(assetManager, "ui/icons/heart.svg", w, h)
 */
final class HudSvgRasterizer {

    private HudSvgRasterizer() {}

    // Small LRU to avoid re-transcoding every frame.
    private static final int MAX_CACHE = Integer.getInteger("hud.svg.cache", 256);

    private static final Map<String, Texture2D> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Texture2D> eldest) {
            return size() > MAX_CACHE;
        }
    };

    static Texture2D loadSvgTexture(AssetManager assets, String assetPath, int w, int h) {
        if (assets == null) throw new IllegalArgumentException("assets is null");
        if (assetPath == null || assetPath.isBlank()) throw new IllegalArgumentException("assetPath is blank");
        w = Math.max(1, w);
        h = Math.max(1, h);

        final String key = assetPath + "@" + w + "x" + h;
        synchronized (CACHE) {
            Texture2D cached = CACHE.get(key);
            if (cached != null) return cached;
        }

        BufferedImage img = transcodeToImage(assets, assetPath, w, h);
        if (img == null) return null;

        AWTLoader loader = new AWTLoader();
        Image jmeImg = loader.load(img, true);
        Texture2D tex = new Texture2D(jmeImg);

        synchronized (CACHE) {
            CACHE.put(key, tex);
        }
        return tex;
    }

    private static BufferedImage transcodeToImage(AssetManager assets, String assetPath, int w, int h) {
        try (InputStream in = openAssetStream(assets, assetPath)) {
            if (in == null) return null;

            // Batik can render directly to BufferedImage if using ImageTranscoder,
            // but PNGTranscoder is simpler and stable.
            PNGTranscoder t = new PNGTranscoder();
            t.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) w);
            t.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) h);

            TranscoderInput input = new TranscoderInput(in);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(8192, w * h / 2))) {
                TranscoderOutput output = new TranscoderOutput(baos);
                t.transcode(input, output);
                baos.flush();

                byte[] png = baos.toByteArray();
                return javax.imageio.ImageIO.read(new ByteArrayInputStream(png));
            }
        } catch (TranscoderException | java.io.IOException e) {
            return null;
        }
    }

    private static InputStream openAssetStream(AssetManager assets, String assetPath) throws java.io.IOException {
        try {
            AssetInfo info = assets.locateAsset(new AssetKey<>(assetPath));
            if (info == null) return null;
            return info.openStream();
        } catch (Throwable t) {
            return null;
        }
    }

}
