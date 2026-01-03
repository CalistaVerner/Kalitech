// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudRect.java
package org.foxesworld.kalitech.engine.api.impl.hud;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;

final class HudRect extends HudElement {

    private Geometry geom;
    private Quad quad;
    private Material mat;

    private ColorRGBA color = new ColorRGBA(0, 0, 0, 0.4f);

    HudRect(int id) {
        super(id, "hud:rect:" + id);
        this.kind = Kind.RECT;        // ✅ CRITICAL
        this.contentSized = false;    // rect is size-driven
        this.w = 0f;
        this.h = 0f;
    }

    void setSize(float w, float h, EngineApiImpl engine) {
        this.w = Math.max(0f, w);
        this.h = Math.max(0f, h);

        ensureGeom(engine);
        applySize();
    }

    void setColor(ColorRGBA c, EngineApiImpl engine) {
        if (c == null) return;
        this.color = c.clone();

        ensureGeom(engine);
        applyColor();
    }

    private void ensureGeom(EngineApiImpl engine) {
        if (geom != null) return;

        quad = new Quad(1f, 1f, true);
        geom = new Geometry("hudRect#" + id, quad);
        geom.setQueueBucket(RenderQueue.Bucket.Gui);
        geom.setCullHint(Spatial.CullHint.Never);

        // simple unshaded + alpha
        mat = new Material(engine.getAssets(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);

        RenderState rs = mat.getAdditionalRenderState();
        rs.setBlendMode(RenderState.BlendMode.Alpha);
        rs.setDepthTest(false);
        rs.setDepthWrite(false);
        rs.setFaceCullMode(RenderState.FaceCullMode.Off);

        geom.setMaterial(mat);
        node.attachChild(geom);

        applySize();
        applyColor();
    }

    private void applySize() {
        if (quad == null) return;

        float ww = Math.max(0f, w);
        float hh = Math.max(0f, h);

        // Quad is (1x1) by default, scale it
        geom.setLocalScale(ww, hh, 1f);

        try { geom.updateModelBound(); } catch (Throwable ignored) {}
    }

    private void applyColor() {
        if (mat == null) return;
        mat.setColor("Color", color);
    }

    @Override
    void onDestroy() {
        try { node.detachAllChildren(); } catch (Throwable ignored) {}
        geom = null;
        quad = null;
        mat = null;
    }
}