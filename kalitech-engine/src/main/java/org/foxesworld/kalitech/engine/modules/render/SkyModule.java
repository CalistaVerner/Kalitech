/*
 * MIT License
 *
 * Copyright (c) 2026 FoxesWorld
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;

/**
 * SkyDome integration (CDPR-style): minimal state, stable updates, zero per-frame allocations.
 *
 * Rendering model:
 *  - A single shared material for all dome geometries.
 *  - Dome is always centered on camera in the vertex shader (no parallax).
 *  - For 2D skies we rely on mesh-authored seam-safe UVs (not atan() mapping).
 *  - For cubemaps we sample by direction.
 */
public final class SkyModule {

    /**
     * The relative path to the sky dome model.
     */
    private static final String SKYDOME_MODEL_ASSET = "Models/Sky/skydome.obj";
    /** The world scale applied to the sky dome. */
    private static final float SKYDOME_SCALE = 1000f;

    private final SimpleApplication app;
    private final AssetManager assets;
    private final Logger log;

    /**
     * The root spatial for the skydome. When non‑null, this node is attached
     * directly to the application's root node and detached on clear.
     */
    private Spatial skydome;
    /** Material shared by all geometry making up the dome. */
    private Material skydomeMat;
    /** Tracks whether currently loaded sky textures are cubemaps or 2D textures. */
    private Boolean sdUseCube = null;
    /** Cached references to the geometries comprising the dome. */
    private Geometry[] skydomeGeoms;

    // Cached uniform values to avoid redundant material updates.
    private float sdSkyBlend = Float.NaN;
    private float sdTexBlend = Float.NaN;
    private float sdTexExposure = Float.NaN;
    private float sdSunDx = Float.NaN, sdSunDy = Float.NaN, sdSunDz = Float.NaN;
    private float sdMoonDx = Float.NaN, sdMoonDy = Float.NaN, sdMoonDz = Float.NaN;
    private float sdSunR = Float.NaN, sdSunG = Float.NaN, sdSunB = Float.NaN, sdSunI = Float.NaN;
    private float sdMoonR = Float.NaN, sdMoonG = Float.NaN, sdMoonB = Float.NaN, sdMoonI = Float.NaN;
    private float sdZenR = Float.NaN, sdZenG = Float.NaN, sdZenB = Float.NaN;
    private float sdHorR = Float.NaN, sdHorG = Float.NaN, sdHorB = Float.NaN;
    private float sdHaze = Float.NaN, sdSunDisk = Float.NaN, sdMoonDisk = Float.NaN, sdExposure = Float.NaN;

    public SkyModule(SimpleApplication app, AssetManager assets, Logger log) {
        if (app == null) throw new IllegalArgumentException("app is null");
        if (assets == null) throw new IllegalArgumentException("assets is null");
        if (log == null) throw new IllegalArgumentException("log is null");
        this.app = app;
        this.assets = assets;
        this.log = log;
    }

    /**
     * Removes the sky dome from the scene and resets internal state. After
     * calling this method, a subsequent call to {@link #skyDomeCfg(Value)} or
     * {@link #skyDomeTexA(String)} will lazily recreate the dome.
     */
    public void skyDomeClear() {
        if (skydome != null) {
            skydome.removeFromParent();
            skydome = null;
        }
        skydomeMat = null;
        sdUseCube = null;
        skydomeGeoms = null;

        sdSkyBlend = Float.NaN;
        sdTexBlend = Float.NaN;
        sdTexExposure = Float.NaN;
        sdSunDx = sdSunDy = sdSunDz = Float.NaN;
        sdMoonDx = sdMoonDy = sdMoonDz = Float.NaN;
        sdSunR = sdSunG = sdSunB = sdSunI = Float.NaN;
        sdMoonR = sdMoonG = sdMoonB = sdMoonI = Float.NaN;
        sdZenR = sdZenG = sdZenB = Float.NaN;
        sdHorR = sdHorG = sdHorB = Float.NaN;
        sdHaze = sdSunDisk = sdMoonDisk = sdExposure = Float.NaN;

        log.info("RenderApi: skydome cleared");
    }

