// FILE: org/foxesworld/kalitech/engine/modules/render/LightRig.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.modules.render.RenderCfg.*;

public final class LightRig {

    private static final Logger log = LogManager.getLogger(LightRig.class);

    private final RenderThread thread;
    private final SimpleApplication app;

    private AmbientLight ambient;
    private DirectionalLight sun;
    private DirectionalLight moon;

    private String primaryDirectional = "sun";

    private float _sunDx = Float.NaN, _sunDy = Float.NaN, _sunDz = Float.NaN;
    private float _sunR = Float.NaN, _sunG = Float.NaN, _sunB = Float.NaN, _sunI = Float.NaN;

    private float _moonDx = Float.NaN, _moonDy = Float.NaN, _moonDz = Float.NaN;
    private float _moonR = Float.NaN, _moonG = Float.NaN, _moonB = Float.NaN, _moonI = Float.NaN;

    private float _ambR = Float.NaN, _ambG = Float.NaN, _ambB = Float.NaN, _ambI = Float.NaN;

    public LightRig(RenderThread thread, SimpleApplication app) {
        this.thread = thread;
        this.app = app;
    }

    public void ensure() {
        ensureAmbientExists();
        ensureSunExists();
        ensureMoonExists();
    }

    public AmbientLight ambient() {
        return ambient;
    }

    public DirectionalLight sun() {
        return sun;
    }

    public DirectionalLight moon() {
        return moon;
    }

    public String primaryDirectional() {
        return primaryDirectional;
    }

    public DirectionalLight primaryLight() {
        if ("moon".equals(primaryDirectional)) return (moon != null ? moon : sun);
        return sun;
    }

    public void setPrimaryDirectional(String which) {
        final String w = (which == null ? "" : which.trim().toLowerCase());
        if (!w.equals("sun") && !w.equals("moon")) {
            throw new IllegalArgumentException("[render] setPrimaryDirectional: expected 'sun' or 'moon'");
        }
        thread.onJme(() -> {
            ensure();
            if (w.equals(primaryDirectional)) return;
            primaryDirectional = w;
            log.info("RenderApi: primaryDirectional={}", primaryDirectional);
        });
    }

    public void ambientCfg(Value cfg) {
        thread.onJme(() -> {
            ensureAmbientExists();

            double r = num(cfg, "r", numPath(cfg, "color", "r", 0.25));
            double g = num(cfg, "g", numPath(cfg, "color", "g", 0.28));
            double b = num(cfg, "b", numPath(cfg, "color", "b", 0.35));
            double intensity = num(cfg, "intensity", 1.0);

            float fr = (float) r, fg = (float) g, fb = (float) b;
            float fi = (float) Math.max(0.0, intensity);

            if (approx(fr, _ambR) && approx(fg, _ambG) && approx(fb, _ambB) && approx(fi, _ambI)) return;
            _ambR = fr;
            _ambG = fg;
            _ambB = fb;
            _ambI = fi;

            ambient.setColor(new ColorRGBA(fr, fg, fb, 1f).mult(fi));
        });
    }

    public void sunCfg(Value cfg) {
        thread.onJme(() -> {
            ensureSunExists();

            Value dir = member(cfg, "dir");
            Value col = member(cfg, "color");

            float dx = vec3x(dir, -1f);
            float dy = vec3y(dir, -1f);
            float dz = vec3z(dir, -0.3f);

            float r = vec3x(col, 1f);
            float g = vec3y(col, 0.98f);
            float b = vec3z(col, 0.9f);

            float intensity = (float) Math.max(0.0, num(cfg, "intensity", 1.2));

            if (approx(dx, _sunDx) && approx(dy, _sunDy) && approx(dz, _sunDz) &&
                    approx(r, _sunR) && approx(g, _sunG) && approx(b, _sunB) && approx(intensity, _sunI)) {
                return;
            }

            _sunDx = dx;
            _sunDy = dy;
            _sunDz = dz;
            _sunR = r;
            _sunG = g;
            _sunB = b;
            _sunI = intensity;

            Vector3f v = new Vector3f(dx, dy, dz);
            if (v.lengthSquared() < 1e-6f) v.set(-1, -1, -1);
            v.normalizeLocal();

            sun.setDirection(v);
            sun.setColor(new ColorRGBA(r, g, b, 1f).mult(intensity));
        });
    }

    public void moonCfg(Value cfg) {
        thread.onJme(() -> {
            ensureMoonExists();

            Value dir = member(cfg, "dir");
            Value col = member(cfg, "color");

            float dx = vec3x(dir, 1f);
            float dy = vec3y(dir, -1f);
            float dz = vec3z(dir, 0.3f);

            float r = vec3x(col, 0.45f);
            float g = vec3y(col, 0.55f);
            float b = vec3z(col, 0.85f);

            float intensity = (float) Math.max(0.0, num(cfg, "intensity", 0.0));

            if (approx(dx, _moonDx) && approx(dy, _moonDy) && approx(dz, _moonDz) &&
                    approx(r, _moonR) && approx(g, _moonG) && approx(b, _moonB) && approx(intensity, _moonI)) {
                return;
            }

            _moonDx = dx;
            _moonDy = dy;
            _moonDz = dz;
            _moonR = r;
            _moonG = g;
            _moonB = b;
            _moonI = intensity;

            Vector3f v = new Vector3f(dx, dy, dz);
            if (v.lengthSquared() < 1e-6f) v.set(1, -1, 0);
            v.normalizeLocal();

            moon.setDirection(v);
            moon.setColor(new ColorRGBA(r, g, b, 1f).mult(intensity));
        });
    }

    private void ensureAmbientExists() {
        if (ambient != null) return;
        ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(0.25f, 0.28f, 0.35f, 1f));
        app.getRootNode().addLight(ambient);
        log.info("RenderApi: ambient created");
    }

    private void ensureSunExists() {
        if (sun != null) return;
        sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-1, -1, -0.3f).normalizeLocal());
        sun.setColor(new ColorRGBA(1f, 0.98f, 0.90f, 1f).mult(1.2f));
        app.getRootNode().addLight(sun);
        log.info("RenderApi: sun created");
    }

    private void ensureMoonExists() {
        if (moon != null) return;
        moon = new DirectionalLight();
        moon.setDirection(new Vector3f(1, -1, 0.3f).normalizeLocal());
        moon.setColor(new ColorRGBA(0.45f, 0.55f, 0.85f, 1f).mult(0.0f));
        app.getRootNode().addLight(moon);
        log.info("RenderApi: moon created");
    }
}