// FILE: Scripts/player/CameraCollisionSolver.js
"use strict";

const U = require("./camUtil.js");

function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

function invSqrt(x) {
    return (x > 0) ? (1.0 / Math.sqrt(x)) : 0;
}

function hasEngine() {
    return (typeof ENGINE !== "undefined") && !!ENGINE;
}

function getDebug() {
    if (!hasEngine()) return null;

    const d = ENGINE.debug;
    if (!d) return null;

    const dbg = (typeof d === "function") ? d() : d;
    if (!dbg) return null;

    try {
        if (typeof dbg.enabled === "function") dbg.enabled(true);
        else if (typeof dbg.enabledDraw === "function") dbg.enabledDraw(true);
    } catch (_) {
    }

    try {
        if (typeof dbg.scope === "function") return dbg.scope("camera").scope("collision");
        if (typeof dbg.child === "function") return dbg.child("camera").child("collision");
    } catch (_) {
    }

    return dbg;
}

function requireTerrainApi() {
    if (!hasEngine()) throw new Error("[camera][collision] global ENGINE is required for terrain sampling");
    const terr = ENGINE.terrain;
    if (!terr) throw new Error("[camera][collision] ENGINE.terrain is required (global Terrain API)");
    if (typeof terr.heightAt !== "function") throw new Error("[camera][collision] ENGINE.terrain.heightAt(surface,x,z,world) is required");
    if (typeof terr.normalAt !== "function") throw new Error("[camera][collision] ENGINE.terrain.normalAt(surface,x,z,world) is required");
    return terr;
}

function requireTerrainHandle() {
    if (typeof TERRAIN === "undefined" || !TERRAIN) {
        throw new Error(
            "[camera][collision] terrain mode enabled, but global TERRAIN is not set. " +
            "Create terrain first and assign it to global TERRAIN."
        );
    }
    const t = TERRAIN;
    return (t && typeof t === "object" && t.surface) ? t.surface : t;
}

function hasSweepCapsule(phys) {
    return phys && typeof phys.sweepCapsule === "function";
}

function normalizeDir(dx, dy, dz) {
    const l2 = dx * dx + dy * dy + dz * dz;
    if (l2 < 1e-12) return {x: 0, y: 1, z: 0, len: 0, inv: 0};
    const len = Math.sqrt(l2);
    const inv = 1.0 / len;
    return {x: dx * inv, y: dy * inv, z: dz * inv, len, inv};
}

function orthonormalBasisFromDir(dx, dy, dz) {
    let ax = 0, ay = 1, az = 0;
    if (Math.abs(dy) > 0.95) {
        ax = 1;
        ay = 0;
        az = 0;
    }

    let rx = dy * az - dz * ay;
    let ry = dz * ax - dx * az;
    let rz = dx * ay - dy * ax;
    const rl2 = rx * rx + ry * ry + rz * rz;
    const invR = invSqrt(rl2);
    rx *= invR;
    ry *= invR;
    rz *= invR;

    let ux = ry * dz - rz * dy;
    let uy = rz * dx - rx * dz;
    let uz = rx * dy - ry * dx;
    const ul2 = ux * ux + uy * uy + uz * uz;
    const invU = invSqrt(ul2);
    ux *= invU;
    uy *= invU;
    uz *= invU;

    return {rx, ry, rz, ux, uy, uz};
}

function dbgCapsule(dbg, a, b, radius, ttl, depth, alpha) {
    if (!dbg) return;
    if (!Number.isFinite(radius) || radius <= 0) return;

    const dx = b[0] - a[0];
    const dy = b[1] - a[1];
    const dz = b[2] - a[2];
    const nd = normalizeDir(dx, dy, dz);

    if (nd.len <= 1e-5) {
        if (typeof dbg.sphere === "function") dbg.sphere(a, radius, [0.2, 0.9, 1, alpha], ttl, depth, alpha, 16);
        return;
    }

    if (typeof dbg.circle !== "function" || typeof dbg.line !== "function") return;

    const B = orthonormalBasisFromDir(nd.x, nd.y, nd.z);
    const col = [0.2, 0.9, 1, alpha];

    dbg.circle(a, [nd.x, nd.y, nd.z], radius, col, ttl, depth, alpha, 24);
    dbg.circle(a, [B.rx, B.ry, B.rz], radius, col, ttl, depth, alpha, 24);
    dbg.circle(a, [B.ux, B.uy, B.uz], radius, col, ttl, depth, alpha, 24);

    dbg.circle(b, [nd.x, nd.y, nd.z], radius, col, ttl, depth, alpha, 24);
    dbg.circle(b, [B.rx, B.ry, B.rz], radius, col, ttl, depth, alpha, 24);
    dbg.circle(b, [B.ux, B.uy, B.uz], radius, col, ttl, depth, alpha, 24);

    const ex1 = B.rx * radius, ey1 = B.ry * radius, ez1 = B.rz * radius;
    const ex2 = B.ux * radius, ey2 = B.uy * radius, ez2 = B.uz * radius;

    dbg.line([a[0] + ex1, a[1] + ey1, a[2] + ez1], [b[0] + ex1, b[1] + ey1, b[2] + ez1], col, ttl, depth, alpha);
    dbg.line([a[0] - ex1, a[1] - ey1, a[2] - ez1], [b[0] - ex1, b[1] - ey1, b[2] - ez1], col, ttl, depth, alpha);

    dbg.line([a[0] + ex2, a[1] + ey2, a[2] + ez2], [b[0] + ex2, b[1] + ey2, b[2] + ez2], col, ttl, depth, alpha);
    dbg.line([a[0] - ex2, a[1] - ey2, a[2] - ez2], [b[0] - ex2, b[1] - ey2, b[2] - ez2], col, ttl, depth, alpha);
}

