// FILE: org/foxesworld/kalitech/engine/modules/render/ShadowModule.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.texture.FrameBuffer;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadows.PcssDirectionalLightShadowRenderer;
import org.foxesworld.kalitech.engine.modules.render.shadows.ShadowRenderer;
import org.foxesworld.kalitech.engine.modules.render.shadows.ShadowTunable;
import org.foxesworld.kalitech.engine.modules.render.shadows.StableDirectionalLightShadowRenderer;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public final class ShadowModule {

    private final RenderThread thread;
    private final SimpleApplication app;
    private final AssetManager assets;
    private final Logger log;
    private final LightRigModule lights;

    private DirectionalLightShadowRenderer dlsr;
    private ShadowRenderer shadowRenderer;
    private ShadowTunable tunable;

    private final int reattachEveryFrames = 10;

    private int mapSize = 2048;
    private int splits = 4;
    private float lambda = 0.65f;
    private float intensity = 0.75f;

    private boolean enabled = true;

    private boolean snapEnabled = true;
    private float extentsPadding = 1.05f;
    private ViewPort attachedVp;

    private boolean dbg = true;
    private int dbgEveryFrames = 60;
    private float[] splitDistances = null;

    // mode flags
    private boolean useUnified = true; // StableDirectionalLightShadowRenderer = unified pipeline
    private boolean usePcss = true;    // PCSS layer on top of stable renderer

    private boolean autoStateAttached = false;
    private int frame = 0;

    public ShadowModule(RenderThread thread, SimpleApplication app, AssetManager assets, Logger log, LightRigModule lights) {
        if (thread == null) throw new IllegalArgumentException("thread is null");
        if (app == null) throw new IllegalArgumentException("app is null");
        if (assets == null) throw new IllegalArgumentException("assets is null");
        if (log == null) throw new IllegalArgumentException("log is null");
        if (lights == null) throw new IllegalArgumentException("lights is null");

        this.thread = thread;
        this.app = app;
        this.assets = assets;
        this.log = log;
        this.lights = lights;

        attachAutoStateOnce();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        rebuild();
    }

    public void setUseUnified(boolean enabled) {
        this.useUnified = enabled;
        rebuild();
    }

    public void setUsePcss(boolean enabled) {
        this.usePcss = enabled;
        rebuild();
    }

    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
        if (tunable != null) tunable.setSnapEnabled(enabled);
        log.info("[shadow] snapEnabled={}", enabled);
    }

    public void applyCfg(int mapSize, int splits, float lambda, float intensity) {
        this.mapSize = mapSize;
        this.splits = splits;
        this.lambda = lambda;
        this.intensity = intensity;
        rebuild();
    }

    public void setSplitDistances(float... distances) {
        if (distances == null || distances.length == 0) this.splitDistances = null;
        else this.splitDistances = distances.clone();

        if (tunable != null && splitDistances != null) tunable.setSplitDistances(splitDistances);
        log.info("[shadow] splitDistances updated: {}", Arrays.toString(this.splitDistances));
    }

    public void setExtentsPadding(float padding) {
        this.extentsPadding = Math.max(1.0f, padding);
        if (tunable != null) tunable.setExtentsPadding(this.extentsPadding);
        log.info("[shadow] extentsPadding={}", this.extentsPadding);
    }

    public void setDebug(boolean enabled, int everyFrames) {
        this.dbg = enabled;
        this.dbgEveryFrames = Math.max(1, everyFrames);
        if (tunable != null) tunable.setDebug(log, dbg, dbgEveryFrames);
        log.info("[shadow] debug={} everyFrames={}", dbg, dbgEveryFrames);
    }

    private void attachAutoStateOnce() {
        if (autoStateAttached) return;
        autoStateAttached = true;

        thread.onJme(() -> {
            try {
                app.getStateManager().attach(new BaseAppState() {
                    @Override
                    protected void initialize(com.jme3.app.Application app) {
                    }

                    @Override
                    protected void cleanup(com.jme3.app.Application app) {
                    }

                    @Override
                    protected void onEnable() {
                    }

                    @Override
                    protected void onDisable() {
                    }

                    @Override
                    public void update(float tpf) {
                        if (!enabled) return;
                        if (dlsr == null) return;

                        frame++;
                        if ((frame % reattachEveryFrames) != 0) return;
                        ensureAttached();
                    }
                });
                log.info("[shadow] auto-reattach state attached");
            } catch (Throwable t) {
                log.warn("[shadow] failed to attach auto state: {}", t.toString());
            }
        });
    }

    private void detachOld() {
        if (attachedVp != null && dlsr != null) {
            try {
                attachedVp.removeProcessor(dlsr);
            } catch (Throwable t) {
                log.warn("[shadow] removeProcessor failed: {}", t.toString());
            }
        }
        attachedVp = null;
    }

    private ViewPort pickSceneViewPort() {
        ViewPort vp = app.getViewPort();
        if (isCandidate(vp, app.getGuiViewPort())) return vp;

        RenderManager rm = app.getRenderManager();
        if (rm == null) return null;

        ViewPort gui = app.getGuiViewPort();

        ViewPort best = findFirstNonGuiWithScene(rm.getMainViews(), gui);
        if (best != null) return best;

        best = findFirstNonGuiWithScene(rm.getPreViews(), gui);
        if (best != null) return best;

        best = findFirstNonGuiWithScene(rm.getPostViews(), gui);
        if (best != null) return best;

        return null;
    }

    private ViewPort findFirstNonGuiWithScene(List<ViewPort> vps, ViewPort gui) {
        for (ViewPort vp : vps) if (isCandidate(vp, gui)) return vp;
        return null;
    }

    private boolean isCandidate(ViewPort vp, ViewPort gui) {
        if (vp == null) return false;
        if (vp == gui) return false;
        if ("Gui Default".equals(vp.getName())) return false;
        if (vp.getScenes() == null || vp.getScenes().isEmpty()) return false;
        return true;
    }

    public void rebuild() {
        thread.onJme(() -> {
            detachOld();
            dlsr = null;
            shadowRenderer = null;
            tunable = null;

            if (!enabled) {
                log.info("[shadow] disabled");
                return;
            }

            final DirectionalLight primary = lights.primaryLight();
            if (primary == null) {
                log.warn("[shadow] cannot enable: primary directional light is null (LightRig not ready?)");
                return;
            }

            final ViewPort vp = pickSceneViewPort();
            if (vp == null) {
                log.warn("[shadow] cannot attach: no suitable scene viewport found.");
                return;
            }

            // choose implementation
            final ShadowRenderer r;
            if (usePcss) {
                r = new PcssDirectionalLightShadowRenderer(assets, mapSize, splits);
            } else if (useUnified) {
                // "Unified" = stable pipeline (snap + stable basis + hysteresis + stable fitter)
                r = new StableDirectionalLightShadowRenderer(assets, mapSize, splits);
            } else {
                // basic JME-like (still supports clearShadows contract)
                r = new BasicShadowRenderer(assets, mapSize, splits);
            }

            r.setLight(primary);
            r.setLambda(lambda);
            r.setShadowIntensity(intensity);

            shadowRenderer = r;
            tunable = (r instanceof ShadowTunable) ? (ShadowTunable) r : null;

            if (tunable != null) {
                tunable.setExtentsPadding(extentsPadding);
                tunable.setSnapEnabled(snapEnabled);
                tunable.setDebug(log, dbg, dbgEveryFrames);

                tunable.setShadowBias(0.0008f);
                tunable.setShadowSlopeBias(2.0f);
                tunable.setShadowNormalOffset(0.0f);

                tunable.setCascadeBlendEnabled(true);
                tunable.setCascadeBlendLength(1.5f);

                if (splitDistances != null) tunable.setSplitDistances(splitDistances);
            }

            dlsr = (DirectionalLightShadowRenderer) r;
            attachedVp = vp;

            if (!vp.getProcessors().contains(dlsr)) vp.addProcessor(dlsr);

            log.info("[shadow] attached vp='{}' cam={} scenes={} renderer={} unified={} pcss={}",
                    vp.getName(),
                    vp.getCamera() != null ? vp.getCamera().hashCode() : 0,
                    vp.getScenes() != null ? vp.getScenes().size() : 0,
                    dlsr.getClass().getSimpleName(),
                    useUnified,
                    usePcss
            );
        });
    }

    public DirectionalLightShadowRenderer renderer() {
        return dlsr;
    }

    private void ensureAttached() {
        if (dlsr == null) return;

        final ViewPort want = pickSceneViewPort();
        if (want == null) return;
        if (attachedVp == want) return;

        if (attachedVp != null) {
            try {
                attachedVp.removeProcessor(dlsr);
            } catch (Throwable t) {
                log.warn("[shadow] removeProcessor failed: {}", t.toString());
            }
        }

        attachedVp = want;
        if (!want.getProcessors().contains(dlsr)) want.addProcessor(dlsr);

        log.info("[shadow] reattached vp='{}' cam={} scenes={}",
                want.getName(),
                want.getCamera() != null ? want.getCamera().hashCode() : 0,
                want.getScenes() != null ? want.getScenes().size() : 0);
    }

    public void onPrimaryLightChanged() {
        thread.onJme(() -> {
            if (dlsr == null) return;
            DirectionalLight primary = lights.primaryLight();
            if (primary == null) {
                log.warn("[shadow] primary light became null => shadows will stop");
                return;
            }
            dlsr.setLight(primary);
        });
    }

    public void clearShadowMaps(String reason) {
        final String why = (reason == null || reason.isBlank()) ? "manual" : reason.trim();

        thread.onJme(() -> {
            if (!enabled) {
                log.info("[shadow] clearShadowMaps ignored (disabled) reason={}", why);
                return;
            }
            if (shadowRenderer == null || dlsr == null) {
                log.info("[shadow] clearShadowMaps: renderer not ready => rebuild. reason={}", why);
                rebuild();
                return;
            }

            ensureAttached();

            final ViewPort vp = attachedVp;
            final RenderManager rm = app.getRenderManager();
            if (vp == null || rm == null) {
                log.warn("[shadow] clearShadowMaps failed: vp/rm is null. reason={}", why);
                return;
            }

            log.info("[shadow] clearShadowMaps begin reason={} vp='{}' proc={}",
                    why, vp.getName(), dlsr.getClass().getSimpleName());

            shadowRenderer.clearShadows(rm, vp);

            log.info("[shadow] clearShadowMaps done reason={}", why);
        });
    }

    public void clearShadowMaps() {
        clearShadowMaps("manual");
    }

    /**
     * Minimal "vanilla-like" renderer that still respects ShadowRenderer contract.
     * Uses the same GPU-clear reflection strategy as stable renderer.
     */
    private static final class BasicShadowRenderer extends DirectionalLightShadowRenderer implements ShadowRenderer {

        BasicShadowRenderer(AssetManager assets, int shadowMapSize, int nbSplits) {
            super(assets, shadowMapSize, nbSplits);
        }

        private static FrameBuffer[] tryGetShadowFbsByReflection(Object self) {
            try {
                Class<?> c = self.getClass();
                while (c != null && c != Object.class) {
                    for (String n : new String[]{"shadowFB", "shadowFbs", "shadowFBOs", "shadowFbo", "shadowFramebuffers"}) {
                        try {
                            Field f = c.getDeclaredField(n);
                            f.setAccessible(true);
                            Object v = f.get(self);
                            if (v instanceof FrameBuffer[]) return (FrameBuffer[]) v;
                        } catch (NoSuchFieldException ignored) {
                        }
                    }
                    c = c.getSuperclass();
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        @Override
        public void clearShadows(RenderManager rm, ViewPort vp) {
            if (rm == null) return;
            Renderer r = rm.getRenderer();
            if (r == null) return;

            FrameBuffer[] fbs = tryGetShadowFbsByReflection(this);
            if (fbs == null || fbs.length == 0) return;

            FrameBuffer prev = r.getCurrentFrameBuffer();
            try {
                for (FrameBuffer fb : fbs) {
                    if (fb == null) continue;
                    r.setFrameBuffer(fb);
                    r.clearBuffers(false, true, false);
                }
            } catch (Throwable ignored) {
            } finally {
                try {
                    r.setFrameBuffer(prev);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}