/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.SimpleApplication
 *  com.jme3.light.AmbientLight
 *  com.jme3.light.DirectionalLight
 *  com.jme3.light.Light
 *  com.jme3.math.ColorRGBA
 *  com.jme3.math.Vector3f
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.render.light;

import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.Light;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.RenderCfg;
import org.foxesworld.kalitech.engine.modules.render.RenderThread;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class LightRigModule {
    private static final Logger log = LogManager.getLogger(LightRigModule.class);
    private final RenderThread thread;
    private final SimpleApplication app;
    private AmbientLight ambient;
    private DirectionalLight sun;
    private DirectionalLight moon;
    private String primaryDirectional = "sun";
    private float timeOfDay = 12.0f;
    private float dayLengthSeconds = 3600.0f;
    private float _sunDx = Float.NaN;
    private float _sunDy = Float.NaN;
    private float _sunDz = Float.NaN;
    private float _sunR = Float.NaN;
    private float _sunG = Float.NaN;
    private float _sunB = Float.NaN;
    private float _sunI = Float.NaN;
    private float _moonDx = Float.NaN;
    private float _moonDy = Float.NaN;
    private float _moonDz = Float.NaN;
    private float _moonR = Float.NaN;
    private float _moonG = Float.NaN;
    private float _moonB = Float.NaN;
    private float _moonI = Float.NaN;
    private float _ambR = Float.NaN;
    private float _ambG = Float.NaN;
    private float _ambB = Float.NaN;
    private float _ambI = Float.NaN;

    public LightRigModule(RenderThread thread, SimpleApplication app) {
        this.thread = thread;
        this.app = app;
    }

    public void ensure() {
        this.ensureAmbientExists();
        this.ensureSunExists();
        this.ensureMoonExists();
    }

    public AmbientLight ambient() {
        return this.ambient;
    }

    public DirectionalLight sun() {
        return this.sun;
    }

    public DirectionalLight moon() {
        return this.moon;
    }

    public String primaryDirectional() {
        return this.primaryDirectional;
    }

    public DirectionalLight primaryLight() {
        if ("moon".equals(this.primaryDirectional)) {
            return this.moon != null ? this.moon : this.sun;
        }
        return this.sun;
    }

    public void setPrimaryDirectional(String which) {
        String w;
        String string = w = which == null ? "" : which.trim().toLowerCase();
        if (!w.equals("sun") && !w.equals("moon")) {
            throw new IllegalArgumentException("[render] setPrimaryDirectional: expected 'sun' or 'moon'");
        }
        this.thread.onJme(() -> {
            this.ensure();
            if (w.equals(this.primaryDirectional)) {
                return;
            }
            this.primaryDirectional = w;
            log.info("RenderApi: primaryDirectional={}", (Object)this.primaryDirectional);
        });
    }

    public float getTimeOfDay() {
        return this.timeOfDay;
    }

    public void setDayLengthSeconds(float seconds) {
        if (!(seconds > 0.0f)) {
            throw new IllegalArgumentException("dayLengthSeconds must be > 0");
        }
        this.dayLengthSeconds = seconds;
    }

    public void updateDayNight(float deltaSeconds) {
        float sunZ;
        if (Float.isNaN(deltaSeconds) || Float.isInfinite(deltaSeconds)) {
            return;
        }
        if (this.dayLengthSeconds <= 0.0f) {
            return;
        }
        float deltaHours = deltaSeconds / this.dayLengthSeconds * 24.0f;
        this.timeOfDay = (this.timeOfDay + deltaHours) % 24.0f;
        if (this.timeOfDay < 0.0f) {
            this.timeOfDay += 24.0f;
        }
        float t = this.timeOfDay / 24.0f;
        float phase = (t - 0.25f) * ((float)Math.PI * 2);
        float sunY = (float)Math.sin(phase);
        float sunIntensity = Math.max(0.0f, sunY);
        float sunX = (float)Math.cos(phase);
        Vector3f sunDir = new Vector3f(sunX, -sunY, sunZ = (float)Math.sin((double)phase + 1.5707963705062866));
        if (sunDir.lengthSquared() < 1.0E-6f) {
            sunDir.set(0.0f, -1.0f, 0.0f);
        }
        sunDir.normalizeLocal();
        ColorRGBA dayColour = new ColorRGBA(1.0f, 0.98f, 0.9f, 1.0f);
        ColorRGBA sunsetColour = new ColorRGBA(1.0f, 0.63f, 0.39f, 1.0f);
        float sunsetWeight = 1.0f - Math.min(1.0f, Math.abs(sunY));
        float sr = dayColour.r * (1.0f - sunsetWeight) + sunsetColour.r * sunsetWeight;
        float sg = dayColour.g * (1.0f - sunsetWeight) + sunsetColour.g * sunsetWeight;
        float sb = dayColour.b * (1.0f - sunsetWeight) + sunsetColour.b * sunsetWeight;
        float finalSunIntensity = sunIntensity * 1.2f;
        float finalSunR = sr;
        float finalSunG = sg;
        float finalSunB = sb;
        Vector3f moonDir = new Vector3f(-sunDir.x, -sunDir.y, -sunDir.z);
        float moonIntensity = Math.max(0.0f, 1.0f - sunIntensity);
        float mr = 0.45f;
        float mg = 0.55f;
        float mb = 0.85f;
        ColorRGBA ambientNight = new ColorRGBA(0.08f, 0.1f, 0.14f, 1.0f);
        ColorRGBA ambientDay = new ColorRGBA(0.25f, 0.28f, 0.35f, 1.0f);
        float ambientWeight = sunIntensity;
        float ambR = ambientDay.r * ambientWeight + ambientNight.r * (1.0f - ambientWeight);
        float ambG = ambientDay.g * ambientWeight + ambientNight.g * (1.0f - ambientWeight);
        float ambB = ambientDay.b * ambientWeight + ambientNight.b * (1.0f - ambientWeight);
        this.thread.onJme(() -> {
            this.ensure();
            this.sun.setDirection(sunDir);
            this.sun.setColor(new ColorRGBA(finalSunR, finalSunG, finalSunB, 1.0f).mult(finalSunIntensity));
            this.moon.setDirection(moonDir);
            this.moon.setColor(new ColorRGBA(mr, mg, mb, 1.0f).mult(moonIntensity));
            this.ambient.setColor(new ColorRGBA(ambR, ambG, ambB, 1.0f));
        });
    }

    public void ambientCfg(LuaValueRef cfg) {
        this.thread.onJme(() -> {
            this.ensureAmbientExists();
            double r = LuaCfg.num((LuaValueRef)cfg, (String)"r", (double)RenderCfg.numPath(cfg, "color", "r", 0.25));
            double g = LuaCfg.num((LuaValueRef)cfg, (String)"g", (double)RenderCfg.numPath(cfg, "color", "g", 0.28));
            double b = LuaCfg.num((LuaValueRef)cfg, (String)"b", (double)RenderCfg.numPath(cfg, "color", "b", 0.35));
            double intensity = LuaCfg.num((LuaValueRef)cfg, (String)"intensity", (double)1.0);
            float fr = (float)r;
            float fg = (float)g;
            float fb = (float)b;
            float fi = (float)Math.max(0.0, intensity);
            if (RenderCfg.approx(fr, this._ambR) && RenderCfg.approx(fg, this._ambG) && RenderCfg.approx(fb, this._ambB) && RenderCfg.approx(fi, this._ambI)) {
                return;
            }
            this._ambR = fr;
            this._ambG = fg;
            this._ambB = fb;
            this._ambI = fi;
            this.ambient.setColor(new ColorRGBA(fr, fg, fb, 1.0f).mult(fi));
        });
    }

    public void sunCfg(LuaValueRef cfg) {
        this.thread.onJme(() -> {
            this.ensureSunExists();
            LuaValueRef dir = LuaCfg.member((LuaValueRef)cfg, (String)"dir");
            LuaValueRef col = LuaCfg.member((LuaValueRef)cfg, (String)"color");
            float dx = RenderCfg.vec3x(dir, -1.0f);
            float dy = RenderCfg.vec3y(dir, -1.0f);
            float dz = RenderCfg.vec3z(dir, -0.3f);
            float r = RenderCfg.vec3x(col, 1.0f);
            float g = RenderCfg.vec3y(col, 0.98f);
            float b = RenderCfg.vec3z(col, 0.9f);
            float intensity = (float)Math.max(0.0, LuaCfg.num((LuaValueRef)cfg, (String)"intensity", (double)1.2));
            if (RenderCfg.approx(dx, this._sunDx) && RenderCfg.approx(dy, this._sunDy) && RenderCfg.approx(dz, this._sunDz) && RenderCfg.approx(r, this._sunR) && RenderCfg.approx(g, this._sunG) && RenderCfg.approx(b, this._sunB) && RenderCfg.approx(intensity, this._sunI)) {
                return;
            }
            this._sunDx = dx;
            this._sunDy = dy;
            this._sunDz = dz;
            this._sunR = r;
            this._sunG = g;
            this._sunB = b;
            this._sunI = intensity;
            Vector3f v = new Vector3f(dx, dy, dz);
            if (v.lengthSquared() < 1.0E-6f) {
                v.set(-1.0f, -1.0f, -1.0f);
            }
            v.normalizeLocal();
            this.sun.setDirection(v);
            this.sun.setColor(new ColorRGBA(r, g, b, 1.0f).mult(intensity));
        });
    }

    public void moonCfg(LuaValueRef cfg) {
        this.thread.onJme(() -> {
            this.ensureMoonExists();
            LuaValueRef dir = LuaCfg.member((LuaValueRef)cfg, (String)"dir");
            LuaValueRef col = LuaCfg.member((LuaValueRef)cfg, (String)"color");
            float dx = RenderCfg.vec3x(dir, 1.0f);
            float dy = RenderCfg.vec3y(dir, -1.0f);
            float dz = RenderCfg.vec3z(dir, 0.3f);
            float r = RenderCfg.vec3x(col, 0.45f);
            float g = RenderCfg.vec3y(col, 0.55f);
            float b = RenderCfg.vec3z(col, 0.85f);
            float intensity = (float)Math.max(0.0, LuaCfg.num((LuaValueRef)cfg, (String)"intensity", (double)0.0));
            if (RenderCfg.approx(dx, this._moonDx) && RenderCfg.approx(dy, this._moonDy) && RenderCfg.approx(dz, this._moonDz) && RenderCfg.approx(r, this._moonR) && RenderCfg.approx(g, this._moonG) && RenderCfg.approx(b, this._moonB) && RenderCfg.approx(intensity, this._moonI)) {
                return;
            }
            this._moonDx = dx;
            this._moonDy = dy;
            this._moonDz = dz;
            this._moonR = r;
            this._moonG = g;
            this._moonB = b;
            this._moonI = intensity;
            Vector3f v = new Vector3f(dx, dy, dz);
            if (v.lengthSquared() < 1.0E-6f) {
                v.set(1.0f, -1.0f, 0.0f);
            }
            v.normalizeLocal();
            this.moon.setDirection(v);
            this.moon.setColor(new ColorRGBA(r, g, b, 1.0f).mult(intensity));
        });
    }

    private void ensureAmbientExists() {
        if (this.ambient != null) {
            return;
        }
        this.ambient = new AmbientLight();
        this.ambient.setColor(new ColorRGBA(0.25f, 0.28f, 0.35f, 1.0f));
        this.app.getRootNode().addLight((Light)this.ambient);
        log.info("RenderApi: ambient created");
    }

    private void ensureSunExists() {
        if (this.sun != null) {
            return;
        }
        this.sun = new DirectionalLight();
        this.sun.setDirection(new Vector3f(-1.0f, -1.0f, -0.3f).normalizeLocal());
        this.sun.setColor(new ColorRGBA(1.0f, 0.98f, 0.9f, 1.0f).mult(1.2f));
        this.app.getRootNode().addLight((Light)this.sun);
        log.info("RenderApi: sun created");
    }

    private void ensureMoonExists() {
        if (this.moon != null) {
            return;
        }
        this.moon = new DirectionalLight();
        this.moon.setDirection(new Vector3f(1.0f, -1.0f, 0.3f).normalizeLocal());
        this.moon.setColor(new ColorRGBA(0.45f, 0.55f, 0.85f, 1.0f).mult(0.0f));
        this.app.getRootNode().addLight((Light)this.moon);
        log.info("RenderApi: moon created");
    }
}

