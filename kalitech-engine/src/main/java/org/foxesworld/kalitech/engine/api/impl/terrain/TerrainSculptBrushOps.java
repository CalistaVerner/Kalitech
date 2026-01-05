package org.foxesworld.kalitech.engine.api.impl.terrain;

import com.jme3.math.Vector3f;
import com.jme3.terrain.geomipmap.TerrainQuad;
import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.api.impl.terrain.TerrainValues.*;
import static org.foxesworld.kalitech.engine.util.ValueCfg.i32;

/**
 * Sculpt brushes for terrain heightmaps (raise/lower/smooth/flatten + falloff).
 *
 * This is designed for tooling and runtime interaction.
 * CDPR-style goals:
 *  - deterministic, single-pass operations
 *  - no allocations per sample (except optional heightmap copy)
 *  - explicit brush shape & falloff
 */
public final class TerrainSculptBrushOps {

    public enum Mode { RAISE, LOWER, FLATTEN, SMOOTH }
    public enum Falloff { LINEAR, SMOOTHSTEP, GAUSS }

    public TerrainSculptBrushOps() {}

    /**
     * Apply brush to a TerrainQuad in-place.
     * cfg:
     *  - mode: "raise"|"lower"|"flatten"|"smooth" (default "raise")
     *  - x: world x (required)
     *  - z: world z (required)
     *  - radius: world radius (required)
     *  - strength: height delta per application for raise/lower (default 1)
     *  - target: target height for flatten (default current height)
     *  - falloff: "linear"|"smoothstep"|"gauss" (default smoothstep)
     *  - hardness: 0..1 (default 0.0)  // 0 soft, 1 hard edge
     *  - smoothPasses: int (default 1)
     *  - rebuild: boolean (default true)
     */
    public void apply(TerrainQuad tq, Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("brush cfg is null");

        double wx = num(cfg, "x", Double.NaN);
        double wz = num(cfg, "z", Double.NaN);
        if (!Double.isFinite(wx) || !Double.isFinite(wz)) throw new IllegalArgumentException("brush x/z required");

        double radiusW = Math.max(0.0001, num(cfg, "radius", 0.0));
        if (!(radiusW > 0.0)) throw new IllegalArgumentException("brush.radius must be > 0");

        Mode mode = parseMode(str(cfg, "mode", "raise"));
        Falloff falloff = parseFalloff(str(cfg, "falloff", "smoothstep"));
        double hardness = clamp(num(cfg, "hardness", 0.0), 0.0, 1.0);

        double strength = num(cfg, "strength", 1.0);
        int smoothPasses = Math.max(1, i32(cfg, "smoothPasses", 1));
        boolean rebuild = bool(cfg, "rebuild", true);

        // Convert world point to local terrain space.
        Vector3f local = tq.worldToLocal(new Vector3f((float) wx, 0f, (float) wz), null);

        float[] hm = tq.getHeightMap();
        if (hm == null || hm.length == 0) return;

        int size = tq.getTerrainSize();
        if (size <= 1) return;

        // Terrain heightmap is in local space; X/Z indices correspond to local coordinates scaled by localScale.
        float sx = tq.getLocalScale().x;
        float sz = tq.getLocalScale().z;
        if (sx <= 1e-6f || sz <= 1e-6f) return;

        // Convert local x/z to heightmap index space.
        double cx = local.x / sx;
        double cz = local.z / sz;
        double radius = radiusW / sx; // assume uniform scale x=z

        int minX = (int) Math.floor(cx - radius);
        int maxX = (int) Math.ceil(cx + radius);
        int minZ = (int) Math.floor(cz - radius);
        int maxZ = (int) Math.ceil(cz + radius);

        if (maxX < 0 || maxZ < 0 || minX > size - 1 || minZ > size - 1) return;

        minX = Math.max(0, minX);
        minZ = Math.max(0, minZ);
        maxX = Math.min(size - 1, maxX);
        maxZ = Math.min(size - 1, maxZ);

        double r2 = radius * radius;

        switch (mode) {
            case RAISE -> applyRaiseLower(hm, size, cx, cz, radius, r2, +Math.abs(strength), falloff, hardness);
            case LOWER -> applyRaiseLower(hm, size, cx, cz, radius, r2, -Math.abs(strength), falloff, hardness);
            case FLATTEN -> {
                // target in world Y, but heightmap stores local height; convert.
                double targetWorld = num(cfg, "target", Double.NaN);
                double targetLocal;
                if (Double.isFinite(targetWorld)) {
                    targetLocal = (targetWorld - tq.getWorldTranslation().y) / tq.getLocalScale().y;
                } else {
                    // use current sample height
                    int ix = clampI((int) Math.round(cx), 0, size - 1);
                    int iz = clampI((int) Math.round(cz), 0, size - 1);
                    targetLocal = hm[iz * size + ix];
                }
                applyFlatten(hm, size, cx, cz, radius, r2, targetLocal, falloff, hardness);
            }
            case SMOOTH -> {
                for (int p = 0; p < smoothPasses; p++) {
                    applySmooth(hm, size, cx, cz, radius, r2, Math.abs(strength), falloff, hardness);
                }
            }
        }

        //tq.setHeightmap(hm);
        if (rebuild) {
            tq.updateModelBound();
            tq.updateGeometricState();
        }
    }

    // ---------------------------------------------------------------------
    // Brush kernels
    // ---------------------------------------------------------------------

