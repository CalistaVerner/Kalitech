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
    private Material mat;

    ColorRGBA color = new ColorRGBA(1, 1, 1, 1);

    HudRect(int id) {
        super(id, "hud:rect:" + id);
    }

    void setSize(float w, float h, EngineApiImpl engine) {
        this.w = Math.max(0f, w);
        this.h = Math.max(0f, h);

        if (geom == null) {
            Quad quad = new Quad(this.w, this.h);
            geom = new Geometry("rect#" + id, quad);

            // 🔒 ЖЁСТКИЙ GUI-ПРОФИЛЬ ДЛЯ GEOMETRY
            geom.setQueueBucket(RenderQueue.Bucket.Gui);
            geom.setCullHint(Spatial.CullHint.Never);

            node.attachChild(geom);
        } else {
            ((Quad) geom.getMesh()).updateGeometry(this.w, this.h);
        }

        ensureMaterial(engine);
        applyMaterial();
    }

    void setColor(ColorRGBA c, EngineApiImpl engine) {
        if (c == null) return;
        this.color = c.clone();

        ensureMaterial(engine);
        applyMaterial();
    }

    private void ensureMaterial(EngineApiImpl engine) {
        if (mat != null) return;

        mat = new Material(engine.getAssets(), "Common/MatDefs/Misc/Unshaded.j3md");

        RenderState rs = mat.getAdditionalRenderState();

        // 🔒 GUI MUST-HAVES
        rs.setFaceCullMode(RenderState.FaceCullMode.Off);
        rs.setDepthTest(false);
        rs.setDepthWrite(false);

        // ❌ Blend выключен по умолчанию (включать ТОЛЬКО если реально нужна альфа)
        rs.setBlendMode(RenderState.BlendMode.Off);
    }

    private void applyMaterial() {
        if (geom == null || mat == null) return;

        mat.setColor("Color", color);
        geom.setMaterial(mat);

        // на всякий случай повторно
        geom.setQueueBucket(RenderQueue.Bucket.Gui);
        geom.setCullHint(Spatial.CullHint.Never);
    }

    @Override
    void onDestroy() {
        if (node != null) {
            node.detachAllChildren();
        }
        geom = null;
        mat = null;
    }
}