function bundleHit(phys, from, dirN, len, radius) {
    const B = orthonormalBasisFromDir(dirN.x, dirN.y, dirN.z);

    const ox1 = B.rx * radius, oy1 = B.ry * radius, oz1 = B.rz * radius;
    const ox2 = B.ux * radius, oy2 = B.uy * radius, oz2 = B.uz * radius;

    const origins = [
        [from.x, from.y, from.z],
        [from.x + ox1, from.y + oy1, from.z + oz1],
        [from.x - ox1, from.y - oy1, from.z - oz1],
        [from.x + ox2, from.y + oy2, from.z + oz2],
        [from.x - ox2, from.y - oy2, from.z - oz2],
    ];

    let best = null;
    let bestD = Infinity;

    for (let i = 0; i < origins.length; i++) {
        const o = origins[i];
        const h = phys.raycast(o[0], o[1], o[2], dirN.x, dirN.y, dirN.z, len);
        if (h && h.hit) {
            const hx = +h.x, hy = +h.y, hz = +h.z;
            if (!Number.isFinite(hx) || !Number.isFinite(hy) || !Number.isFinite(hz)) continue;

            const dx = hx - o[0], dy = hy - o[1], dz = hz - o[2];
            const d = dx * dirN.x + dy * dirN.y + dz * dirN.z;
            if (d >= 0 && d < bestD) {
                bestD = d;
                best = h;
            }
        }
    }

    return best;
}

function resolveCameraObstacle(phys, dbg, from, to, radius, pad, ttl, depth, axisLen, halfHeight) {
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const dz = to.z - from.z;

    const nd = normalizeDir(dx, dy, dz);
    if (nd.len <= 1e-6) return false;

    const skin = Math.max(0.0, radius + pad);

    let hit = null;

    if (hasSweepCapsule(phys)) {
        const hh = Number.isFinite(halfHeight) ? halfHeight : 0.0;
        try {
            hit = phys.sweepCapsule(from.x, from.y, from.z, to.x, to.y, to.z, radius, hh);
        } catch (_) {
            hit = null;
        }
    }

    if (!hit) hit = bundleHit(phys, from, nd, nd.len + skin, radius);
    if (!(hit && hit.hit)) return false;

    const n = hit.normal;
    if (!n) throw new Error("[camera][collision] hit must provide normal:{x,y,z}");

    let nx = +n.x, ny = +n.y, nz = +n.z;
    if (!Number.isFinite(nx) || !Number.isFinite(ny) || !Number.isFinite(nz)) {
        throw new Error("[camera][collision] hit normal must be finite");
    }

    const nlen2 = nx * nx + ny * ny + nz * nz;
    const invN = invSqrt(nlen2);
    if (invN > 0) {
        nx *= invN;
        ny *= invN;
        nz *= invN;
    } else {
        nx = 0;
        ny = 1;
        nz = 0;
    }

    const dot = dx * nx + dy * ny + dz * nz;
    const sx = dx - nx * dot;
    const sy = dy - ny * dot;
    const sz = dz - nz * dot;

    const cx = (+hit.x) - nx * skin;
    const cy = (+hit.y) - ny * skin;
    const cz = (+hit.z) - nz * skin;

    if (dbg) {
        if (typeof dbg.sphere === "function") dbg.sphere([+hit.x, +hit.y, +hit.z], 0.08, [1, 0.4, 0.2, 0.95], ttl, depth, 0.95, 12);
        if (typeof dbg.ray === "function") dbg.ray([+hit.x, +hit.y, +hit.z], [nx, ny, nz], axisLen, [1, 0.4, 0.2, 0.95], ttl, depth, 0.95, true, 0.12);
        if (typeof dbg.sphere === "function") dbg.sphere([cx, cy, cz], 0.06, [1, 0.85, 0.1, 0.95], ttl, depth, 0.95, 10);
    }

    to.x = cx + sx * 0.25;
    to.y = cy + sy * 0.25;
    to.z = cz + sz * 0.25;

    return true;
}

