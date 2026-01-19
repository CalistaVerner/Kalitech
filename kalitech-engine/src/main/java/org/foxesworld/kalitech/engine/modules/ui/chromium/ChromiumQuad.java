package org.foxesworld.kalitech.engine.modules.ui.chromium;

import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture2D;

import java.util.Objects;

/**
 * Draws a fullscreen quad with Chromium texture.
 */
public final class ChromiumQuad {

    private final Geometry geom;

    public ChromiumQuad(Node guiNode, Material unshadedMat, Texture2D texture, float w, float h) {
        Objects.requireNonNull(guiNode, "guiNode");
        Objects.requireNonNull(unshadedMat, "unshadedMat");
        Objects.requireNonNull(texture, "texture");

        Quad q = new Quad(w, h);
        geom = new Geometry("ChromiumQuad", q);

        Material mat = unshadedMat.clone();
        mat.setTexture("ColorMap", texture);

        geom.setMaterial(mat);
        geom.setLocalTranslation(new Vector3f(0, 0, 0));

        guiNode.attachChild(geom);
    }

    public Geometry geometry() {
        return geom;
    }
}