    /**
     * Updates shader parameters on the sky dome material based on the provided
     * configuration object. Values are only pushed to the GPU when they change
     * to avoid redundant uniform updates each frame. If no sky has yet been
     * created, calling this method will implicitly load the dome model and
     * material.
     *
     * @param cfg configuration containing direction, colour and intensity
     *            information for the sun, moon, zenith and horizon, along with
     *            haze and exposure controls.
     */
    public void skyDomeCfg(Value cfg) {
        ensureSkyDomeExists();

        // Extract parameters with sensible defaults. These helpers clamp or
        // approximate values to prevent invalid inputs. See RenderCfg for details.
        Value sunDir = RenderCfg.member(cfg, "sunDir");
        Value moonDir = RenderCfg.member(cfg, "moonDir");
        Value sunCol = RenderCfg.member(cfg, "sunColor");
        Value moonCol = RenderCfg.member(cfg, "moonColor");
        Value zen = RenderCfg.member(cfg, "zenithColor");
        Value hor = RenderCfg.member(cfg, "horizonColor");

        float sdx = RenderCfg.vec3x(sunDir, -1f);
        float sdy = RenderCfg.vec3y(sunDir, -1f);
        float sdz = RenderCfg.vec3z(sunDir, -0.3f);
        float mdx = RenderCfg.vec3x(moonDir, 1f);
        float mdy = RenderCfg.vec3y(moonDir, -1f);
        float mdz = RenderCfg.vec3z(moonDir, 0.3f);

        float sr = RenderCfg.vec3x(sunCol, 1f);
        float sg = RenderCfg.vec3y(sunCol, 0.98f);
        float sb = RenderCfg.vec3z(sunCol, 0.90f);
        float mr = RenderCfg.vec3x(moonCol, 0.45f);
        float mg = RenderCfg.vec3y(moonCol, 0.55f);
        float mb = RenderCfg.vec3z(moonCol, 0.85f);

        float sunInt = (float) Math.max(0.0, RenderCfg.num(cfg, "sunIntensity", 1.0));
        float moonInt = (float) Math.max(0.0, RenderCfg.num(cfg, "moonIntensity", 0.0));

        float zr = RenderCfg.vec3x(zen, 0.10f);
        float zg = RenderCfg.vec3y(zen, 0.17f);
        float zb = RenderCfg.vec3z(zen, 0.32f);
        float hr = RenderCfg.vec3x(hor, 0.65f);
        float hg = RenderCfg.vec3y(hor, 0.72f);
        float hb = RenderCfg.vec3z(hor, 0.82f);

        float haze = RenderCfg.clamp01((float) RenderCfg.num(cfg, "haze", 0.55));
        float sunDisk = RenderCfg.clamp((float) RenderCfg.num(cfg, "sunDisk", 45.0), 0.5f, 500f);
        float moonDisk = RenderCfg.clamp((float) RenderCfg.num(cfg, "moonDisk", 120.0), 0.5f, 2000f);
        float exposure = RenderCfg.clamp((float) RenderCfg.num(cfg, "exposure", 1.0), 0.05f, 10f);
        float skyBlend = RenderCfg.clamp01((float) RenderCfg.num(cfg, "skyBlend", 0.0));
        float texBlend = RenderCfg.clamp01((float) RenderCfg.num(cfg, "texBlend", 0.0));
        float texExposure = RenderCfg.clamp((float) RenderCfg.num(cfg, "texExposure", 8.0), 0.001f, 100.0f);

        // Determine whether any sky texture is currently assigned. 2D textures and
        // cubemaps use separate parameters on the material; if none are defined
        // blending should be forced to zero.
        boolean hasTex =
                skydomeMat.getParam("SkyTexA") != null ||
                        skydomeMat.getParam("SkyTexB") != null ||
                        skydomeMat.getParam("SkyCubeA") != null ||
                        skydomeMat.getParam("SkyCubeB") != null;
        if (!hasTex) texBlend = 0.0f;

        boolean changedTex =
                !RenderCfg.approx(skyBlend, sdSkyBlend) ||
                        !RenderCfg.approx(texBlend, sdTexBlend) ||
                        !RenderCfg.approx(texExposure, sdTexExposure);

        if (changedTex) {
            sdSkyBlend = skyBlend;
            sdTexBlend = texBlend;
            sdTexExposure = texExposure;
            skydomeMat.setFloat("SkyBlend", skyBlend);
            skydomeMat.setFloat("TexBlend", texBlend);
            skydomeMat.setFloat("TexExposure", texExposure);
        }

        // Check whether any of the inputs actually changed. If not, we avoid
        // updating the material to save on state changes.
        boolean changed =
                changedTex ||
                        !RenderCfg.approx3(sdx, sdy, sdz, sdSunDx, sdSunDy, sdSunDz) ||
                        !RenderCfg.approx3(mdx, mdy, mdz, sdMoonDx, sdMoonDy, sdMoonDz) ||
                        !RenderCfg.approx(sr, sdSunR) || !RenderCfg.approx(sg, sdSunG) || !RenderCfg.approx(sb, sdSunB) || !RenderCfg.approx(sunInt, sdSunI) ||
                        !RenderCfg.approx(mr, sdMoonR) || !RenderCfg.approx(mg, sdMoonG) || !RenderCfg.approx(mb, sdMoonB) || !RenderCfg.approx(moonInt, sdMoonI) ||
                        !RenderCfg.approx(zr, sdZenR) || !RenderCfg.approx(zg, sdZenG) || !RenderCfg.approx(zb, sdZenB) ||
                        !RenderCfg.approx(hr, sdHorR) || !RenderCfg.approx(hg, sdHorG) || !RenderCfg.approx(hb, sdHorB) ||
                        !RenderCfg.approx(haze, sdHaze) ||
                        !RenderCfg.approx(sunDisk, sdSunDisk) ||
                        !RenderCfg.approx(moonDisk, sdMoonDisk) ||
                        !RenderCfg.approx(exposure, sdExposure);

        if (!changed) return;

        // Cache new values.
        sdSunDx = sdx;
        sdSunDy = sdy;
        sdSunDz = sdz;
        sdMoonDx = mdx;
        sdMoonDy = mdy;
        sdMoonDz = mdz;
        sdSunR = sr;
        sdSunG = sg;
        sdSunB = sb;
        sdSunI = sunInt;
        sdMoonR = mr;
        sdMoonG = mg;
        sdMoonB = mb;
        sdMoonI = moonInt;
        sdZenR = zr;
        sdZenG = zg;
        sdZenB = zb;
        sdHorR = hr;
        sdHorG = hg;
        sdHorB = hb;
        sdHaze = haze;
        sdSunDisk = sunDisk;
        sdMoonDisk = moonDisk;
        sdExposure = exposure;

        // Normalise sun and moon direction vectors. If both zero, provide sensible defaults
        // so that the shader receives unit vectors and avoids division by zero.
        Vector3f sdir = new Vector3f(sdx, sdy, sdz);
        if (sdir.lengthSquared() < 1e-6f) sdir.set(-1, -1, -1);
        sdir.normalizeLocal();
        Vector3f mdir = new Vector3f(mdx, mdy, mdz);
        if (mdir.lengthSquared() < 1e-6f) mdir.set(1, -1, 0);
        mdir.normalizeLocal();

        // Update all material uniforms atomically.
        skydomeMat.setVector3("SunDir", sdir);
        skydomeMat.setVector3("MoonDir", mdir);
        skydomeMat.setColor("SunColor", new ColorRGBA(sr, sg, sb, 1f));
        skydomeMat.setFloat("SunIntensity", sunInt);
        skydomeMat.setColor("MoonColor", new ColorRGBA(mr, mg, mb, 1f));
        skydomeMat.setFloat("MoonIntensity", moonInt);
        skydomeMat.setColor("ZenithColor", new ColorRGBA(zr, zg, zb, 1f));
        skydomeMat.setColor("HorizonColor", new ColorRGBA(hr, hg, hb, 1f));
        skydomeMat.setFloat("Haze", haze);
        skydomeMat.setFloat("SunDisk", sunDisk);
        skydomeMat.setFloat("MoonDisk", moonDisk);
        skydomeMat.setFloat("Exposure", exposure);

        // Propagate updated material to all geometries. This is important if the
        // geometry array was created before the material was assigned (for
        // instance, after switching textures). We only set the material on
        // geometries whose material reference differs to avoid redundant state
        // changes within jME's render manager.
        if (skydomeGeoms != null) {
            for (Geometry g : skydomeGeoms) {
                if (g.getMaterial() != skydomeMat) {
                    g.setMaterial(skydomeMat);
                }
            }
        }
    }

