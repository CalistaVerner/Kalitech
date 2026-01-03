package org.foxesworld.kalitech.engine.api.impl.hud;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.impl.hud.font.HudSdfFontAtlas;
import org.foxesworld.kalitech.engine.api.impl.hud.font.HudSdfFontManager;
import org.foxesworld.kalitech.engine.api.impl.hud.font.HudSdfGlyph;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Modern HUD text element: TTF -> SDF atlas -> GPU shader.
 *
 * Requires:
 *  - MatDefs/HudSdfText.j3md
 *  - Shaders/HudSdfText.vert
 *  - Shaders/HudSdfText.frag
 *
 * This replaces legacy BitmapText completely.
 */
final class HudText extends HudElement {

    // Bind font manager once from HudApiImpl ctor:
    // HudText.bindFontManager(fontManager);
    private static volatile HudSdfFontManager FONT_MGR;

    static void bindFontManager(HudSdfFontManager mgr) {
        FONT_MGR = mgr;
    }

    private String textValue = "";
    private String fontTtf = System.getProperty("hud.ttf.default", "Fonts/FSElliotPro-Heavy.ttf");

    // author dp font size
    float rawFontPx = 20f;

    // resolved px font size (computed each layout)
    float fontPx = 20f;

    ColorRGBA color = new ColorRGBA(1, 1, 1, 1);

    // effects
    float softness = 0.02f;
    float outlineSize = 0.0f;
    ColorRGBA outlineColor = new ColorRGBA(0, 0, 0, 0);
    float shadowAlpha = 0.0f;
    float shadowX = 0f;
    float shadowY = 0f;

    // runtime
    private Geometry geom;
    private Mesh mesh;

    private HudSdfFontAtlas atlas;
    private Material mat;

    HudText(int id) {
        super(id, "hud:text:" + id);
        this.kind = Kind.TEXT;
        this.contentSized = true;
    }

    void setText(String s, EngineApiImpl engine) {
        this.textValue = (s == null) ? "" : s;
        rebuild(engine);
    }

    void setFontTtf(String path, EngineApiImpl engine) {
        if (path == null || path.isBlank()) return;
        this.fontTtf = path;
        rebuild(engine);
    }

    void setFontPx(float px, EngineApiImpl engine) {
        this.rawFontPx = Math.max(6f, px);
        rebuild(engine);
    }

    void setColor(ColorRGBA c, EngineApiImpl engine) {
        if (c == null) return;
        this.color = c.clone();
        if (mat != null) mat.setColor("Color", this.color);
    }

    void setSoftness(float v) {
        this.softness = Math.max(0.0001f, v);
        if (mat != null) mat.setFloat("Softness", softness);
    }

    void setOutline(float size, ColorRGBA c) {
        this.outlineSize = Math.max(0f, size);
        if (c != null) this.outlineColor = c.clone();
        if (mat != null) {
            mat.setFloat("OutlineSize", outlineSize);
            mat.setColor("OutlineColor", outlineColor);
        }
    }

    void setShadow(float x, float y, float alpha) {
        this.shadowX = x;
        this.shadowY = y;
        this.shadowAlpha = Math.max(0f, alpha);
        if (mat != null) {
            mat.setVector2("ShadowOffset", new com.jme3.math.Vector2f(shadowX, shadowY));
            mat.setFloat("ShadowAlpha", shadowAlpha);
        }
    }

    /**
     * Если у тебя есть dp->px pass в HudApiImpl, пусть он перед relayout
     * выставляет fontPx, иначе здесь оно будет равно rawFontPx.
     */
    void setResolvedFontPx(float resolvedPx, EngineApiImpl engine) {
        this.fontPx = Math.max(6f, resolvedPx);
        rebuild(engine);
    }

    private void rebuild(EngineApiImpl engine) {
        if (engine == null) return;
        if (fontPx <= 0f) fontPx = rawFontPx;

        ensureAtlas(engine);
        ensureGeom();

        buildMesh();
        applyMaterialParams();

        measure();
    }

    private void ensureAtlas(EngineApiImpl engine) {
        HudSdfFontManager mgr = FONT_MGR;
        if (mgr == null) {
            throw new IllegalStateException("HudSdfFontManager not bound. Call HudText.bindFontManager(fontManager) from HudApiImpl.");
        }

        this.atlas = mgr.get(fontTtf);

        if (this.mat == null || this.mat.getTextureParam("SdfMap") == null) {
            // atlas.mat should exist; clone per element
            this.mat = atlas.mat.clone();
            if (geom != null) geom.setMaterial(this.mat);
        } else {
            this.mat.setTexture("SdfMap", atlas.atlasTex);
        }
    }

