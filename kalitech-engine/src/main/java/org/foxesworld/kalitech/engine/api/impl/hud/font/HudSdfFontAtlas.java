// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudSdfFontAtlas.java
package org.foxesworld.kalitech.engine.api.impl.hud.font;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;

public final class HudSdfFontAtlas {

    // ... Key без изменений ...

    final String ttfPath;
    public final int bakePx;

    /** SDF spread/range in pixels used during atlas bake. MUST be passed to shader as PxRange. */
    public final int spreadPx;

    /** Convenience float range for shader usage (same as spreadPx). */
    public final float pxRange;

    public final Texture2D atlasTex;

    /** Atlas dimensions (needed for TexelSize) */
    public final int atlasW;
    public final int atlasH;

    final int ascent;
    final int descent;
    public final int lineHeight;

    private final Map<Integer, HudSdfGlyph> glyphs = new HashMap<>();

    /** Material template (clone per element). Must contain TexelSize & PxRange. */
    public final Material mat;

    private HudSdfFontAtlas(String ttfPath,
                            int bakePx,
                            int spreadPx,
                            Texture2D atlasTex,
                            int atlasW,
                            int atlasH,
                            int ascent,
                            int descent,
                            int lineHeight,
                            Map<Integer, HudSdfGlyph> glyphs,
                            Material mat) {
        this.ttfPath = ttfPath;
        this.bakePx = bakePx;
        this.spreadPx = spreadPx;
        this.pxRange = (float) spreadPx;
        this.atlasTex = atlasTex;
        this.atlasW = atlasW;
        this.atlasH = atlasH;
        this.ascent = ascent;
        this.descent = descent;
        this.lineHeight = lineHeight;
        this.glyphs.putAll(glyphs);
        this.mat = mat;
    }

    public HudSdfGlyph glyph(int codepoint) { return glyphs.get(codepoint); }
    boolean has(int codepoint) { return glyphs.containsKey(codepoint); }

    static HudSdfFontAtlas build(AssetManager assets,
                                 String ttfPath,
                                 int bakePx,
                                 int spreadPx,
                                 IntSet charset) {

        try (InputStream in = openAssetStream(assets, ttfPath)) {
            if (in == null) throw new IllegalArgumentException("TTF not found: " + ttfPath);

            Font base = Font.createFont(Font.TRUETYPE_FONT, in);
            Font font = base.deriveFont(Font.PLAIN, (float) bakePx);

            FontRenderContext frc = new FontRenderContext(null, true, true);

            BufferedImage tmp = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = tmp.createGraphics();
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int ascent = fm.getAscent();
            int descent = fm.getDescent();
            int lineHeight = fm.getHeight();
            g2.dispose();

            List<GlyphBitmap> rendered = new ArrayList<>(charset.size());
            for (IntCursor c = charset.cursor(); c.hasNext();) {
                int cp = c.next();
                GlyphBitmap gb = renderGlyph(font, frc, cp, spreadPx);
                if (gb != null) rendered.add(gb);
            }

            Packer packer = new Packer(512, 512);
            for (GlyphBitmap gb : rendered) packer.place(gb);
            int atlasW = packer.atlasW;
            int atlasH = packer.atlasH;

            byte[] atlasA = new byte[atlasW * atlasH];
            Map<Integer, HudSdfGlyph> glyphs = new HashMap<>();

            for (GlyphBitmap gb : rendered) {
                for (int y = 0; y < gb.h; y++) {
                    int dstRow = (gb.py + y) * atlasW + gb.px;
                    int srcRow = y * gb.w;
                    System.arraycopy(gb.alpha, srcRow, atlasA, dstRow, gb.w);
                }

                float u0 = gb.px / (float) atlasW;
                float v0 = gb.py / (float) atlasH;
                float u1 = (gb.px + gb.w) / (float) atlasW;
                float v1 = (gb.py + gb.h) / (float) atlasH;

                glyphs.put(gb.codepoint, new HudSdfGlyph(
                        gb.codepoint, gb.w, gb.h, gb.xOff, gb.yOff, gb.xAdvance,
                        u0, v0, u1, v1
                ));
            }

            ByteBuffer buf = BufferUtils.createByteBuffer(atlasA.length);
            buf.put(atlasA).flip();

            Image img = new Image(Image.Format.Alpha8, atlasW, atlasH, buf);
            Texture2D tex = new Texture2D(img);
            tex.setMagFilter(com.jme3.texture.Texture.MagFilter.Bilinear);
            tex.setMinFilter(com.jme3.texture.Texture.MinFilter.BilinearNoMipMaps);
            tex.setWrap(com.jme3.texture.Texture.WrapMode.Clamp);

            Material mat = new Material(assets, "MatDefs/HudSdfText.j3md");
            mat.setTexture("SdfMap", tex);
            mat.setColor("Color", ColorRGBA.White);
            mat.setFloat("Softness", 0.02f);

            // Pixel semantics (AAA)
            mat.setFloat("OutlineSize", 0.0f);
            mat.setColor("OutlineColor", new ColorRGBA(0, 0, 0, 0));
            mat.setVector2("ShadowOffset", new Vector2f(0, 0));
            mat.setFloat("ShadowAlpha", 0.0f);

            // ✅ CRITICAL: atlas metadata for stable rendering
            mat.setVector2("TexelSize", new Vector2f(1f / atlasW, 1f / atlasH));
            mat.setFloat("PxRange", (float) spreadPx);

            return new HudSdfFontAtlas(ttfPath, bakePx, spreadPx, tex, atlasW, atlasH,
                    ascent, descent, lineHeight, glyphs, mat);

        } catch (Exception e) {
            throw new RuntimeException("Failed to build SDF atlas from: " + ttfPath, e);
        }
    }