    /**
     * Clears a parameter on the material if it exists. This helper checks that
     * the material definition declares the parameter and that it currently has
     * a value before attempting to clear it. Avoids unnecessary exceptions from
     * the underlying Material class.
     *
     * @param m    material on which to clear a parameter
     * @param name the name of the parameter to clear
     */
    private static void safeClearParam(Material m, String name) {
        if (m == null || name == null) {
            return;
        }
        if (m.getMaterialDef() == null) {
            return;
        }
        if (m.getMaterialDef().getMaterialParam(name) == null) {
            return;
        }
        if (m.getParam(name) == null) {
            return;
        }
        m.clearParam(name);
    }

    /**
     * Assigns the first sky texture to the dome. The supplied asset may be a
     * standard 2D texture or a cubemap; the code detects the type and configures
     * filtering and wrap modes accordingly. When switching between 2D and cube
     * textures, this method validates that the previously assigned textures are
     * of the same type, since mixing types across A/B channels is unsupported.
     *
     * @param asset path to the texture asset to assign as SkyTexA
     */
    public void skyDomeTexA(String asset) {
        if (asset == null || asset.isBlank()) {
            throw new IllegalArgumentException("[render] skyDomeTexA: asset is blank");
        }
        final String a = asset.trim();
        ensureSkyDomeExists();
        final Texture t = assets.loadTexture(a);
        if (t == null) {
            throw new IllegalStateException("[render] skyDomeTexA: loadTexture returned null: " + a);
        }
        final boolean useCube = (t instanceof com.jme3.texture.TextureCubeMap);
        configureSkyTexture(t, useCube);
        if (sdUseCube == null) {
            sdUseCube = useCube;
        } else if (sdUseCube != useCube) {
            throw new IllegalStateException("[render] SkyDome A/B type mismatch: A is " +
                    (useCube ? "CUBE" : "2D") + " but existing mode is " + (sdUseCube ? "CUBE" : "2D"));
        }
        if (useCube) {
            skydomeMat.setTexture("SkyCubeA", t);
            skydomeMat.setBoolean("UseCube", true);
            safeClearParam(skydomeMat, "SkyTexA");
            return;
        }
        skydomeMat.setTexture("SkyTexA", t);
        skydomeMat.setBoolean("UseCube", false);
        safeClearParam(skydomeMat, "SkyCubeA");
    }