    private static void applyRaiseLower(float[] hm, int size, double cx, double cz, double radius, double r2, double delta,
                                       Falloff falloff, double hardness) {
        int minX = clampI((int) Math.floor(cx - radius), 0, size - 1);
        int maxX = clampI((int) Math.ceil(cx + radius), 0, size - 1);
        int minZ = clampI((int) Math.floor(cz - radius), 0, size - 1);
        int maxZ = clampI((int) Math.ceil(cz + radius), 0, size - 1);

        for (int z = minZ; z <= maxZ; z++) {
            double dz = z - cz;
            for (int x = minX; x <= maxX; x++) {
                double dx = x - cx;
                double d2 = dx * dx + dz * dz;
                if (d2 > r2) continue;
                double w = weight(Math.sqrt(d2) / radius, falloff, hardness);
                int idx = z * size + x;
                hm[idx] = (float) (hm[idx] + delta * w);
            }
        }
    }

    private static void applyFlatten(float[] hm, int size, double cx, double cz, double radius, double r2, double targetLocal,
                                    Falloff falloff, double hardness) {
        int minX = clampI((int) Math.floor(cx - radius), 0, size - 1);
        int maxX = clampI((int) Math.ceil(cx + radius), 0, size - 1);
        int minZ = clampI((int) Math.floor(cz - radius), 0, size - 1);
        int maxZ = clampI((int) Math.ceil(cz + radius), 0, size - 1);

        for (int z = minZ; z <= maxZ; z++) {
            double dz = z - cz;
            for (int x = minX; x <= maxX; x++) {
                double dx = x - cx;
                double d2 = dx * dx + dz * dz;
                if (d2 > r2) continue;
                double w = weight(Math.sqrt(d2) / radius, falloff, hardness);
                int idx = z * size + x;
                double cur = hm[idx];
                hm[idx] = (float) (cur + (targetLocal - cur) * w);
            }
        }
    }

    private static void applySmooth(float[] hm, int size, double cx, double cz, double radius, double r2, double strength,
                                   Falloff falloff, double hardness) {
        // Simple weighted neighbor average within 1 cell; strength mixes current->avg.
        int minX = clampI((int) Math.floor(cx - radius), 0, size - 1);
        int maxX = clampI((int) Math.ceil(cx + radius), 0, size - 1);
        int minZ = clampI((int) Math.floor(cz - radius), 0, size - 1);
        int maxZ = clampI((int) Math.ceil(cz + radius), 0, size - 1);

        // Copy the affected window only (cheap and avoids full copy).
        int w = (maxX - minX + 1);
        int h = (maxZ - minZ + 1);
        float[] window = new float[w * h];
        for (int z = 0; z < h; z++) {
            int src = (minZ + z) * size + minX;
            System.arraycopy(hm, src, window, z * w, w);
        }

        double mix = clamp(strength, 0.0, 1.0);

        for (int z = minZ; z <= maxZ; z++) {
            double dz = z - cz;
            for (int x = minX; x <= maxX; x++) {
                double dx = x - cx;
                double d2 = dx * dx + dz * dz;
                if (d2 > r2) continue;

                double wFall = weight(Math.sqrt(d2) / radius, falloff, hardness);
                if (wFall <= 1e-6) continue;

                float avg = avg3x3(window, w, h, x - minX, z - minZ);
                int idx = z * size + x;
                double cur = hm[idx];
                double sm = cur + (avg - cur) * mix;
                hm[idx] = (float) (cur + (sm - cur) * wFall);
            }
        }
    }

    private static float avg3x3(float[] win, int w, int h, int x, int z) {
        double sum = 0.0;
        int cnt = 0;
        for (int dz = -1; dz <= 1; dz++) {
            int zz = z + dz;
            if (zz < 0 || zz >= h) continue;
            int row = zz * w;
            for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx;
                if (xx < 0 || xx >= w) continue;
                sum += win[row + xx];
                cnt++;
            }
        }
        return (cnt > 0) ? (float) (sum / cnt) : win[z * w + x];
    }

    private static double weight(double t01, Falloff falloff, double hardness) {
        // t01: 0 at center, 1 at radius
        double t = clamp(t01, 0.0, 1.0);

        // hardness makes inner region flatter (hard edge).
        if (hardness > 1e-6) {
            // remap so that hardness=1 => almost step at radius.
            double inner = 1.0 - hardness;
            if (t <= inner) t = 0.0;
            else t = (t - inner) / (1.0 - inner);
        }

        double w;
        switch (falloff) {
            case LINEAR -> w = 1.0 - t;
            case GAUSS -> {
                // exp(-k*t^2) with k tuned so at t=1 -> ~0.02
                w = Math.exp(-4.0 * t * t);
            }
            default -> {
                // smoothstep
                double s = t * t * (3.0 - 2.0 * t);
                w = 1.0 - s;
            }
        }
        return clamp(w, 0.0, 1.0);
    }

    private static int clampI(int v, int a, int b) {
        return (v < a) ? a : (v > b) ? b : v;
    }

    private static Mode parseMode(String s) {
        if (s == null) return Mode.RAISE;
        return switch (s.trim().toLowerCase()) {
            case "lower" -> Mode.LOWER;
            case "flatten" -> Mode.FLATTEN;
            case "smooth" -> Mode.SMOOTH;
            default -> Mode.RAISE;
        };
    }

    private static Falloff parseFalloff(String s) {
        if (s == null) return Falloff.SMOOTHSTEP;
        return switch (s.trim().toLowerCase()) {
            case "linear" -> Falloff.LINEAR;
            case "gauss", "gaussian" -> Falloff.GAUSS;
            default -> Falloff.SMOOTHSTEP;
        };
    }
}