    public static final class Key {
        public final String ttfPath;
        public final int bakePx;
        public final int spreadPx;
        public final int charsetHash;

        public Key(String ttfPath, int bakePx, int spreadPx, int charsetHash) {
            this.ttfPath = Objects.requireNonNull(ttfPath, "ttfPath");
            this.bakePx = bakePx;
            this.spreadPx = spreadPx;
            this.charsetHash = charsetHash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return bakePx == k.bakePx
                    && spreadPx == k.spreadPx
                    && charsetHash == k.charsetHash
                    && ttfPath.equals(k.ttfPath);
        }

        @Override
        public int hashCode() {
            int h = ttfPath.hashCode();
            h = 31 * h + bakePx;
            h = 31 * h + spreadPx;
            h = 31 * h + charsetHash;
            return h;
        }

        @Override
        public String toString() {
            return "HudSdfFontAtlas.Key{" +
                    "ttfPath='" + ttfPath + '\'' +
                    ", bakePx=" + bakePx +
                    ", spreadPx=" + spreadPx +
                    ", charsetHash=" + charsetHash +
                    '}';
        }
    }


    private static InputStream openAssetStream(AssetManager assets, String path) {
        try {
            var loc = assets.locateAsset(new AssetKey<>(path));
            if (loc == null) return null;
            return loc.openStream();
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------- glyph render -------------------

    private static final class GlyphBitmap {
        final int codepoint;
        final int w, h;
        final int xOff, yOff, xAdvance;
        final byte[] alpha; // SDF alpha8
        int px, py; // packed atlas pos

        GlyphBitmap(int codepoint, int w, int h, int xOff, int yOff, int xAdvance, byte[] alpha) {
            this.codepoint = codepoint;
            this.w = w;
            this.h = h;
            this.xOff = xOff;
            this.yOff = yOff;
            this.xAdvance = xAdvance;
            this.alpha = alpha;
        }
    }

    private static GlyphBitmap renderGlyph(Font font, FontRenderContext frc, int cp, int spreadPx) {
        if (cp == '\n' || cp == '\r' || cp == '\t') return null;

        char[] chars = Character.toChars(cp);
        GlyphVector gv = font.createGlyphVector(frc, new String(chars));

        Rectangle2D vb = gv.getVisualBounds();
        Rectangle2D lb = gv.getLogicalBounds();

        int xAdvance = (int) Math.ceil(lb.getWidth());

        // if glyph has no visible bounds (space), we still create metrics but no bitmap
        if (vb.getWidth() <= 0.001 || vb.getHeight() <= 0.001) {
            // fallback: 1x1 empty sdf cell
            int w = 1;
            int h = 1;
            byte[] a = new byte[w * h];
            Arrays.fill(a, (byte) 0x80);
            return new GlyphBitmap(cp, w, h, 0, 0, Math.max(1, xAdvance), a);
        }

        // add padding = spread * 2
        int pad = Math.max(2, spreadPx + 2);
        int w = (int) Math.ceil(vb.getWidth()) + pad * 2;
        int h = (int) Math.ceil(vb.getHeight()) + pad * 2;

        // offsets relative to pen:
        // Java2D draws glyph at baseline; we want top-left offset.
        int xOff = (int) Math.floor(vb.getX()) - pad;
        int yOff = (int) Math.floor(vb.getY()) - pad;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setFont(font);
        g.setColor(Color.WHITE);

        // translate so that visual bounds fit into image with padding
        g.translate(-vb.getX() + pad, -vb.getY() + pad);
        g.drawGlyphVector(gv, 0, 0);
        g.dispose();

        // extract mask
        boolean[] inside = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                inside[y * w + x] = a >= 128;
            }
        }

        // build signed distance field
        byte[] sdf = buildSdf(inside, w, h, spreadPx);

        return new GlyphBitmap(cp, w, h, xOff, yOff, Math.max(1, xAdvance), sdf);
    }