    /**
     * Assigns the second sky texture to the dome. The semantics mirror those of
     * {@link #skyDomeTexA(String)}. See that method for details.
     *
     * @param asset path to the texture asset to assign as SkyTexB
     */
    public void skyDomeTexB(String asset) {
        if (asset == null || asset.isBlank()) {
            throw new IllegalArgumentException("[render] skyDomeTexB: asset is blank");
        }
        final String a = asset.trim();
        ensureSkyDomeExists();
        final Texture t = assets.loadTexture(a);
        if (t == null) {
            throw new IllegalStateException("[render] skyDomeTexB: loadTexture returned null: " + a);
        }
        final boolean useCube = (t instanceof com.jme3.texture.TextureCubeMap);
        configureSkyTexture(t, useCube);
        if (sdUseCube == null) {
            sdUseCube = useCube;
        } else if (sdUseCube != useCube) {
            throw new IllegalStateException("[render] SkyDome A/B type mismatch: B is " +
                    (useCube ? "CUBE" : "2D") + " but existing mode is " + (sdUseCube ? "CUBE" : "2D"));
        }
        if (useCube) {
            skydomeMat.setTexture("SkyCubeB", t);
            skydomeMat.setBoolean("UseCube", true);
            safeClearParam(skydomeMat, "SkyTexB");
            return;
        }
        skydomeMat.setTexture("SkyTexB", t);
        skydomeMat.setBoolean("UseCube", false);
        safeClearParam(skydomeMat, "SkyCubeB");
    }