    private void ensureGeom() {
        if (geom != null) return;

        this.mesh = new Mesh();
        this.geom = new Geometry("hudText#" + id, mesh);

        // 🔒 GUI-PROFILE
        this.geom.setQueueBucket(RenderQueue.Bucket.Gui);
        this.geom.setCullHint(com.jme3.scene.Spatial.CullHint.Never);

        node.attachChild(geom);
    }

    private void applyMaterialParams() {
        if (mat == null) return;

        mat.setColor("Color", color);
        mat.setFloat("Softness", softness);
        mat.setFloat("OutlineSize", outlineSize);
        mat.setColor("OutlineColor", outlineColor);
        mat.setVector2("ShadowOffset", new com.jme3.math.Vector2f(shadowX, shadowY));
        mat.setFloat("ShadowAlpha", shadowAlpha);

        // ensure HUD render state
        RenderState rs = mat.getAdditionalRenderState();
        rs.setBlendMode(RenderState.BlendMode.Alpha);
        rs.setDepthTest(false);
        rs.setDepthWrite(false);
        rs.setFaceCullMode(RenderState.FaceCullMode.Off);

        geom.setMaterial(mat);
    }

    private void buildMesh() {
        if (atlas == null) return;

        String s = (textValue == null) ? "" : textValue;
        if (s.isEmpty()) {
            mesh.clearBuffer(VertexBuffer.Type.Position);
            mesh.clearBuffer(VertexBuffer.Type.TexCoord);
            mesh.clearBuffer(VertexBuffer.Type.Index);
            mesh.updateBound();
            return;
        }

        float scale = fontPx / (float) atlas.bakePx;

        int maxGlyphs = s.codePointCount(0, s.length());
        int vCount = maxGlyphs * 4;
        int iCount = maxGlyphs * 6;

        FloatBuffer pos = BufferUtils.createFloatBuffer(vCount * 3);
        FloatBuffer uv  = BufferUtils.createFloatBuffer(vCount * 2);
        ShortBuffer idx = BufferUtils.createShortBuffer(iCount);

        float penX = 0f;
        float penY = 0f;

        int vi = 0;

        for (int off = 0; off < s.length(); ) {
            int cp = s.codePointAt(off);
            off += Character.charCount(cp);

            if (cp == '\n') {
                penX = 0;
                penY -= atlas.lineHeight * scale;
                continue;
            }

            HudSdfGlyph g = atlas.glyph(cp);
            if (g == null) g = atlas.glyph((int) '?');
            if (g == null) continue;

            float x0 = penX + (g.xOff * scale);
            float y0 = penY + (g.yOff * scale);
            float x1 = x0 + (g.w * scale);
            float y1 = y0 + (g.h * scale);

            // v0
            pos.put(x0).put(y0).put(0);
            uv.put(g.u0).put(g.v0);
            // v1
            pos.put(x1).put(y0).put(0);
            uv.put(g.u1).put(g.v0);
            // v2
            pos.put(x1).put(y1).put(0);
            uv.put(g.u1).put(g.v1);
            // v3
            pos.put(x0).put(y1).put(0);
            uv.put(g.u0).put(g.v1);

            short base = (short) vi;
            idx.put(base).put((short) (base + 1)).put((short) (base + 2));
            idx.put(base).put((short) (base + 2)).put((short) (base + 3));
            vi += 4;

            penX += g.xAdvance * scale;
        }

        pos.flip();
        uv.flip();
        idx.flip();

        mesh.setBuffer(VertexBuffer.Type.Position, 3, pos);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uv);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, idx);
        mesh.updateBound();
    }

    private void measure() {
        if (atlas == null) { w = 0; h = 0; return; }

        String s = (textValue == null) ? "" : textValue;
        if (s.isEmpty()) { w = 0; h = 0; return; }

        float scale = fontPx / (float) atlas.bakePx;

        float penX = 0f;
        float maxX = 0f;

        int lines = 1;

        for (int off = 0; off < s.length(); ) {
            int cp = s.codePointAt(off);
            off += Character.charCount(cp);

            if (cp == '\n') {
                maxX = Math.max(maxX, penX);
                penX = 0;
                lines++;
                continue;
            }

            HudSdfGlyph g = atlas.glyph(cp);
            if (g == null) g = atlas.glyph((int) '?');
            if (g == null) continue;

            penX += g.xAdvance * scale;
        }
        maxX = Math.max(maxX, penX);

        float height = lines * (atlas.lineHeight * scale);

        this.w = Math.max(0f, maxX);
        this.h = Math.max(0f, height);
        this.contentSized = true;
    }

    @Override
    void onDestroy() {
        if (node != null) node.detachAllChildren();
        geom = null;
        mesh = null;
        mat = null;
        atlas = null;
    }
}