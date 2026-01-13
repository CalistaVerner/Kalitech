// FILE: org/foxesworld/kalitech/engine/modules/render/ShadowModule.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.shadows.PcssDirectionalLightShadowRenderer;
import org.foxesworld.kalitech.engine.modules.render.shadows.ShadowRenderer;
import org.foxesworld.kalitech.engine.modules.render.shadows.StableDirectionalLightShadowRenderer;

import java.util.Arrays;
import java.util.List;

public final class ShadowModule {

    private final RenderThread thread;
    private final SimpleApplication app;
    private final AssetManager assets;
    private final Logger log;
    private final LightRigModule lights;

    private DirectionalLightShadowRenderer dlsr;
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
    private boolean usePcss = true;
    // unified handle (no instanceof in public API)
    private ShadowRenderer shadowRenderer;
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

    private static String vpInfo(ViewPort vp) {
        if (vp == null) return "null";
        StringBuilder sb = new StringBuilder(256);
        sb.append("name=").append(vp.getName());
        sb.append(" camHash=").append(vp.getCamera() != null ? vp.getCamera().hashCode() : 0);
        sb.append(" scenes=").append(vp.getScenes() != null ? vp.getScenes().size() : 0);
        sb.append(" procs=").append(vp.getProcessors() != null ? vp.getProcessors().size() : 0);
        return sb.toString();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        rebuild();
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

    public void setSnapEnabled(boolean enabled) {
        this.snapEnabled = enabled;
        if (shadowRenderer instanceof StableDirectionalLightShadowRenderer s) s.setSnapEnabled(enabled);
        log.info("[shadow] snapEnabled={}", enabled);
    }

    public void applyCfg(int mapSize, int splits, float lambda, float intensity) {
        this.mapSize = mapSize;
        this.splits = splits;
        this.lambda = lambda;
        this.intensity = intensity;
        rebuild();
    }

    public void setUsePcss(boolean enabled) {
        this.usePcss = enabled;
        rebuild();
    }

    public void setSplitDistances(float... distances) {
        if (distances == null || distances.length == 0) {
            this.splitDistances = null;
        } else {
            this.splitDistances = distances.clone();
        }
        if (shadowRenderer instanceof StableDirectionalLightShadowRenderer s) {
            s.setSplitDistances(this.splitDistances);
        }
        log.info("[shadow] splitDistances updated: {}", Arrays.toString(this.splitDistances));
    }

    public void setExtentsPadding(float padding) {
        this.extentsPadding = Math.max(1.0f, padding);
        if (shadowRenderer instanceof StableDirectionalLightShadowRenderer s) s.setExtentsPadding(this.extentsPadding);
        log.info("[shadow] extentsPadding={}", this.extentsPadding);
    }

    public void setDebug(boolean enabled, int everyFrames) {
        this.dbg = enabled;
        this.dbgEveryFrames = Math.max(1, everyFrames);
        if (shadowRenderer instanceof StableDirectionalLightShadowRenderer s) {
            s.setDebugLogger(log);
            s.setDebugEnabled(dbg);
            s.setDebugEveryFrames(dbgEveryFrames);
        }
        log.info("[shadow] debug={} everyFrames={}", dbg, dbgEveryFrames);
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
        for (ViewPort vp : vps) {
            if (isCandidate(vp, gui)) return vp;
        }
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
                dumpAllViewPorts("noSuitableVp");
                return;
            }

            shadowRenderer = usePcss
                    ? new PcssDirectionalLightShadowRenderer(assets, mapSize, splits)
                    : new StableDirectionalLightShadowRenderer(assets, mapSize, splits);

            shadowRenderer.setLight(primary);
            shadowRenderer.setLambda(lambda);
            shadowRenderer.setShadowIntensity(intensity);

            // stable knobs are common base for both
            StableDirectionalLightShadowRenderer s = (StableDirectionalLightShadowRenderer) shadowRenderer;
            s.setExtentsPadding(extentsPadding);
            s.setSnapEnabled(snapEnabled);

            s.setDebugLogger(log);
            s.setDebugEnabled(dbg);
            s.setDebugEveryFrames(dbgEveryFrames);

            s.setShadowBias(0.0008f);
            s.setShadowSlopeBias(2.0f);
            s.setShadowNormalOffset(0.0f);

            s.setCascadeBlendEnabled(true);
            s.setCascadeBlendLength(1.5f);

            if (splitDistances != null) s.setSplitDistances(splitDistances);

            dlsr = (DirectionalLightShadowRenderer) shadowRenderer;
            attachedVp = vp;

            if (!vp.getProcessors().contains(dlsr)) vp.addProcessor(dlsr);

            log.info("[shadow] attached vp='{}' cam={} scenes={}",
                    vp.getName(),
                    vp.getCamera() != null ? vp.getCamera().hashCode() : 0,
                    vp.getScenes() != null ? vp.getScenes().size() : 0);

            dumpAllViewPorts("afterAttach");
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

    /**
     * The ONE correct public API to kill "ghost shadows".
     * - Runs on JME thread
     * - Ensures viewport attach
     * - Clears GPU shadow maps via ShadowRenderer.clearShadows()
     */
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

    /**
     * Convenience overload.
     */
    public void clearShadowMaps() {
        clearShadowMaps("manual");
    }

    private void dumpAllViewPorts(String tag) {
        RenderManager rm = app.getRenderManager();
        if (rm == null) return;

        log.info("[shadow][vpDump] {} PRE:", tag);
        for (ViewPort vp : rm.getPreViews()) log.info("[shadow][vpDump]   {}", vpInfo(vp));

        log.info("[shadow][vpDump] {} MAIN:", tag);
        for (ViewPort vp : rm.getMainViews()) log.info("[shadow][vpDump]   {}", vpInfo(vp));

        log.info("[shadow][vpDump] {} POST:", tag);
        for (ViewPort vp : rm.getPostViews()) log.info("[shadow][vpDump]   {}", vpInfo(vp));

        log.info("[shadow][vpDump] {} GUI:", tag);
        log.info("[shadow][vpDump]   {}", vpInfo(app.getGuiViewPort()));
    }
}