/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.SimpleApplication
 *  com.jme3.asset.AssetInfo
 *  com.jme3.asset.AssetKey
 *  com.jme3.asset.AssetManager
 *  com.jme3.material.Material
 *  com.jme3.renderer.queue.RenderQueue$Bucket
 *  com.jme3.renderer.queue.RenderQueue$ShadowMode
 *  com.jme3.scene.Geometry
 *  com.jme3.scene.Spatial
 *  com.jme3.scene.Spatial$CullHint
 *  com.jme3.texture.Texture
 *  com.jme3.texture.TextureCubeMap
 *  org.apache.logging.log4j.Logger
 */
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
import com.jme3.texture.TextureCubeMap;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.sky.SkyDomeConfig;
import org.foxesworld.kalitech.engine.modules.render.sky.SkyDomeUniformCache;
import org.foxesworld.kalitech.engine.modules.render.sky.SkyMaterialUtil;
import org.foxesworld.kalitech.engine.modules.render.sky.SkySceneUtil;
import org.foxesworld.kalitech.engine.modules.render.sky.SkyTextureSlot;
import org.foxesworld.kalitech.engine.modules.render.sky.SkyTextureUtil;

public final class SkyDomeRenderer {
    private static final String SKYDOME_MODEL_ASSET = "Models/Sky/skydome.obj";
    private static final String SKYDOME_MAT_DEF = "MatDefs/Sky/SkyDome.j3md";
    private static final float SKYDOME_SCALE = 1000.0f;
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
        if (app == null) {
            throw new IllegalArgumentException("app is null");
        }
        if (assets == null) {
            throw new IllegalArgumentException("assets is null");
        }
        if (log == null) {
            throw new IllegalArgumentException("log is null");
        }
        this.app = app;
        this.assets = assets;
        this.log = log;
    }

    private static boolean hasAnyTextureBound(Material m) {
        return m.getParam("SkyTexA") != null || m.getParam("SkyTexB") != null || m.getParam("SkyCubeA") != null || m.getParam("SkyCubeB") != null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            if (this.skydome != null) {
                this.skydome.removeFromParent();
            }
            return;
        }
        this.ensureExists();
        if (this.skydome.getParent() == null) {
            this.app.getRootNode().attachChild(this.skydome);
        }
    }

    public void clearAll() {
        if (this.skydome != null) {
            this.skydome.removeFromParent();
        }
        this.skydome = null;
        this.skydomeMat = null;
        this.skydomeGeoms = null;
        this.useCubeMode = null;
        this.uniforms.reset();
        this.log.info("RenderApi: skydome cleared");
    }

    public void clearTextures() {
        this.ensureExists();
        SkyMaterialUtil.safeClearParam(this.skydomeMat, "SkyTex");
        SkyMaterialUtil.safeClearParam(this.skydomeMat, "SkyCube");
        SkyMaterialUtil.safeClearParam(this.skydomeMat, "SkyTexA");
        SkyMaterialUtil.safeClearParam(this.skydomeMat, "SkyTexB");
        SkyMaterialUtil.safeClearParam(this.skydomeMat, "SkyCubeA");
        SkyMaterialUtil.safeClearParam(this.skydomeMat, "SkyCubeB");
        this.useCubeMode = null;
        this.uniforms.onTexturesChanged();
        this.log.info("RenderApi: skydome texture cleared");
    }

    public void setTextureA(String asset) {
        this.setTexture(asset, SkyTextureSlot.A);
    }

    public void setTextureB(String asset) {
        this.setTexture(asset, SkyTextureSlot.B);
    }

    public void applyConfig(SkyDomeConfig cfg) {
        this.ensureExists();
        this.uniforms.apply(this.skydomeMat, cfg, SkyDomeRenderer.hasAnyTextureBound(this.skydomeMat));
        this.propagateMaterialToGeometries();
    }

    private void ensureExists() {
        if (this.skydome != null && this.skydomeMat != null) {
            return;
        }
        AssetInfo info = this.assets.locateAsset(new AssetKey(SKYDOME_MODEL_ASSET));
        if (info == null) {
            throw new IllegalStateException("RenderApi: engine skydome resource not found: Models/Sky/skydome.obj");
        }
        Spatial dome = this.assets.loadModel(SKYDOME_MODEL_ASSET);
        if (dome == null) {
            throw new IllegalStateException("RenderApi: loadModel returned null for: Models/Sky/skydome.obj");
        }
        Material m = new Material(this.assets, SKYDOME_MAT_DEF);
        Geometry[] geoms = SkySceneUtil.collectGeometriesAndBind(dome, m);
        dome.setName("SkyDome");
        dome.setQueueBucket(RenderQueue.Bucket.Sky);
        dome.setCullHint(Spatial.CullHint.Never);
        dome.setShadowMode(RenderQueue.ShadowMode.Off);
        dome.setLocalScale(1000.0f);
        this.skydome = dome;
        this.skydomeMat = m;
        this.skydomeGeoms = geoms;
        if (this.enabled) {
            this.app.getRootNode().attachChild(dome);
        }
        this.log.info("RenderApi: skydome created model='{}' geoms={}", (Object)SKYDOME_MODEL_ASSET, (Object)(geoms == null ? 0 : geoms.length));
    }

    private void setTexture(String asset, SkyTextureSlot slot) {
        if (asset == null || asset.isBlank()) {
            throw new IllegalArgumentException("[render] skyDomeTex" + slot.name() + ": asset is blank");
        }
        this.ensureExists();
        String raw = asset.trim();
        String resolved = SkyTextureUtil.resolveSkyAsset(raw);
        Texture t = this.assets.loadTexture(resolved);
        if (t == null) {
            throw new IllegalStateException("[render] skyDomeTex" + slot.name() + ": loadTexture returned null: raw='" + raw + "' resolved='" + resolved + "'");
        }
        boolean isCube = t instanceof TextureCubeMap;
        SkyTextureUtil.configureSkyTexture(t, isCube);
        if (this.useCubeMode == null) {
            this.useCubeMode = isCube;
        } else if (this.useCubeMode != isCube) {
            throw new IllegalStateException("[render] SkyDome A/B type mismatch: requested " + (isCube ? "CUBE" : "2D") + " but existing mode is " + (this.useCubeMode != false ? "CUBE" : "2D"));
        }
        if (isCube) {
            this.skydomeMat.setBoolean("UseCube", true);
            if (slot == SkyTextureSlot.A) {
                this.skydomeMat.setTexture("SkyCubeA", t);
                SkyMaterialUtil.safeClearParam(this.skydomeMat, "SkyTexA");
            } else {
                this.skydomeMat.setTexture("SkyCubeB", t);
                SkyMaterialUtil.safeClearParam(this.skydomeMat, "SkyTexB");
            }
        } else {
            this.skydomeMat.setBoolean("UseCube", false);
            if (slot == SkyTextureSlot.A) {
                this.skydomeMat.setTexture("SkyTexA", t);
                SkyMaterialUtil.safeClearParam(this.skydomeMat, "SkyCubeA");
            } else {
                this.skydomeMat.setTexture("SkyTexB", t);
                SkyMaterialUtil.safeClearParam(this.skydomeMat, "SkyCubeB");
            }
        }
        this.uniforms.onTexturesChanged();
        this.propagateMaterialToGeometries();
    }

    private void propagateMaterialToGeometries() {
        if (this.skydomeGeoms == null || this.skydomeMat == null) {
            return;
        }
        for (Geometry g : this.skydomeGeoms) {
            if (g.getMaterial() == this.skydomeMat) continue;
            g.setMaterial(this.skydomeMat);
        }
    }
}

