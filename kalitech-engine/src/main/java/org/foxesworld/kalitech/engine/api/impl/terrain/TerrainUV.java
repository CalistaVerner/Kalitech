package org.foxesworld.kalitech.engine.api.impl.terrain;

import com.jme3.material.Material;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.api.impl.terrain.TerrainValues.member;

public final class TerrainUV {

    public void apply(Spatial s, Value cfgOrUv) {
        if (s == null) throw new IllegalArgumentException("terrain.uv: spatial is null");
        if (cfgOrUv == null || cfgOrUv.isNull()) return;

        // cfg может быть {uv:{...}} или напрямую {scale:...}
        Value uv = cfgOrUv;
        Value nested = member(cfgOrUv, "uv");
        if (nested != null && !nested.isNull()) uv = nested;

        Vector2f scale = readUvScale(uv, 1f, 1f);
        if (scale == null) return;

        if (s instanceof Geometry g) {
            if (g.getMesh() != null) g.getMesh().scaleTextureCoordinates(scale);
            return;
        }

        if (s instanceof TerrainQuad tq) {
            Material m = tq.getMaterial();
            if (m != null) {
                trySetFloat(m, "TexScale", scale.x);
                trySetVector2(m, "UvScale", scale);
                trySetVector2(m, "uvScale", scale);

                trySetFloat(m, "Tex1Scale", scale.x);
                trySetFloat(m, "Tex2Scale", scale.x);
                trySetFloat(m, "Tex3Scale", scale.x);
                trySetFloat(m, "Tex4Scale", scale.x);
            }
            tq.setUserData("uvScale", scale);
            return;
        }

        s.setUserData("uvScale", scale);
    }

    private static Vector2f readUvScale(Value uv, float defX, float defY) {
        try {
            if (uv == null || uv.isNull()) return null;

            // uv: [sx,sy]
            if (uv.hasArrayElements()) {
                float sx = (float) (uv.getArraySize() > 0 ? uv.getArrayElement(0).asDouble() : defX);
                float sy = (float) (uv.getArraySize() > 1 ? uv.getArrayElement(1).asDouble() : defY);
                return new Vector2f(sx, sy);
            }

            // uv: {scale:[sx,sy]} or {scale:s}
            Value sc = member(uv, "scale");
            if (sc != null && !sc.isNull()) {
                if (sc.hasArrayElements()) {
                    float sx = (float) (sc.getArraySize() > 0 ? sc.getArrayElement(0).asDouble() : defX);
                    float sy = (float) (sc.getArraySize() > 1 ? sc.getArrayElement(1).asDouble() : defY);
                    return new Vector2f(sx, sy);
                }
                if (sc.isNumber()) {
                    float s = (float) sc.asDouble();
                    return new Vector2f(s, s);
                }
            }

            // uv: {sx,sy}
            if (uv.hasMember("sx") || uv.hasMember("sy")) {
                float sx = (float) (uv.hasMember("sx") ? uv.getMember("sx").asDouble() : defX);
                float sy = (float) (uv.hasMember("sy") ? uv.getMember("sy").asDouble() : defY);
                return new Vector2f(sx, sy);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void trySetFloat(Material m, String name, float v) {
        try { m.setFloat(name, v); } catch (Throwable ignored) {}
    }

    private static void trySetVector2(Material m, String name, Vector2f v) {
        try { m.setVector2(name, v); } catch (Throwable ignored) {}
    }
}