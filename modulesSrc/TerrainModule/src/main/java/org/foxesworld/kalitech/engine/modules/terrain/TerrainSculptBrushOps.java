/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.math.Vector3f
 *  com.jme3.terrain.geomipmap.TerrainQuad
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.terrain;

import com.jme3.math.Vector3f;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class TerrainSculptBrushOps {
    private static void applyRaiseLower(float[] hm, int size, double cx, double cz, double radius, double r2, double delta, Falloff falloff, double hardness) {
        int minX = TerrainSculptBrushOps.clampI((int)Math.floor(cx - radius), 0, size - 1);
        int maxX = TerrainSculptBrushOps.clampI((int)Math.ceil(cx + radius), 0, size - 1);
        int minZ = TerrainSculptBrushOps.clampI((int)Math.floor(cz - radius), 0, size - 1);
        int maxZ = TerrainSculptBrushOps.clampI((int)Math.ceil(cz + radius), 0, size - 1);
        for (int z = minZ; z <= maxZ; ++z) {
            double dz = (double)z - cz;
            for (int x = minX; x <= maxX; ++x) {
                double dx = (double)x - cx;
                double d2 = dx * dx + dz * dz;
                if (d2 > r2) continue;
                double w = TerrainSculptBrushOps.weight(Math.sqrt(d2) / radius, falloff, hardness);
                int idx = z * size + x;
                hm[idx] = (float)((double)hm[idx] + delta * w);
            }
        }
    }

    private static void applyFlatten(float[] hm, int size, double cx, double cz, double radius, double r2, double targetLocal, Falloff falloff, double hardness) {
        int minX = TerrainSculptBrushOps.clampI((int)Math.floor(cx - radius), 0, size - 1);
        int maxX = TerrainSculptBrushOps.clampI((int)Math.ceil(cx + radius), 0, size - 1);
        int minZ = TerrainSculptBrushOps.clampI((int)Math.floor(cz - radius), 0, size - 1);
        int maxZ = TerrainSculptBrushOps.clampI((int)Math.ceil(cz + radius), 0, size - 1);
        for (int z = minZ; z <= maxZ; ++z) {
            double dz = (double)z - cz;
            for (int x = minX; x <= maxX; ++x) {
                double dx = (double)x - cx;
                double d2 = dx * dx + dz * dz;
                if (d2 > r2) continue;
                double w = TerrainSculptBrushOps.weight(Math.sqrt(d2) / radius, falloff, hardness);
                int idx = z * size + x;
                double cur = hm[idx];
                hm[idx] = (float)(cur + (targetLocal - cur) * w);
            }
        }
    }

    private static void applySmooth(float[] hm, int size, double cx, double cz, double radius, double r2, double strength, Falloff falloff, double hardness) {
        int minX = TerrainSculptBrushOps.clampI((int)Math.floor(cx - radius), 0, size - 1);
        int maxX = TerrainSculptBrushOps.clampI((int)Math.ceil(cx + radius), 0, size - 1);
        int minZ = TerrainSculptBrushOps.clampI((int)Math.floor(cz - radius), 0, size - 1);
        int maxZ = TerrainSculptBrushOps.clampI((int)Math.ceil(cz + radius), 0, size - 1);
        int w = maxX - minX + 1;
        int h = maxZ - minZ + 1;
        float[] window = new float[w * h];
        for (int z = 0; z < h; ++z) {
            int src = (minZ + z) * size + minX;
            System.arraycopy(hm, src, window, z * w, w);
        }
        double mix = LuaCfg.clamp((double)strength, (double)0.0, (double)1.0);
        for (int z = minZ; z <= maxZ; ++z) {
            double dz = (double)z - cz;
            for (int x = minX; x <= maxX; ++x) {
                double wFall;
                double dx = (double)x - cx;
                double d2 = dx * dx + dz * dz;
                if (d2 > r2 || (wFall = TerrainSculptBrushOps.weight(Math.sqrt(d2) / radius, falloff, hardness)) <= 1.0E-6) continue;
                float avg = TerrainSculptBrushOps.avg3x3(window, w, h, x - minX, z - minZ);
                int idx = z * size + x;
                double cur = hm[idx];
                double sm = cur + ((double)avg - cur) * mix;
                hm[idx] = (float)(cur + (sm - cur) * wFall);
            }
        }
    }

    private static float avg3x3(float[] win, int w, int h, int x, int z) {
        double sum = 0.0;
        int cnt = 0;
        for (int dz = -1; dz <= 1; ++dz) {
            int zz = z + dz;
            if (zz < 0 || zz >= h) continue;
            int row = zz * w;
            for (int dx = -1; dx <= 1; ++dx) {
                int xx = x + dx;
                if (xx < 0 || xx >= w) continue;
                sum += (double)win[row + xx];
                ++cnt;
            }
        }
        return cnt > 0 ? (float)(sum / (double)cnt) : win[z * w + x];
    }

    private static double weight(double t01, Falloff falloff, double hardness) {
        double t = LuaCfg.clamp((double)t01, (double)0.0, (double)1.0);
        if (hardness > 1.0E-6) {
            double inner = 1.0 - hardness;
            t = t <= inner ? 0.0 : (t - inner) / (1.0 - inner);
        }
        return LuaCfg.clamp((double)(switch (falloff) {
            case LINEAR -> 1.0 - t;
            case GAUSS -> Math.exp(-4.0 * t * t);
            default -> {
                double s = t * t * (3.0 - 2.0 * t);
                yield 1.0 - s;
            }
        }), (double)0.0, (double)1.0);
    }

    private static int clampI(int v, int a, int b) {
        return v < a ? a : (v > b ? b : v);
    }

    private static Mode parseMode(String s) {
        if (s == null) {
            return Mode.RAISE;
        }
        return switch (s.trim().toLowerCase()) {
            case "lower" -> Mode.LOWER;
            case "flatten" -> Mode.FLATTEN;
            case "smooth" -> Mode.SMOOTH;
            default -> Mode.RAISE;
        };
    }

    private static Falloff parseFalloff(String s) {
        if (s == null) {
            return Falloff.SMOOTHSTEP;
        }
        return switch (s.trim().toLowerCase()) {
            case "linear" -> Falloff.LINEAR;
            case "gauss", "gaussian" -> Falloff.GAUSS;
            default -> Falloff.SMOOTHSTEP;
        };
    }

    public void apply(TerrainQuad tq, LuaValueRef cfg) {
        if (cfg == null || cfg.isNull()) {
            throw new IllegalArgumentException("brush cfg is null");
        }
        double wx = LuaCfg.num((LuaValueRef)cfg, (String)"x", (double)Double.NaN);
        double wz = LuaCfg.num((LuaValueRef)cfg, (String)"z", (double)Double.NaN);
        if (!Double.isFinite(wx) || !Double.isFinite(wz)) {
            throw new IllegalArgumentException("brush x/z required");
        }
        double radiusW = Math.max(1.0E-4, LuaCfg.num((LuaValueRef)cfg, (String)"radius", (double)0.0));
        if (!(radiusW > 0.0)) {
            throw new IllegalArgumentException("brush.radius must be > 0");
        }
        Mode mode = TerrainSculptBrushOps.parseMode(LuaCfg.str((LuaValueRef)cfg, (String)"mode", (String)"raise"));
        Falloff falloff = TerrainSculptBrushOps.parseFalloff(LuaCfg.str((LuaValueRef)cfg, (String)"falloff", (String)"smoothstep"));
        double hardness = LuaCfg.clamp((double)LuaCfg.num((LuaValueRef)cfg, (String)"hardness", (double)0.0), (double)0.0, (double)1.0);
        double strength = LuaCfg.num((LuaValueRef)cfg, (String)"strength", (double)1.0);
        int smoothPasses = Math.max(1, LuaCfg.i32((LuaValueRef)cfg, (String)"smoothPasses", (int)1));
        boolean rebuild = LuaCfg.bool((LuaValueRef)cfg, (String)"rebuild", (boolean)true);
        Vector3f local = tq.worldToLocal(new Vector3f((float)wx, 0.0f, (float)wz), null);
        float[] hm = tq.getHeightMap();
        if (hm == null || hm.length == 0) {
            return;
        }
        int size = tq.getTerrainSize();
        if (size <= 1) {
            return;
        }
        float sx = tq.getLocalScale().x;
        float sz = tq.getLocalScale().z;
        if (sx <= 1.0E-6f || sz <= 1.0E-6f) {
            return;
        }
        double cx = local.x / sx;
        double cz = local.z / sz;
        double radius = radiusW / (double)sx;
        int minX = (int)Math.floor(cx - radius);
        int maxX = (int)Math.ceil(cx + radius);
        int minZ = (int)Math.floor(cz - radius);
        int maxZ = (int)Math.ceil(cz + radius);
        if (maxX < 0 || maxZ < 0 || minX > size - 1 || minZ > size - 1) {
            return;
        }
        minX = Math.max(0, minX);
        minZ = Math.max(0, minZ);
        maxX = Math.min(size - 1, maxX);
        maxZ = Math.min(size - 1, maxZ);
        double r2 = radius * radius;
        switch (mode) {
            case RAISE: {
                TerrainSculptBrushOps.applyRaiseLower(hm, size, cx, cz, radius, r2, Math.abs(strength), falloff, hardness);
                break;
            }
            case LOWER: {
                TerrainSculptBrushOps.applyRaiseLower(hm, size, cx, cz, radius, r2, -Math.abs(strength), falloff, hardness);
                break;
            }
            case FLATTEN: {
                double targetLocal;
                double targetWorld = LuaCfg.num((LuaValueRef)cfg, (String)"target", (double)Double.NaN);
                if (Double.isFinite(targetWorld)) {
                    targetLocal = (targetWorld - (double)tq.getWorldTranslation().y) / (double)tq.getLocalScale().y;
                } else {
                    int ix = TerrainSculptBrushOps.clampI((int)Math.round(cx), 0, size - 1);
                    int iz = TerrainSculptBrushOps.clampI((int)Math.round(cz), 0, size - 1);
                    targetLocal = hm[iz * size + ix];
                }
                TerrainSculptBrushOps.applyFlatten(hm, size, cx, cz, radius, r2, targetLocal, falloff, hardness);
                break;
            }
            case SMOOTH: {
                for (int p = 0; p < smoothPasses; ++p) {
                    TerrainSculptBrushOps.applySmooth(hm, size, cx, cz, radius, r2, Math.abs(strength), falloff, hardness);
                }
                break;
            }
        }
        if (rebuild) {
            tq.updateModelBound();
            tq.updateGeometricState();
        }
    }

    public static enum Falloff {
        LINEAR,
        SMOOTHSTEP,
        GAUSS;

    }

    public static enum Mode {
        RAISE,
        LOWER,
        FLATTEN,
        SMOOTH;

    }
}

