package org.foxesworld.kalitech.engine.api.impl.terrain;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.terrain.geomipmap.TerrainLodControl;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.graalvm.polyglot.Value;

import java.util.Map;

import static org.foxesworld.kalitech.engine.api.impl.terrain.TerrainValues.bool;
import static org.foxesworld.kalitech.engine.api.impl.terrain.TerrainValues.clamp;
import static org.foxesworld.kalitech.engine.api.impl.terrain.TerrainValues.num;

public final class TerrainOps {

    private final Camera camera;

    public TerrainOps(Camera camera) {
        this.camera = camera;
    }

    public void lod(TerrainQuad tq, Value cfg) {
        boolean enable = (cfg == null || cfg.isNull()) ? true : bool(cfg, "enabled", true);
        if (!enable) {
            TerrainLodControl c = tq.getControl(TerrainLodControl.class);
            if (c != null) tq.removeControl(c);
            return;
        }

        TerrainLodControl existing = tq.getControl(TerrainLodControl.class);
        if (existing != null) {
            existing.setCamera(camera);
            return;
        }
        tq.addControl(new TerrainLodControl(tq, camera));
    }

    public void scale(TerrainQuad tq, double xzScale, Value cfg) {
        float xz = (float) clamp(xzScale, 0.0001, 1_000_000);
        float y = tq.getLocalScale().y;
        if (cfg != null && !cfg.isNull() && cfg.hasMember("yScale")) {
            y = (float) clamp(num(cfg, "yScale", y), 0.0001, 1_000_000);
        }
        tq.setLocalScale(xz, y, xz);
    }

    public double heightAt(TerrainQuad tq, double x, double z, boolean world) {
        float lx, lz;
        if (world) {
            Vector3f local = tq.worldToLocal(new Vector3f((float) x, 0f, (float) z), null);
            lx = local.x; lz = local.z;
        } else {
            lx = (float) x; lz = (float) z;
        }

        Float h = tq.getHeight(new Vector2f(lx, lz));
        if (h == null) return Double.NaN;

        if (!world) return h;

        Vector3f wp = tq.localToWorld(new Vector3f(0f, h, 0f), null);
        return wp.y;
    }

    public Map<String, Double> normalAt(TerrainQuad tq, double x, double z, boolean world) {
        float lx, lz;
        if (world) {
            Vector3f local = tq.worldToLocal(new Vector3f((float) x, 0f, (float) z), null);
            lx = local.x; lz = local.z;
        } else {
            lx = (float) x; lz = (float) z;
        }

        Vector3f n = tq.getNormal(new Vector2f(lx, lz));
        if (n == null) return Map.of("x", Double.NaN, "y", Double.NaN, "z", Double.NaN);

        if (world) n = tq.getWorldRotation().mult(n);
        return Map.of("x", (double) n.x, "y", (double) n.y, "z", (double) n.z);
    }
}