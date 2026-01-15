// FILE: org/foxesworld/kalitech/engine/modules/render/sky/SkyDomeRenderer.java
// Author: Calista Verner (KΛYLΛ)
package org.foxesworld.kalitech.engine.modules.render.sky;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import org.apache.logging.log4j.Logger;

/**
 * Owns the sky dome model/material lifecycle and texture bindings.
 * <p>
 * Responsibilities:
 * - lazy creation & attachment
 * - safe texture switching (2D vs cubemap)
 * - stable state transitions (enable/disable/clear)
 */
public final class SkyDomeRenderer {

    private static final String SKYDOME_MODEL_ASSET = "Models/Sky/skydome.obj";
    private static final String SKYDOME_MAT_DEF = "MatDefs/Sky/SkyDome.j3md";
    private static final float SKYDOME_SCALE = 1000f;

    private final SimpleApplication app;
    private final AssetManager assets;
    private final Logger log;

    private final SkyDomeUniformCache uniforms = new SkyDomeUniformCache();

    private Spatial skydome;
    private Material skydomeMat;
    private Geometry[] skydomeGeoms;

    private boolean enabled = true;
    private Boolean useCubeMode = null;

    public SkyDomeRenderer(SimpleApplication app, AssetManager assets, Logger log) {
        if (app == null) throw new IllegalArgumentException("app is null");
        if (assets == null) throw new IllegalArgumentException("assets is null");
        if (log == null) throw new IllegalArgumentException("log is null");
        this.app = app;
        this.assets = assets;
        this.log = log;
    }

    private static boolean hasAnyTextureBound(Material m) {
        return m.getParam("SkyTexA") != null
                || m.getParam("SkyTexB") != null
                || m.getParam("SkyCubeA") != null
                || m.getParam("SkyCubeB") != null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (!enabled) {
            if (skydome != null) {
                skydome.removeFromParent();
            }
            return;
        }

        ensureExists();
        if (skydome.getParent() == null) {
            app.getRootNode().attachChild(skydome);
        }
    }

    public void clearAll() {
        if (skydome != null) {
            skydome.removeFromParent();
        }
        skydome = null;
        skydomeMat = null;
        skydomeGeoms = null;
        useCubeMode = null;
        uniforms.reset();
        log.info("RenderApi: skydome cleared");
    }

    public void clearTextures() {
        ensureExists();
        SkyMaterialUtil.safeClearParam(skydomeMat, "SkyTex");
        SkyMaterialUtil.safeClearParam(skydomeMat, "SkyCube");
        SkyMaterialUtil.safeClearParam(skydomeMat, "SkyTexA");
        SkyMaterialUtil.safeClearParam(skydomeMat, "SkyTexB");
        SkyMaterialUtil.safeClearParam(skydomeMat, "SkyCubeA");
        SkyMaterialUtil.safeClearParam(skydomeMat, "SkyCubeB");
        useCubeMode = null;
        uniforms.onTexturesChanged();
        log.info("RenderApi: skydome texture cleared");
    }

    public void setTextureA(String asset) {
        setTexture(asset, SkyTextureSlot.A);
    }

    public void setTextureB(String asset) {
        setTexture(asset, SkyTextureSlot.B);
    }

    public void applyConfig(SkyDomeConfig cfg) {
        ensureExists();
        uniforms.apply(skydomeMat, cfg, hasAnyTextureBound(skydomeMat));
        propagateMaterialToGeometries();
    }

    private void ensureExists() {
        if (skydome != null && skydomeMat != null) {
            return;
        }

        AssetInfo info = assets.locateAsset(new AssetKey<>(SKYDOME_MODEL_ASSET));
        if (info == null) {
            throw new IllegalStateException("RenderApi: engine skydome resource not found: " + SKYDOME_MODEL_ASSET);
        }

        Spatial dome = assets.loadModel(SKYDOME_MODEL_ASSET);
        if (dome == null) {
            throw new IllegalStateException("RenderApi: loadModel returned null for: " + SKYDOME_MODEL_ASSET);
        }

        Material m = new Material(assets, SKYDOME_MAT_DEF);

        Geometry[] geoms = SkySceneUtil.collectGeometriesAndBind(dome, m);
        dome.setName("SkyDome");
        dome.setQueueBucket(RenderQueue.Bucket.Sky);
        dome.setCullHint(Spatial.CullHint.Never);
        dome.setShadowMode(RenderQueue.ShadowMode.Off);
        dome.setLocalScale(SKYDOME_SCALE);

        skydome = dome;
        skydomeMat = m;
        skydomeGeoms = geoms;

        if (enabled) {
            app.getRootNode().attachChild(dome);
        }

        log.info("RenderApi: skydome created model='{}' geoms={}", SKYDOME_MODEL_ASSET, (geoms == null ? 0 : geoms.length));
    }

    private void setTexture(String asset, SkyTextureSlot slot) {
        if (asset == null || asset.isBlank()) {
            throw new IllegalArgumentException("[render] skyDomeTex" + slot.name() + ": asset is blank");
        }
        ensureExists();

        String raw = asset.trim();
        String resolved = SkyTextureUtil.resolveSkyAsset(raw);

        Texture t = assets.loadTexture(resolved);
        if (t == null) {
            throw new IllegalStateException("[render] skyDomeTex" + slot.name()
                    + ": loadTexture returned null: raw='" + raw + "' resolved='" + resolved + "'");
        }

        boolean isCube = (t instanceof com.jme3.texture.TextureCubeMap);
        SkyTextureUtil.configureSkyTexture(t, isCube);

        if (useCubeMode == null) {
            useCubeMode = isCube;
        } else if (useCubeMode != isCube) {
            throw new IllegalStateException("[render] SkyDome A/B type mismatch: requested " +
                    (isCube ? "CUBE" : "2D") + " but existing mode is " + (useCubeMode ? "CUBE" : "2D"));
        }

        if (isCube) {
            skydomeMat.setBoolean("UseCube", true);
            if (slot == SkyTextureSlot.A) {
                skydomeMat.setTexture("SkyCubeA", t);
                SkyMaterialUtil.safeClearParam(skydomeMat, "SkyTexA");
            } else {
                skydomeMat.setTexture("SkyCubeB", t);
                SkyMaterialUtil.safeClearParam(skydomeMat, "SkyTexB");
            }
        } else {
            skydomeMat.setBoolean("UseCube", false);
            if (slot == SkyTextureSlot.A) {
                skydomeMat.setTexture("SkyTexA", t);
                SkyMaterialUtil.safeClearParam(skydomeMat, "SkyCubeA");
            } else {
                skydomeMat.setTexture("SkyTexB", t);
                SkyMaterialUtil.safeClearParam(skydomeMat, "SkyCubeB");
            }
        }

        uniforms.onTexturesChanged();
        propagateMaterialToGeometries();
    }

    private void propagateMaterialToGeometries() {
        if (skydomeGeoms == null || skydomeMat == null) return;
        for (Geometry g : skydomeGeoms) {
            if (g.getMaterial() != skydomeMat) {
                g.setMaterial(skydomeMat);
            }
        }
    }
}