    /**
     * Ensures that the dome model and material are loaded and attached to the
     * scene. If the dome has already been instantiated, this method returns
     * immediately.
     */
    private void ensureSkyDomeExists() {
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
        Material m = new Material(assets, "MatDefs/Sky/SkyDome.j3md");
        bindSkyMaterial(dome, m);
        dome.setName("SkyDome");
        dome.setQueueBucket(RenderQueue.Bucket.Sky);
        dome.setCullHint(Spatial.CullHint.Never);
        dome.setShadowMode(RenderQueue.ShadowMode.Off);
        dome.setLocalScale(SKYDOME_SCALE);
        app.getRootNode().attachChild(dome);
        skydome = dome;
        skydomeMat = m;
        log.info("RenderApi: skydome created model='{}' geoms={}",
                SKYDOME_MODEL_ASSET, (skydomeGeoms == null ? 0 : skydomeGeoms.length));
    }

    /** Assigns the provided material to all geometries inside the loaded dome. */
    private void bindSkyMaterial(Spatial root, Material m) {
        ArrayList<Geometry> list = new ArrayList<>(8);

        root.depthFirstTraversal(sp -> {
            if (!(sp instanceof Geometry g)) return;

            g.setMaterial(m);
            g.setQueueBucket(RenderQueue.Bucket.Sky);
            g.setCullHint(Spatial.CullHint.Never);
            g.setShadowMode(RenderQueue.ShadowMode.Off);

            list.add(g);
        });

        skydomeGeoms = list.isEmpty() ? null : list.toArray(new Geometry[0]);
    }

    /**
     * Clears all sky textures on the dome. This resets the internal type flag
     * and leaves the material with no assigned textures. After clearing, a call
     * to {@link #skyDomeCfg(Value)} or assigning a new texture will set the
     * appropriate blending parameters.
     */
    public void skyDomeTexClear() {
        ensureSkyDomeExists();
        safeClearParam(skydomeMat, "SkyTex");
        safeClearParam(skydomeMat, "SkyCube");
        safeClearParam(skydomeMat, "SkyTexA");
        safeClearParam(skydomeMat, "SkyTexB");
        safeClearParam(skydomeMat, "SkyCubeA");
        safeClearParam(skydomeMat, "SkyCubeB");
        sdUseCube = null;
        log.info("RenderApi: skydome texture cleared");
    }

    private void configureSkyTexture(Texture t, boolean cube) {
        if (t == null) {
            return;
        }
        // Sky textures are a special case:
        // - For 2D equirectangular panoramas, mip selection near the seam can produce
        //   "sparkly sand" artifacts when the GPU chooses a tiny mip level. Disabling
        //   mipmaps is a pragmatic, stable fix.
        // - For cubemaps, trilinear is fine and reduces shimmer.
        if (!cube) {
            t.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        } else if (t.getMinFilter() != Texture.MinFilter.NearestNoMipMaps
                && t.getMinFilter() != Texture.MinFilter.BilinearNoMipMaps) {
            t.setMinFilter(Texture.MinFilter.Trilinear);
        }
        t.setMagFilter(Texture.MagFilter.Bilinear);
        // Repeat wrap for equirectangular images; edge clamp for cubes.
        if (!cube) {
            t.setWrap(Texture.WrapMode.EdgeClamp);
        } else {
            // Cubemaps rely on samplerCube which clamps at edges; edge clamp is safe.
            t.setWrap(Texture.WrapMode.EdgeClamp);
        }
        // Enforce a reasonable level of anisotropic filtering. If the value is
        // already higher than our suggested minimum, respect it.
        if (t.getAnisotropicFilter() < 8) {
            t.setAnisotropicFilter(8);
        }
    }
}