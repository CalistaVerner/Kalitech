// FILE: org/foxesworld/kalitech/engine/modules/render/LightRigModule.java
package org.foxesworld.kalitech.engine.modules.render;

import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.apache.logging.log4j.Logger;

public final class LightRigModule {

    private final Node root;
    private final Logger log;

    private AmbientLight ambient;
    private DirectionalLight sun;
    private DirectionalLight moon;

    private String primaryDirectional = "sun"; // "sun" | "moon"

    public LightRigModule(Node root, Logger log) {
        this.root = root;
        this.log = log;
    }

    public void ensure() {
        ensureAmbient();
        ensureSun();
        ensureMoon();
    }

    public void ensureAmbient() {
        if (ambient != null) return;
        ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(0.25f, 0.28f, 0.35f, 1f));
        root.addLight(ambient);
        log.info("RenderApi: ambient created");
    }

    public void ensureSun() {
        if (sun != null) return;
        sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-1, -1, -0.3f).normalizeLocal());
        sun.setColor(new ColorRGBA(1f, 0.98f, 0.90f, 1f).mult(1.2f));
        root.addLight(sun);
        log.info("RenderApi: sun created");
    }

    public void ensureMoon() {
        if (moon != null) return;
        moon = new DirectionalLight();
        moon.setDirection(new Vector3f(1, -1, 0.3f).normalizeLocal());
        moon.setColor(new ColorRGBA(0.45f, 0.55f, 0.85f, 1f).mult(0.0f));
        root.addLight(moon);
        log.info("RenderApi: moon created");
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

    public void setPrimaryDirectional(String which) {
        if (!"sun".equals(which) && !"moon".equals(which)) {
            throw new IllegalArgumentException("[render] primary directional must be 'sun' or 'moon'");
        }
        primaryDirectional = which;
    }

    public String primaryDirectional() {
        return primaryDirectional;
    }

    public DirectionalLight primaryLight() {
        if ("moon".equals(primaryDirectional)) return moon != null ? moon : sun;
        return sun;
    }
}