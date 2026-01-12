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
import com.jme3.bounding.BoundingVolume;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * Utility class responsible for managing a sky dome within the Kalitech engine.
 *
 * <p>
 * A <strong>skydome</strong> is a hemispherical mesh that encloses the scene and
 * gives the illusion of a distant sky. Compared to a skybox (a textured cube),
 * a skydome provides better pixel coverage around the horizon and eliminates
 * corner artefacts. It also allows per–vertex colour gradients and animation
 * effects, which are difficult with a skybox. These advantages make skydomes
 * well suited for dynamic day/night cycles and moving cloud layers, albeit at
 * the cost of increased vertex count【143815102460990†L83-L86】【159722157242832†L188-L208】.
 *
 * <p>
 * The implementation here loads an OBJ model containing the dome geometry and
 * applies a custom material. To avoid visible seams where the U texture
 * coordinates wrap from 1.0 back to 0.0, the mesh is converted to a non‑indexed
 * representation and duplicate vertices are introduced along the seam. The
 * texture wrap mode is set to {@link Texture.WrapMode#Repeat} for 2D textures
 * so that values greater than 1 wrap correctly and the seam disappears【129796243801363†L1107-L1121】.
 * Cubemap textures use {@link Texture.WrapMode#EdgeClamp} instead, as repeat
 * wrapping is undefined for cubemaps. Trilinear filtering and a minimum
 * anisotropic filter of 8× are enforced to minimise shimmering at oblique
 * angles, which is especially noticeable along the horizon line.
 */
public final class SkyModule {

    /**
     * The relative path to the sky dome model.
     */
    private static final String SKYDOME_MODEL_ASSET = "Models/Sky/skydome.obj";
    /**
     * The world scale applied to the sky dome.
     */
    private static final float SKYDOME_SCALE = 1000f;

    private final SimpleApplication app;
    private final AssetManager assets;
    private final Logger log;

    /**
     * The root spatial for the skydome. When non‑null, this node is attached
     * directly to the application's root node and detached on clear.
     */
    private Spatial skydome;
    /**
     * Material shared by all geometry making up the dome.
     */
    private Material skydomeMat;
    /**
     * Tracks whether currently loaded sky textures are cubemaps or 2D textures.
     */
    private Boolean sdUseCube = null;
    /**
     * Cached references to the geometries comprising the dome.
     */
    private Geometry[] skydomeGeoms;

    // Cached uniform values to avoid redundant material updates.
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
     * Copies three consecutive floats from one FloatBuffer into another. This
     * helper avoids object allocations inside the seam fix logic.
     *
     * @param src    source buffer
     * @param srcPos starting position in the source buffer
     * @param dst    destination buffer
     */
    private static void put3(FloatBuffer src, int srcPos, FloatBuffer dst) {
        dst.put(src.get(srcPos));
        dst.put(src.get(srcPos + 1));
        dst.put(src.get(srcPos + 2));
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
        float texBlend = RenderCfg.clamp01((float) RenderCfg.num(cfg, "texBlend", 0.0));
        float texExposure = RenderCfg.clamp((float) RenderCfg.num(cfg, "texExposure", 8.0), 0.001f, 100.0f);

        // Determine whether any sky texture is currently assigned. 2D textures and
        // cubemaps use separate parameters on the material; if none are defined
        // blending should be forced to zero.
        boolean hasTex =
                skydomeMat.getParam("SkyTex") != null ||
                        skydomeMat.getParam("SkyCube") != null ||
                        skydomeMat.getParam("SkyTexA") != null ||
                        skydomeMat.getParam("SkyTexB") != null ||
                        skydomeMat.getParam("SkyCubeA") != null ||
                        skydomeMat.getParam("SkyCubeB") != null;
        if (!hasTex) texBlend = 0.0f;

        skydomeMat.setFloat("TexBlend", texBlend);
        skydomeMat.setFloat("TexExposure", texExposure);

        // Check whether any of the inputs actually changed. If not, we avoid
        // updating the material to save on state changes.
        boolean changed =
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
        log.info("RenderApi: skydome bind material geoms={}", (skydomeGeoms == null ? 0 : skydomeGeoms.length));
        log.info("RenderApi: skydome created (model='{}')", SKYDOME_MODEL_ASSET);
    }

    /**
     * Assigns the provided material to every geometry contained in the root node
     * and fixes the UV seam on each mesh. Geometry names and buffer presence
     * information are logged for debugging purposes. The dome’s bounding box is
     * recentred on the origin so that scaling operates uniformly around the
     * viewer. Note that {@link #bakeMeshCenterToOriginOnce(Geometry)} already
     * invokes {@link #fixUvSeamOnce(Geometry)}, so this method no longer calls
     * it explicitly to avoid redundant seam fixes.
     *
     * @param root the loaded sky dome spatial
     * @param m    the material to assign to all contained geometries
     */
    private void bindSkyMaterial(Spatial root, Material m) {
        ArrayList<Geometry> list = new ArrayList<>(8);
        root.depthFirstTraversal(sp -> {
            if (!(sp instanceof Geometry g)) return;
            // Move the mesh so that its local centre is at the origin. This avoids
            // translation artefacts when scaling the dome and keeps it centred
            // around the camera. The method also fixes the UV seam on first run.
            bakeMeshCenterToOriginOnce(g);
            g.setMaterial(m);
            g.setQueueBucket(RenderQueue.Bucket.Sky);
            g.setCullHint(Spatial.CullHint.Never);
            g.setShadowMode(RenderQueue.ShadowMode.Off);
            Mesh mesh = g.getMesh();
            boolean hasPos = mesh != null && mesh.getBuffer(VertexBuffer.Type.Position) != null;
            boolean hasNor = mesh != null && mesh.getBuffer(VertexBuffer.Type.Normal) != null;
            boolean hasUv = mesh != null && mesh.getBuffer(VertexBuffer.Type.TexCoord) != null;
            int tris = (mesh == null) ? 0 : mesh.getTriangleCount();
            log.info("RenderApi: skydome geom='{}' buffers: pos={} normal={} uv={} tris={}",
                    g.getName(), hasPos, hasNor, hasUv, tris);
            list.add(g);
        });
        skydomeGeoms = list.isEmpty() ? null : list.toArray(new Geometry[0]);
    }

    /**
     * Recentres the given geometry’s mesh so that its bounding volume is around
     * the origin and fixes the UV seam once. The result is cached in user data
     * fields to avoid repeated work. After adjusting the vertex positions, the
     * mesh’s bound and counts are updated.
     *
     * @param g the geometry to bake
     */
    private void bakeMeshCenterToOriginOnce(Geometry g) {
        Boolean baked = g.getUserData("sky.bakedCenter");
        if (Boolean.TRUE.equals(baked)) {
            return;
        }
        Mesh mesh = g.getMesh();
        if (mesh == null) {
            return;
        }
        VertexBuffer vb = mesh.getBuffer(VertexBuffer.Type.Position);
        if (vb == null) {
            return;
        }
        mesh.updateBound();
        BoundingVolume bv = mesh.getBound();
        if (bv == null) {
            return;
        }
        Vector3f c = bv.getCenter();
        if (c == null) {
            return;
        }
        // If the centre is already very close to the origin, skip translation.
        if (c.lengthSquared() < 1e-10f) {
            g.setUserData("sky.bakedCenter", true);
            return;
        }
        FloatBuffer fb = (FloatBuffer) vb.getData();
        if (fb == null) {
            return;
        }
        // Translate each vertex by subtracting the centre. We operate directly on
        // the FloatBuffer for performance. The buffer is updated in place.
        for (int i = 0; i < fb.limit(); i += 3) {
            fb.put(i, fb.get(i) - c.x);
            fb.put(i + 1, fb.get(i + 1) - c.y);
            fb.put(i + 2, fb.get(i + 2) - c.z);
        }
        vb.updateData(fb);
        mesh.updateBound();
        // Reset the geometry’s local translation since we baked the offset into
        // vertex positions.
        g.setLocalTranslation(0f, 0f, 0f);
        g.setUserData("sky.bakedCenter", true);
        // Fix the UV seam once after re‑centring. This prevents visible seams
        // along the longitude that wraps from 1 back to 0.
        fixUvSeamOnce(g);
        log.info("RenderApi: skydome baked center to origin geom='{}' center=({}, {}, {})",
                g.getName(), c.x, c.y, c.z);
    }

    /**
     * Fixes the classic U=0/1 seam for 2D equirectangular textures by duplicating
     * vertices along the seam and adjusting their U coordinates. For cubemaps the
     * underlying mesh is left untouched. The method is idempotent and will only
     * run once per geometry.
     *
     * @param g geometry whose mesh seam is to be fixed
     */
    private void fixUvSeamOnce(Geometry g) {
        Boolean baked = g.getUserData("sky.fixedUvSeam");
        if (Boolean.TRUE.equals(baked)) {
            return;
        }
        Mesh mesh = g.getMesh();
        if (mesh == null) {
            return;
        }
        VertexBuffer posVb = mesh.getBuffer(VertexBuffer.Type.Position);
        VertexBuffer uvVb = mesh.getBuffer(VertexBuffer.Type.TexCoord);
        if (posVb == null || uvVb == null) {
            g.setUserData("sky.fixedUvSeam", true);
            return;
        }
        // Only handle triangle mode; skip seam fixing for other primitive types.
        if (mesh.getMode() != Mesh.Mode.Triangles) {
            g.setUserData("sky.fixedUvSeam", true);
            return;
        }
        // If the mesh is non‑indexed, fix in place; otherwise expand and fix.
        VertexBuffer idxVb = mesh.getBuffer(VertexBuffer.Type.Index);
        if (idxVb == null) {
            fixSeamNonIndexed(mesh);
            g.setUserData("sky.fixedUvSeam", true);
            return;
        }
        expandIndexedAndFixSeam(mesh);
        g.setUserData("sky.fixedUvSeam", true);
        log.info("RenderApi: skydome UV seam fixed (expanded to non-indexed) geom='{}'", g.getName());
    }

    /**
     * Fixes the U=0/1 seam on a non‑indexed mesh by adjusting the U coordinate of
     * vertices that cross the seam. After modification, the mesh’s bounds are
     * updated to reflect the possibly changed vertex positions. This method does
     * nothing if the texture coordinate buffer length is not divisible by 2.
     *
     * @param mesh the non‑indexed mesh to process
     */
    private void fixSeamNonIndexed(Mesh mesh) {
        VertexBuffer uvVb = mesh.getBuffer(VertexBuffer.Type.TexCoord);
        if (uvVb == null) {
            return;
        }
        FloatBuffer uv = (FloatBuffer) uvVb.getData();
        if (uv == null) {
            return;
        }
        int lim = uv.limit();
        if ((lim % 2) != 0) {
            return;
        }
        int verts = lim / 2;
        int tris = verts / 3;
        for (int t = 0; t < tris; t++) {
            int v0 = (t * 3 + 0) * 2;
            int v1 = (t * 3 + 1) * 2;
            int v2 = (t * 3 + 2) * 2;
            float u0 = uv.get(v0);
            float u1 = uv.get(v1);
            float u2 = uv.get(v2);
            float min = Math.min(u0, Math.min(u1, u2));
            float max = Math.max(u0, Math.max(u1, u2));
            if (max - min > 0.5f) {
                if (u0 < 0.5f) uv.put(v0, u0 + 1.0f);
                if (u1 < 0.5f) uv.put(v1, u1 + 1.0f);
                if (u2 < 0.5f) uv.put(v2, u2 + 1.0f);
            }
        }
        uvVb.updateData(uv);
        // Update counts and bounding volume after modifying the buffer. Without
        // updating the bound, culling calculations may be incorrect.
        mesh.updateCounts();
        mesh.updateBound();
    }

    /**
     * Expands an indexed mesh into a non‑indexed representation and applies seam
     * corrections on the U coordinate. The index buffer is removed and new
     * position, normal and UV buffers are created. After replacement the mesh
     * becomes static to allow the GPU to optimise it. Bounds and counts are
     * updated appropriately.
     *
     * @param mesh the indexed mesh to expand and fix
     */
    private void expandIndexedAndFixSeam(Mesh mesh) {
        VertexBuffer posVb = mesh.getBuffer(VertexBuffer.Type.Position);
        VertexBuffer norVb = mesh.getBuffer(VertexBuffer.Type.Normal);
        VertexBuffer uvVb = mesh.getBuffer(VertexBuffer.Type.TexCoord);
        if (posVb == null || uvVb == null) {
            return;
        }
        FloatBuffer pos = (FloatBuffer) posVb.getData();
        FloatBuffer nor = (norVb != null) ? (FloatBuffer) norVb.getData() : null;
        FloatBuffer uv = (FloatBuffer) uvVb.getData();
        if (pos == null || uv == null) {
            return;
        }
        com.jme3.scene.mesh.IndexBuffer ib = mesh.getIndicesAsList();
        int indexCount = ib.size();
        if (indexCount <= 0 || (indexCount % 3) != 0) {
            return;
        }
        int newVerts = indexCount;
        FloatBuffer newPos = BufferUtils.createFloatBuffer(newVerts * 3);
        FloatBuffer newNor = (nor != null) ? BufferUtils.createFloatBuffer(newVerts * 3) : null;
        FloatBuffer newUv = BufferUtils.createFloatBuffer(newVerts * 2);
        for (int i = 0; i < indexCount; i += 3) {
            int ia = ib.get(i);
            int ibb = ib.get(i + 1);
            int ic = ib.get(i + 2);
            float ua = uv.get(ia * 2);
            float ub = uv.get(ibb * 2);
            float uc = uv.get(ic * 2);
            float minU = Math.min(ua, Math.min(ub, uc));
            float maxU = Math.max(ua, Math.max(ub, uc));
            boolean seamTri = (maxU - minU) > 0.5f;
            if (seamTri) {
                if (ua < 0.5f) ua += 1.0f;
                if (ub < 0.5f) ub += 1.0f;
                if (uc < 0.5f) uc += 1.0f;
            }
            // Vertex A
            put3(pos, ia * 3, newPos);
            if (newNor != null) put3(nor, ia * 3, newNor);
            newUv.put(ua).put(uv.get(ia * 2 + 1));
            // Vertex B
            put3(pos, ibb * 3, newPos);
            if (newNor != null) put3(nor, ibb * 3, newNor);
            newUv.put(ub).put(uv.get(ibb * 2 + 1));
            // Vertex C
            put3(pos, ic * 3, newPos);
            if (newNor != null) put3(nor, ic * 3, newNor);
            newUv.put(uc).put(uv.get(ic * 2 + 1));
        }
        newPos.flip();
        if (newNor != null) newNor.flip();
        newUv.flip();
        mesh.clearBuffer(VertexBuffer.Type.Index);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, newPos);
        if (newNor != null) {
            mesh.setBuffer(VertexBuffer.Type.Normal, 3, newNor);
        }
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, newUv);
        mesh.updateBound();
        mesh.updateCounts();
        mesh.setStatic();
    }

    /**
     * Configures filtering and wrapping on a sky texture. For equirectangular
     * textures the wrap mode is set to {@link Texture.WrapMode#Repeat} so that
     * sampling beyond 1.0 correctly wraps and hides seams. For cubemap textures
     * the wrap mode is {@link Texture.WrapMode#EdgeClamp}, which is the only
     * sensible choice for cube maps. Trilinear filtering is enforced unless
     * mipmaps are deliberately disabled. An anisotropic filter of at least
     * 8× is applied to improve clarity along the horizon, as recommended by
     * graphics best practices【143815102460990†L83-L86】.
     *
     * @param t    the texture to configure
     * @param cube whether the texture is a cubemap
     */
    private void configureSkyTexture(Texture t, boolean cube) {
        if (t == null) {
            return;
        }
        // If the texture already uses no mip maps, preserve that, otherwise use
        // trilinear filtering to reduce shimmering.
        if (t.getMinFilter() == Texture.MinFilter.NearestNoMipMaps || t.getMinFilter() == Texture.MinFilter.BilinearNoMipMaps) {
            // Intentionally leave as-is; the author explicitly requested no mipmaps.
        } else {
            t.setMinFilter(Texture.MinFilter.Trilinear);
        }
        t.setMagFilter(Texture.MagFilter.Bilinear);
        // Repeat wrap for equirectangular images; edge clamp for cubes.
        if (!cube) {
            t.setWrap(Texture.WrapMode.Repeat);
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