class CameraCollisionSolver {
    constructor() {
        this.enabled = true;

        // obstacle prevention
        this.radius = 0.25;
        this.surfacePadding = 0.08;
        this.capsuleHalfHeight = 0.0;

        // ground clamp
        this.floorPadding = 0.20;
        this.maxRayLenDown = 8.0;
        this.groundRayLift = 2.0;
        this.groundSnapPen = 0.45;

        // smoothing (ONLY for tiny penetrations)
        this.smooth = 18.0;

        // terrain fallback
        this.useTerrainHeight = true;
        this.terrainWorld = true;

        // slope response
        this.slopePadScale = 0.45;
        this.slopeSlide = 0.35;
        this.slopeMinNy = 0.35;

        // debug draw
        this.debugDraw = true;
        this.debugTTL = 0.06;
        this.debugDepth = false;
        this.debugAxisLen = 0.45;
        this.debugMinYSpan = 0.55;
        this.debugCapsule = true;

        // stability
        this.obstaclePasses = 2;
    }

    solve(ctx) {
        if (!this.enabled) return;

        // publish a safety clamp for post-smoothing stage
        // (CameraOrchestrator MUST respect it, otherwise smoothing can re-penetrate terrain)
        ctx._camMinY = -Infinity;

        const phys = ctx && ctx.physics;
        if (!phys || typeof phys.raycast !== "function") {
            throw new Error("[camera][collision] ctx.physics.raycast(...) is required");
        }

        const zo = ctx.zoneOverrides;
        if (zo && zo.collisionEnabled === false) return;

        const radius = (zo && zo.camRadius != null) ? +zo.camRadius : this.radius;
        const pad = (zo && zo.surfacePadding != null) ? +zo.surfacePadding : this.surfacePadding;

        const baseFloorPad = (zo && zo.floorPadding != null) ? +zo.floorPadding : this.floorPadding;
        const slopePadScale = (zo && zo.slopePadScale != null) ? +zo.slopePadScale : this.slopePadScale;
        const slopeSlide = (zo && zo.slopeSlide != null) ? +zo.slopeSlide : this.slopeSlide;

        const from = ctx.target;
        const to = ctx.outPos;
        if (!from || !to) throw new Error("[camera][collision] ctx.target and ctx.outPos are required");

        const dt = clamp(U.num(ctx.dt, 1 / 60), 0, 0.05);

        const dbg = (this.debugDraw || (zo && zo.debugDraw === true)) ? getDebug() : null;
        const ttl = (zo && zo.debugTTL != null) ? +zo.debugTTL : this.debugTTL;
        const depth = (zo && zo.debugDepth != null) ? !!zo.debugDepth : this.debugDepth;

        // ------------------------------------------------------------
        // 1) obstacles: keep camera OUT of geometry
        // ------------------------------------------------------------
        {
            const dx0 = to.x - from.x;
            const dy0 = to.y - from.y;
            const dz0 = to.z - from.z;
            const nd0 = normalizeDir(dx0, dy0, dz0);

            if (nd0.len > 1e-6) {
                if (dbg && ((zo && zo.debugCapsule != null) ? !!zo.debugCapsule : this.debugCapsule)) {
                    dbgCapsule(dbg, [from.x, from.y, from.z], [to.x, to.y, to.z], radius, ttl, depth, 0.55);
                }

                const passes = clamp((zo && zo.obstaclePasses != null) ? (zo.obstaclePasses | 0) : this.obstaclePasses, 1, 3);
                const hh = (zo && zo.capsuleHalfHeight != null) ? +zo.capsuleHalfHeight : this.capsuleHalfHeight;

                for (let i = 0; i < passes; i++) {
                    const changed = resolveCameraObstacle(
                        phys, dbg, from, to,
                        radius, pad,
                        ttl, depth,
                        this.debugAxisLen * (i ? 0.8 : 1.0),
                        hh
                    );
                    if (!changed) break;
                }
            }
        }

        // ------------------------------------------------------------
        // 2) ground clamp (physics down-ray from ABOVE + terrain fallback)
        // ------------------------------------------------------------
        let groundYPhys = NaN;
        let nxg = 0, nyg = 1, nzg = 0;
        let haveGroundNormal = false;

        const lift = (zo && zo.groundRayLift != null) ? +zo.groundRayLift : this.groundRayLift;
        const rayLen = (zo && zo.maxRayLenDown != null) ? +zo.maxRayLenDown : this.maxRayLenDown;

        const startY = Math.max(from.y, to.y) + Math.max(0.5, lift);
        const down = phys.raycast(to.x, startY, to.z, 0, -1, 0, rayLen);

        if (dbg && typeof dbg.ray === "function") {
            dbg.ray([to.x, startY, to.z], [0, -1, 0], rayLen, [0.45, 0.85, 1, 0.45], ttl, depth, 0.45, true, 0.10);
        }

        if (down && down.hit) {
            groundYPhys = +down.y;

            if (down.normal) {
                nxg = +down.normal.x;
                nyg = +down.normal.y;
                nzg = +down.normal.z;
                if (Number.isFinite(nxg) && Number.isFinite(nyg) && Number.isFinite(nzg)) haveGroundNormal = true;
            }

            if (dbg && typeof dbg.sphere === "function") {
                dbg.sphere([to.x, groundYPhys, to.z], 0.10, [0.45, 0.85, 1, 0.90], ttl, depth, 0.90, 10);
            }
        }

        let groundYTerr = NaN;
        let nxT = 0, nyT = 1, nzT = 0;
        let haveTerrNormal = false;

        const useTerr = (zo && zo.useTerrainHeight != null) ? !!zo.useTerrainHeight : !!this.useTerrainHeight;
        if (useTerr) {
            const terrApi = requireTerrainApi();
            const terrainH = requireTerrainHandle();
            const world = (zo && zo.terrainWorld != null) ? !!zo.terrainWorld : !!this.terrainWorld;

            groundYTerr = +terrApi.heightAt(terrainH, to.x, to.z, world);

            const n = terrApi.normalAt(terrainH, to.x, to.z, world);
            if (n) {
                nxT = +n.x;
                nyT = +n.y;
                nzT = +n.z;
                if (Number.isFinite(nxT) && Number.isFinite(nyT) && Number.isFinite(nzT)) haveTerrNormal = true;
            }

            if (dbg && typeof dbg.sphere === "function") {
                dbg.sphere([to.x, groundYTerr, to.z], 0.09, [0.8, 0.95, 0.55, 0.90], ttl, depth, 0.90, 10);
            }
        }

        let groundY = NaN;
        if (Number.isFinite(groundYPhys) && Number.isFinite(groundYTerr)) groundY = Math.max(groundYPhys, groundYTerr);
        else if (Number.isFinite(groundYPhys)) groundY = groundYPhys;
        else if (Number.isFinite(groundYTerr)) groundY = groundYTerr;
        else return;

        if (Number.isFinite(groundYPhys) && groundY === groundYPhys && haveGroundNormal) {
            // keep physics normal
        } else if (haveTerrNormal) {
            nxg = nxT;
            nyg = nyT;
            nzg = nzT;
            haveGroundNormal = true;
        }

        if (haveGroundNormal) {
            const nlen2 = nxg * nxg + nyg * nyg + nzg * nzg;
            const invN = invSqrt(nlen2);
            if (invN > 0) {
                nxg *= invN;
                nyg *= invN;
                nzg *= invN;
            } else {
                nxg = 0;
                nyg = 1;
                nzg = 0;
                haveGroundNormal = false;
            }
        }

        const nyClamped = clamp(nyg, 0, 1);
        const slope = 1.0 - nyClamped;

        const floorPadEff = baseFloorPad + slope * slopePadScale;
        const minY = groundY + floorPadEff;

        // publish clamp for post-smoothing stage
        ctx._camMinY = minY;

        if (dbg && typeof dbg.line === "function") {
            const s = (zo && zo.debugMinYSpan != null) ? +zo.debugMinYSpan : this.debugMinYSpan;
            dbg.line([to.x - s, minY, to.z], [to.x + s, minY, to.z], [1, 0.2, 0.85, 0.75], ttl, depth, 0.75);
        }

        if (to.y >= minY) return;

        const pen = (minY - to.y);
        const snapPen = (zo && zo.groundSnapPen != null) ? +zo.groundSnapPen : this.groundSnapPen;

        if (pen > snapPen) {
            to.y = minY;
        } else {
            const smooth = (zo && zo.smooth != null) ? +zo.smooth : this.smooth;
            const a = (smooth <= 0) ? 1 : (1 - Math.exp(-smooth * dt));
            to.y = to.y + (minY - to.y) * a;
        }

        if (haveGroundNormal && slopeSlide > 0 && nyClamped < this.slopeMinNy) {
            const hx = nxg;
            const hz = nzg;
            const hlen2 = hx * hx + hz * hz;
            const invH = invSqrt(hlen2);

            if (invH > 0) {
                const ux = hx * invH;
                const uz = hz * invH;

                const k = slopeSlide * clamp(pen, 0, 1.5);
                to.x += ux * k;
                to.z += uz * k;

                if (dbg && typeof dbg.ray === "function") {
                    dbg.ray([to.x, minY, to.z], [ux, 0, uz], 0.45, [1, 0.2, 0.85, 0.75], ttl, depth, 0.75, true, 0.10);
                }
            }
        }
    }
}

module.exports = CameraCollisionSolver;