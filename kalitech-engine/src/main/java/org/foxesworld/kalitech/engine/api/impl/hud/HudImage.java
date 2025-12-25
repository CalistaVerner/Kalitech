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

    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("hud.debug", "false"));

    private Picture pic;
    private Material mat;

    String imageAsset; // asset path
    ColorRGBA color = new ColorRGBA(1, 1, 1, 1);

    HudImage(int id) {
        super(id, "hud:image:" + id);
    }

    void setSize(float w, float h, EngineApiImpl engine) {
        this.w = Math.max(0f, w);
        this.h = Math.max(0f, h);

        ensurePicture(engine);
        applySize();
        applyColor(); // safe to re-apply

        if (DEBUG) {
            System.out.println("[hud] image#" + id + " setSize -> " + this.w + "x" + this.h);
        }
    }

    void setImage(String assetPath, EngineApiImpl engine) {
        if (assetPath == null || assetPath.isBlank()) return;
        this.imageAsset = assetPath.trim();

        ensurePicture(engine);
        applyImage(engine); // loads texture into Gui material

        if (DEBUG) {
            System.out.println("[hud] image#" + id + " setImage -> " + this.imageAsset);
        }
    }

    void setColor(ColorRGBA c, EngineApiImpl engine) {
        if (c == null) return;
        this.color = c.clone();

        ensurePicture(engine);
        applyColor();

        if (DEBUG) {
            System.out.println("[hud] image#" + id + " setColor -> rgba("
                    + color.r + "," + color.g + "," + color.b + "," + color.a + ")");
        }
    }

    private void ensurePicture(EngineApiImpl engine) {
        if (pic != null) return;

        pic = new Picture("pic#" + id);
        pic.setQueueBucket(RenderQueue.Bucket.Gui);
        pic.setCullHint(Spatial.CullHint.Never);
        node.attachChild(pic);

        // Canonical GUI material
        mat = new Material(engine.getAssets(), "Common/MatDefs/Gui/Gui.j3md");
        RenderState rs = mat.getAdditionalRenderState();
        rs.setFaceCullMode(RenderState.FaceCullMode.Off);
        rs.setDepthTest(false);
        rs.setDepthWrite(false);
        rs.setBlendMode(RenderState.BlendMode.Alpha);

        pic.setMaterial(mat);

        // Apply current state
        applyImage(engine);
        applySize();
        applyColor();
    }

    private void applyImage(EngineApiImpl engine) {
        if (mat == null) return;

        if (imageAsset == null || imageAsset.isBlank()) {
            if (DEBUG) System.out.println("[hud] image#" + id + " no imageAsset set (drawing tinted picture)");
            return;
        }

        try {
            Texture tex = engine.getAssets().loadTexture(imageAsset);

            // UI-friendly (optional but helps)
            tex.setMagFilter(Texture.MagFilter.Bilinear);
            tex.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);

            mat.setTexture("Texture", tex);      // Gui.j3md param name
            // Some jME versions use "Texture" in Gui.j3md (common). If yours is "ColorMap", swap to ColorMap.

            if (DEBUG) {
                System.out.println("[hud] image#" + id + " loaded texture: " + imageAsset
                        + " tex=" + tex.getImage().getWidth() + "x" + tex.getImage().getHeight());
            }
        } catch (Throwable t) {
            System.out.println("[hud] image#" + id + " FAILED to load texture: " + imageAsset + " err=" + t);
        }
    }

    private void applySize() {
        if (pic == null) return;

        float ww = Math.max(0f, w);
        float hh = Math.max(0f, h);

        pic.setWidth(ww);
        pic.setHeight(hh);

        // force refresh (fixes “size не меняется” на некоторых пайплайнах)
        try { pic.updateModelBound(); } catch (Throwable ignored) {}
        try { pic.updateGeometricState(); } catch (Throwable ignored) {}
    }

    private void applyColor() {
        if (mat == null) return;

        // Gui.j3md supports "Color" in many setups; if not present, harmless try/catch.
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