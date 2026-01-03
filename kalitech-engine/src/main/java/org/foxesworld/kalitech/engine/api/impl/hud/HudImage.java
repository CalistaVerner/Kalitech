// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudImage.java
package org.foxesworld.kalitech.engine.api.impl.hud;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.jme3.ui.Picture;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;

final class HudImage extends HudElement {

    private Picture pic;
    private Material mat;

    String imageAsset;
    ColorRGBA color = new ColorRGBA(1, 1, 1, 1);

    HudImage(int id) {
        super(id, "hud:image:" + id);
        this.kind = Kind.IMAGE;       // ✅ CRITICAL
        this.contentSized = false;
        this.w = 0f;
        this.h = 0f;
    }

    void setSize(float w, float h, EngineApiImpl engine) {
        this.w = Math.max(0f, w);
        this.h = Math.max(0f, h);

        ensurePicture(engine);
        applySize();
        applyColor();
    }

    void setImage(String assetPath, EngineApiImpl engine) {
        if (assetPath == null || assetPath.isBlank()) return;
        this.imageAsset = assetPath.trim();

        ensurePicture(engine);
        applyImage(engine);
    }

    void setColor(ColorRGBA c, EngineApiImpl engine) {
        if (c == null) return;
        this.color = c.clone();

        ensurePicture(engine);
        applyColor();
    }

    private void ensurePicture(EngineApiImpl engine) {
        if (pic != null) return;

        pic = new Picture("pic#" + id);
        pic.setQueueBucket(RenderQueue.Bucket.Gui);
        pic.setCullHint(Spatial.CullHint.Never);
        node.attachChild(pic);

        mat = new Material(engine.getAssets(), "Common/MatDefs/Gui/Gui.j3md");
        RenderState rs = mat.getAdditionalRenderState();
        rs.setFaceCullMode(RenderState.FaceCullMode.Off);
        rs.setDepthTest(false);
        rs.setDepthWrite(false);
        rs.setBlendMode(RenderState.BlendMode.Alpha);

        pic.setMaterial(mat);

        applyImage(engine);
        applySize();
        applyColor();
    }

    private void applyImage(EngineApiImpl engine) {
        if (mat == null) return;
        if (imageAsset == null || imageAsset.isBlank()) return;

        try {
            Texture tex;
            if (imageAsset.toLowerCase().endsWith(".svg")) {
                int tw = (int) Math.max(1, (this.w > 0f ? this.w : 64f));
                int th = (int) Math.max(1, (this.h > 0f ? this.h : 64f));
                tex = HudSvgRasterizer.loadSvgTexture(engine.getAssets(), imageAsset, tw, th);
                if (tex == null) tex = engine.getAssets().loadTexture(imageAsset);
            } else {
                tex = engine.getAssets().loadTexture(imageAsset);
            }

            tex.setMagFilter(Texture.MagFilter.Bilinear);
            tex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);

            mat.setTexture("Texture", tex);
        } catch (Throwable t) {
            System.out.println("[hud] image#" + id + " FAILED to load texture: " + imageAsset + " err=" + t);
        }
    }

    private void applySize() {
        if (pic == null) return;
        pic.setWidth(Math.max(0f, w));
        pic.setHeight(Math.max(0f, h));
        try { pic.updateModelBound(); } catch (Throwable ignored) {}
    }

    private void applyColor() {
        if (mat == null) return;
        try { mat.setColor("Color", color); } catch (Throwable ignored) {}
    }

    @Override
    void onDestroy() {
        try { node.detachAllChildren(); } catch (Throwable ignored) {}
        pic = null;
        mat = null;
        imageAsset = null;
    }
}