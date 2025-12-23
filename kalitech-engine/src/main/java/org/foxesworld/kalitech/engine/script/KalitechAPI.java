package org.foxesworld.kalitech.engine.script;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;

public final class KalitechAPI {

    public static final Logger log = LogManager.getLogger(KalitechAPI.class);

    private final SimpleApplication app;
    private final ScriptEventBus eventBus;

    public KalitechAPI(SimpleApplication app, ScriptEventBus eventBus) {
        this.app = app;
        this.eventBus = eventBus;
    }

    // ---------- logging ----------
    @HostAccess.Export
    public void info(String msg) { log.info("[JS] {}", msg); }

    @HostAccess.Export
    public void warn(String msg) { log.warn("[JS] {}", msg); }

    @HostAccess.Export
    public void error(String msg) { log.error("[JS] {}", msg); }

    // ---------- event bus ----------
    /**
     * JS: kalitech.on("eventName", (payload) => {...})
     */
    @HostAccess.Export
    public void on(String eventName, Value callback) {
        if (callback == null || !callback.canExecute()) {
            warn("kalitech.on('" + eventName + "'): callback is not executable");
            return;
        }

        eventBus.on(eventName, payload -> {
            try {
                callback.execute(payload);
            } catch (Exception e) {
                log.error("JS callback error for event '{}'", eventName, e);
            }
        });
    }

    /**
     * JS: kalitech.emit("eventName", payload)
     * payload может быть строкой/числом/объектом JS (в большинстве случаев будет Value)
     */
    @HostAccess.Export
    public void emit(String eventName, Object payload) {
        eventBus.emit(eventName, payload);
    }

    // ---------- scene helpers ----------
    @HostAccess.Export
    public Geometry spawnBox(String name, float sx, float sy, float sz) {
        Box box = new Box(sx, sy, sz);
        Geometry geom = new Geometry(name, box);

        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        geom.setMaterial(mat);

        app.getRootNode().attachChild(geom);
        return geom;
    }

    @HostAccess.Export
    public void setLocalTranslation(String spatialName, float x, float y, float z) {
        Spatial s = app.getRootNode().getChild(spatialName);
        if (s != null) s.setLocalTranslation(x, y, z);
    }

    @HostAccess.Export
    public void move(String spatialName, float dx, float dy, float dz) {
        Spatial s = app.getRootNode().getChild(spatialName);
        if (s != null) {
            Vector3f p = s.getLocalTranslation();
            s.setLocalTranslation(p.x + dx, p.y + dy, p.z + dz);
        }
    }

    @HostAccess.Export
    public void rotateY(String spatialName, float radians) {
        Spatial s = app.getRootNode().getChild(spatialName);
        if (s != null) s.rotate(0f, radians, 0f);
    }

    // ---------- engine access (узко) ----------
    @HostAccess.Export
    public AssetManager assets() {
        return app.getAssetManager();
    }
}