    // ------------------- SDF generation (exact EDT) -------------------

    private static byte[] buildSdf(boolean[] inside, int w, int h, int spreadPx) {
        final float INF = 1e20f;

        // distance to inside (for outside pixels)
        float[] fIn = new float[w * h];
        // distance to outside (for inside pixels)
        float[] fOut = new float[w * h];

        for (int i = 0; i < w * h; i++) {
            boolean in = inside[i];
            fIn[i] = in ? 0f : INF;
            fOut[i] = in ? INF : 0f;
        }

        float[] dIn = edt2d(fIn, w, h);
        float[] dOut = edt2d(fOut, w, h);

        byte[] out = new byte[w * h];

        float spread = Math.max(1f, (float) spreadPx);
        for (int i = 0; i < w * h; i++) {
            float distIn = (float) Math.sqrt(dIn[i]);
            float distOut = (float) Math.sqrt(dOut[i]);

            // signed distance: negative inside, positive outside
            float signed = distOut - distIn;

            float v = 0.5f + (signed / (2f * spread));
            if (v < 0f) v = 0f;
            else if (v > 1f) v = 1f;

            out[i] = (byte) (int) (v * 255f + 0.5f);
        }

        return out;
    }

    private static float[] edt2d(float[] grid, int w, int h) {
        float[] tmp = new float[w * h];
        float[] out = new float[w * h];

        // transform along Y for each X
        float[] f = new float[Math.max(w, h)];
        float[] d = new float[Math.max(w, h)];

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) f[y] = grid[y * w + x];
            edt1d(f, d, h);
            for (int y = 0; y < h; y++) tmp[y * w + x] = d[y];
        }

        // transform along X for each Y
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) f[x] = tmp[row + x];
            edt1d(f, d, w);
            for (int x = 0; x < w; x++) out[row + x] = d[x];
        }

        return out;
    }

    // Felzenszwalb & Huttenlocher 1D squared distance transform
    private static void edt1d(float[] f, float[] d, int n) {
        int[] v = new int[n];
        float[] z = new float[n + 1];

        int k = 0;
        v[0] = 0;
        z[0] = -Float.MAX_VALUE;
        z[1] = Float.MAX_VALUE;

        for (int q = 1; q < n; q++) {
            float s;
            while (true) {
                int p = v[k];
                s = ((f[q] + q * q) - (f[p] + p * p)) / (2f * (q - p));
                if (s > z[k]) break;
                k--;
                if (k < 0) { k = 0; break; }
            }
            k++;
            v[k] = q;
            z[k] = s;
            z[k + 1] = Float.MAX_VALUE;
        }

        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) k++;
            int p = v[k];
            d[q] = (q - p) * (q - p) + f[p];
        }
    }

    // ------------------- packing -------------------

    private static final class Packer {
        int atlasW, atlasH;
        int x = 0, y = 0, rowH = 0;

        Packer(int startW, int startH) {
            atlasW = startW;
            atlasH = startH;
        }

        void place(GlyphBitmap gb) {
            int gw = gb.w + 2; // 1px padding
            int gh = gb.h + 2;

            // new row
            if (x + gw > atlasW) {
                x = 0;
                y += rowH;
                rowH = 0;
            }

            // grow atlas if needed
            while (y + gh > atlasH) {
                atlasH *= 2;
                if (atlasH > 4096) throw new IllegalStateException("SDF atlas too big");
            }
            while (x + gw > atlasW) {
                atlasW *= 2;
                if (atlasW > 4096) throw new IllegalStateException("SDF atlas too big");
            }

            gb.px = x + 1;
            gb.py = y + 1;

            x += gw;
            rowH = Math.max(rowH, gh);
        }
    }

    // ------------------- tiny int set (no deps) -------------------

    interface IntCursor { boolean hasNext(); int next(); }

    static final class IntSet {
        private final int[] data;
        IntSet(int[] data) { this.data = data; }
        int size() { return data.length; }
        int hashCodeOfSet() { return Arrays.hashCode(data); }
        IntCursor cursor() {
            return new IntCursor() {
                int i = 0;
                public boolean hasNext() { return i < data.length; }
                public int next() { return data[i++]; }
            };
        }
    }
}