package org.foxesworld.kalitech.engine.render.post;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.post.Filter;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;

public final class TonemapFilter extends Filter {

    private final AssetManager assets;
    // RGB multiplier, A as strength
    private final ColorRGBA tint = new ColorRGBA(1f, 1f, 1f, 0f);
    private float exposure = 1.8f;
    private float gamma = 2.2f;
    private float whitePoint = 11.2f;
    private float shoulder = 0.22f;
    private float toe = 0.08f;
    private float saturation = 1.0f;
    private int width = -1;
    private int height = -1;

    public TonemapFilter(AssetManager assets) {
        super("KaliTonemap");
        this.assets = assets;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public void setExposure(float exposure) {
        this.exposure = Math.max(0.01f, exposure);
        if (material != null) material.setFloat("Exposure", this.exposure);
    }

    public void setGamma(float gamma) {
        this.gamma = Math.max(0.01f, gamma);
        if (material != null) material.setFloat("Gamma", this.gamma);
    }

    public void setWhitePoint(float whitePoint) {
        this.whitePoint = Math.max(0.01f, whitePoint);
        if (material != null) material.setFloat("WhitePoint", this.whitePoint);
    }

    public void setShoulder(float shoulder) {
        this.shoulder = clamp01(shoulder);
        if (material != null) material.setFloat("Shoulder", this.shoulder);
    }

    public void setToe(float toe) {
        this.toe = clamp01(toe);
        if (material != null) material.setFloat("Toe", this.toe);
    }

    public void setSaturation(float saturation) {
        this.saturation = Math.max(0.0f, saturation);
        if (material != null) material.setFloat("Saturation", this.saturation);
    }

    public void setTint(float r, float g, float b, float a) {
        tint.set(clamp01(r), clamp01(g), clamp01(b), clamp01(a));
        if (material != null) material.setColor("Tint", tint);
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        this.width = w;
        this.height = h;

        if (material == null) {
            material = new Material(assets, "MatDefs/Post/KaliTonemap.j3md");
        }

        pushAllParams();
    }

    @Override
    protected Material getMaterial() {
        if (material == null) {
            material = new Material(assets, "MatDefs/Post/KaliTonemap.j3md");
            pushAllParams();
        }
        return material;
    }

    private void pushAllParams() {
        material.setFloat("Exposure", exposure);
        material.setFloat("Gamma", gamma);

        material.setFloat("WhitePoint", whitePoint);
        material.setFloat("Shoulder", shoulder);
        material.setFloat("Toe", toe);
        material.setFloat("Saturation", saturation);

        material.setColor("Tint", tint);
    }

    public int getViewportWidth() {
        return width;
    }

    public int getViewportHeight() {
        return height;
    }
}