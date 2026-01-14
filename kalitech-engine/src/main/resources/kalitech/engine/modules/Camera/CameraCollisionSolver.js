"use strict";

const U = require("./camUtil.js");

function clamp(v, lo, hi) {
    return v < lo ? lo : (v > hi ? hi : v);
}

function invSqrt(x) {
    return (x > 0) ? (1.0 / Math.sqrt(x)) : 0;
}

function getDbg(enabled) {
    if (!enabled) return null;
    const e = (typeof ENGINE !== "undefined") ? ENGINE : null;
    if (!e) return null;

    const d = e.debug;
    if (!d) return null;

    const dbg = (typeof d === "function") ? d() : d;
    if (!dbg) return null;

    if (typeof dbg.scope === "function") return dbg.scope("camera").scope("collision");
    if (typeof dbg.child === "function") return dbg.child("camera").child("collision");
    return dbg;
}

function requireTerrainApi() {
    const e = (typeof ENGINE !== "undefined") ? ENGINE : null;
    if (!e || !e.terrain) throw new Error("[camera][collision] ENGINE.terrain is required");
    const terr = e.terrain;
    if (typeof terr.heightAt !== "function" || typeof terr.normalAt !== "function") {
        throw new Error("[camera][collision] ENGINE.terrain.heightAt/normalAt are required");
    }
    return terr;
}

function requireTerrainHandle() {
    const t = (typeof TERRAIN !== "undefined") ? TERRAIN : null;
    if (!t) throw new Error("[camera][collision] TERRAIN global is required when terrain sampling is enabled");
    return (t && typeof t === "object" && t.surface) ? t.surface : t;
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
    const invR = invSqrt(rx * rx + ry * ry + rz * rz);
    rx *= invR;
    ry *= invR;
    rz *= invR;

    let ux = ry * dz - rz * dy;
    let uy = rz * dx - rx * dz;
    let uz = rx * dy - ry * dx;
    const invU = invSqrt(ux * ux + uy * uy + uz * uz);
    ux *= invU;
    uy *= invU;
    uz *= invU;

    return {rx, ry, rz, ux, uy, uz};
}

function dbgSphere(dbg, p, r, col, ttl, depth, a, seg) {
    if (dbg && typeof dbg.sphere === "function") dbg.sphere(p, r, col, ttl, depth, a, seg || 10);
}
function dbgLine(dbg, a, b, col, ttl, depth, alpha) {
    if (dbg && typeof dbg.line === "function") dbg.line(a, b, col, ttl, depth, alpha);
}
function dbgRay(dbg, o, d, len, col, ttl, depth, alpha, arrow, arrowLen) {
    if (dbg && typeof dbg.ray === "function") dbg.ray(o, d, len, col, ttl, depth, alpha, !!arrow, (arrowLen != null ? arrowLen : 0.12));
}

// ------------------------------------------------------------
// Physics adapter: make mesh checks work EXACTLY like terrain checks,
// but through cfg-contract (raycastEx). Returns legacy {hit,x,y,z,normal}.
// ------------------------------------------------------------

function isObj(v) {
    return !!v && typeof v === "object";
}

function toLegacyHitFromMap(h) {
    if (!h || h.hit !== true) return null;

    const p = h.point || h.p || null;
    const n = h.normal || null;

    if (!p || !isObj(p)) return null;

    const x = +p.x, y = +p.y, z = +p.z;
    if (!(Number.isFinite(x) && Number.isFinite(y) && Number.isFinite(z))) return null;

    let nx = 0, ny = 1, nz = 0;
    if (n && isObj(n)) {
        nx = +n.x;
        ny = +n.y;
        nz = +n.z;
        if (!(Number.isFinite(nx) && Number.isFinite(ny) && Number.isFinite(nz))) {
            nx = 0;
            ny = 1;
            nz = 0;
        }
        const invN = invSqrt(nx * nx + ny * ny + nz * nz);
        if (invN > 0) {
            nx *= invN;
            ny *= invN;
            nz *= invN;
        } else {
            nx = 0;
            ny = 1;
            nz = 0;
        }
    }

    return {hit: true, x, y, z, normal: {x: nx, y: ny, z: nz}};
}

function toLegacyHitFromRayHit(h) {
    // in case backend returns PhysicsRayHit (no `hit` boolean)
    if (!h || !isObj(h)) return null;

    // common patterns: {point:{x,y,z}, normal:{x,y,z}} or {x,y,z, normal:...}
    if (h.point && isObj(h.point)) {
        const x = +h.point.x, y = +h.point.y, z = +h.point.z;
        if (!(Number.isFinite(x) && Number.isFinite(y) && Number.isFinite(z))) return null;

        const n = h.normal && isObj(h.normal) ? h.normal : null;
        let nx = 0, ny = 1, nz = 0;
        if (n) {
            nx = +n.x;
            ny = +n.y;
            nz = +n.z;
            if (!(Number.isFinite(nx) && Number.isFinite(ny) && Number.isFinite(nz))) {
                nx = 0;
                ny = 1;
                nz = 0;
            }
            const invN = invSqrt(nx * nx + ny * ny + nz * nz);
            if (invN > 0) {
                nx *= invN;
                ny *= invN;
                nz *= invN;
            } else {
                nx = 0;
                ny = 1;
                nz = 0;
            }
        }
        return {hit: true, x, y, z, normal: {x: nx, y: ny, z: nz}};
    }

    if (Number.isFinite(+h.x) && Number.isFinite(+h.y) && Number.isFinite(+h.z)) {
        const x = +h.x, y = +h.y, z = +h.z;
        const n = h.normal && isObj(h.normal) ? h.normal : null;
        let nx = 0, ny = 1, nz = 0;
        if (n) {
            nx = +n.x;
            ny = +n.y;
            nz = +n.z;
            if (!(Number.isFinite(nx) && Number.isFinite(ny) && Number.isFinite(nz))) {
                nx = 0;
                ny = 1;
                nz = 0;
            }
            const invN = invSqrt(nx * nx + ny * ny + nz * nz);
            if (invN > 0) {
                nx *= invN;
                ny *= invN;
                nz *= invN;
            } else {
                nx = 0;
                ny = 1;
                nz = 0;
            }
        }
        return {hit: true, x, y, z, normal: {x: nx, y: ny, z: nz}};
    }

    return null;
}

function physRayLegacy(phys, ox, oy, oz, dx, dy, dz, len, ignoreBodyId) {
    if (!phys) return null;

    const nd = normalizeDir(dx, dy, dz);
    if (nd.len <= 1e-8) return null;

    const L = Math.max(0.01, +len);
    const tx = ox + nd.x * L;
    const ty = oy + nd.y * L;
    const tz = oz + nd.z * L;

    const cfg = {
        from: [ox, oy, oz],
        to: [tx, ty, tz],
        ignoreBodyId: ignoreBodyId | 0,
        staticOnly: false
    };

    // Prefer raycastEx because it returns a map with hit/point/normal
    if (typeof phys.raycastEx === "function") {
        return toLegacyHitFromMap(phys.raycastEx(cfg));
    }

    // Fallback: raycast(cfg) may return PhysicsRayHit (no hit flag)
    if (typeof phys.raycast === "function") {
        const h = phys.raycast(cfg);
        const m = toLegacyHitFromMap(h);
        if (m) return m;
        return toLegacyHitFromRayHit(h);
    }

    return null;
}

// ------------------------------------------------------------
// Original pear obstacle logic, but now mesh collision works via physRayLegacy()
// ------------------------------------------------------------

function bundleHit(phys, from, dirN, len, radius, ignoreBodyId) {
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

        const h = physRayLegacy(phys, o[0], o[1], o[2], dirN.x, dirN.y, dirN.z, len, ignoreBodyId);
        if (!h || !h.hit) continue;

        const hx = +h.x, hy = +h.y, hz = +h.z;
        const dx = hx - o[0], dy = hy - o[1], dz = hz - o[2];
        const d = dx * dirN.x + dy * dirN.y + dz * dirN.z;

        if (d >= 0 && d < bestD) {
            bestD = d;
            best = h;
        }
    }

    return best;
}

function pearRadius(nearR, farR, t, k) {
    t = clamp(t, 0, 1);
    const w = Math.pow(t, k);
    return nearR + (farR - nearR) * w;
}

function resolvePearObstacle(phys, dbg, from, to, farRadius, nearRadius, pearK, pad, ttl, depth, axisLen, samples, ignoreBodyId) {
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const dz = to.z - from.z;

    const nd = normalizeDir(dx, dy, dz);
    if (nd.len <= 1e-6) return false;

    const len = nd.len;

    let best = null;
    let bestR = farRadius;

    const N = Math.max(3, samples | 0);
    for (let i = 1; i <= N; i++) {
        const t = i / N;
        const r = pearRadius(nearRadius, farRadius, t, pearK);
        const skin = r + pad;

        const sx = from.x + dx * t;
        const sy = from.y + dy * t;
        const sz = from.z + dz * t;

        const remain = len * (1.0 - t) + skin;
        const h = bundleHit(phys, {x: sx, y: sy, z: sz}, nd, remain, r, ignoreBodyId);
        if (h && h.hit) {
            best = h;
            bestR = r;
            break;
        }
    }

    if (!best) return false;

    const n = best.normal || {x: 0, y: 1, z: 0};

    let nx = +n.x, ny = +n.y, nz = +n.z;
    if (!(Number.isFinite(nx) && Number.isFinite(ny) && Number.isFinite(nz))) {
        nx = 0;
        ny = 1;
        nz = 0;
    }

    const invN = invSqrt(nx * nx + ny * ny + nz * nz);
    if (invN > 0) {
        nx *= invN;
        ny *= invN;
        nz *= invN;
    } else {
        nx = 0;
        ny = 1;
        nz = 0;
    }

    const skin = bestR + pad;

    const hx = +best.x, hy = +best.y, hz = +best.z;
    const cx = hx - nx * skin;
    const cy = hy - ny * skin;
    const cz = hz - nz * skin;

    const dot = dx * nx + dy * ny + dz * nz;
    const sx = dx - nx * dot;
    const sy = dy - ny * dot;
    const sz = dz - nz * dot;

    if (dbg) {
        dbgSphere(dbg, [hx, hy, hz], 0.08, [1, 0.4, 0.2, 0.95], ttl, depth, 0.95, 12);
        dbgRay(dbg, [hx, hy, hz], [nx, ny, nz], axisLen, [1, 0.4, 0.2, 0.95], ttl, depth, 0.95, true, 0.12);
        dbgSphere(dbg, [cx, cy, cz], 0.06, [1, 0.85, 0.1, 0.95], ttl, depth, 0.95, 10);
    }

    to.x = cx + sx * 0.25;
    to.y = cy + sy * 0.25;
    to.z = cz + sz * 0.25;
    return true;
}

// ------------------------------------------------------------
// Ground sampling: physics first (meshes + terrain collision bodies),
// then terrain fallback (exactly your old behavior).
// Physics query is fixed to cfg-based raycastEx through physRayLegacy().
// ------------------------------------------------------------

function sampleGround(ctx, x, yHint, z, lift, len, useTerr, terrWorld, ignoreBodyId) {
    const phys = ctx.physics;

    let yPhys = NaN;
    let nxP = 0, nyP = 1, nzP = 0, haveP = false;

    const startY = yHint + Math.max(0.25, lift);

    const down = physRayLegacy(phys, x, startY, z, 0, -1, 0, Math.max(0.01, len), ignoreBodyId);
    if (down && down.hit) {
        yPhys = +down.y;
        const n = down.normal;
        if (n) {
            nxP = +n.x;
            nyP = +n.y;
            nzP = +n.z;
            haveP = Number.isFinite(nxP) && Number.isFinite(nyP) && Number.isFinite(nzP);
        }
    }

    let yTerr = NaN;
    let nxT = 0, nyT = 1, nzT = 0, haveT = false;
    let terrChosenWorld = !!terrWorld;

    if (useTerr) {
        const terrApi = requireTerrainApi();
        const terrainH = requireTerrainHandle();

        const yW = +terrApi.heightAt(terrainH, x, z, true);
        const yL = +terrApi.heightAt(terrainH, x, z, false);

        const dw = Number.isFinite(yW) ? Math.abs(yW - yHint) : Infinity;
        const dl = Number.isFinite(yL) ? Math.abs(yL - yHint) : Infinity;

        terrChosenWorld = (dw <= dl);
        yTerr = terrChosenWorld ? yW : yL;

        const n = terrApi.normalAt(terrainH, x, z, terrChosenWorld);
        if (n) {
            nxT = +n.x;
            nyT = +n.y;
            nzT = +n.z;
            haveT = Number.isFinite(nxT) && Number.isFinite(nyT) && Number.isFinite(nzT);
        }
    }

    let y = NaN;
    let nx = 0, ny = 1, nz = 0;
    let haveN = false;

    if (Number.isFinite(yPhys)) {
        y = yPhys;
        if (haveP) {
            nx = nxP;
            ny = nyP;
            nz = nzP;
            haveN = true;
        } else if (haveT) {
            nx = nxT;
            ny = nyT;
            nz = nzT;
            haveN = true;
        }
    } else if (Number.isFinite(yTerr)) {
        y = yTerr;
        if (haveT) {
            nx = nxT;
            ny = nyT;
            nz = nzT;
            haveN = true;
        }
    }

    if (haveN) {
        const invN = invSqrt(nx * nx + ny * ny + nz * nz);
        if (invN > 0) {
            nx *= invN;
            ny *= invN;
            nz *= invN;
        } else {
            nx = 0;
            ny = 1;
            nz = 0;
            haveN = false;
        }
    }

    return {y, nx, ny, nz, haveN, terrChosenWorld};
}

function pearGroundClamp(ctx, dbg, from, to, nearR, farR, pearK, baseFloorPad, slopePadScale, samples, lift, lenDown, ttl, depth, debugMinYSpan, ignoreBodyId) {
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const dz = to.z - from.z;

    const N = Math.max(3, samples | 0);

    const useTerr = !!ctx._useTerrainHeight;
    const terrWorld = !!ctx._terrainWorld;

    let needLift = 0.0;
    let minYAtCam = -Infinity;

    for (let i = 0; i <= N; i++) {
        const t = i / N;

        const px = from.x + dx * t;
        const py = from.y + dy * t;
        const pz = from.z + dz * t;

        const r = pearRadius(nearR, farR, t, pearK);
        const g = sampleGround(ctx, px, py, pz, lift, lenDown, useTerr, terrWorld, ignoreBodyId);
        if (!Number.isFinite(g.y)) continue;

        const nyClamped = clamp(g.ny, 0, 1);
        const slope = 1.0 - nyClamped;
        const floorPadEff = baseFloorPad + slope * slopePadScale;

        const reqCenterY = g.y + r + floorPadEff;
        const pen = reqCenterY - py;

        if (pen > needLift) needLift = pen;

        if (i === N) {
            minYAtCam = reqCenterY;
            if (dbg) {
                const s = debugMinYSpan;
                dbgLine(dbg, [to.x - s, reqCenterY, to.z], [to.x + s, reqCenterY, to.z], [1, 0.2, 0.85, 0.75], ttl, depth, 0.75);
            }
        }

        if (dbg && (i % 2 === 0)) {
            dbgSphere(dbg, [px, py, pz], Math.max(0.02, r), [0.25, 1, 0.6, 0.12], ttl, depth, 0.12, 10);
            dbgSphere(dbg, [px, g.y, pz], 0.06, [0.8, 0.95, 0.55, 0.35], ttl, depth, 0.35, 10);
        }
    }

    if (needLift > 0) to.y += needLift;
    return {minYAtCam, lifted: needLift};
}

class CameraCollisionSolver {
    constructor() {
        this.enabled = true;

        this.radius = 0.25;
        this.nearRadius = 0.05;
        this.pearK = 1.9;
        this.pearSamples = 8;

        this.surfacePadding = 0.08;

        this.floorPadding = 0.20;
        this.maxRayLenDown = 10.0;
        this.groundRayLift = 1.2;

        this.smooth = 18.0;
        this.groundSnapPen = 0.55;

        this.useTerrainHeight = true;
        this.terrainWorld = true;

        this.slopePadScale = 0.45;
        this.slopeSlide = 0.35;
        this.slopeMinNy = 0.35;

        this.debugDraw = false;
        this.debugTTL = 0.06;
        this.debugDepth = false;
        this.debugAxisLen = 0.45;
        this.debugMinYSpan = 0.55;

        this.debugPear = true;
        this.debugPearStep = 2;

        this.obstaclePasses = 2;
    }

    solve(ctx) {
        if (!this.enabled) return;

        ctx._camMinY = -Infinity;

        const phys = ctx && ctx.physics;
        if (!phys || (typeof phys.raycastEx !== "function" && typeof phys.raycast !== "function")) {
            throw new Error("[camera][collision] ctx.physics.raycastEx(cfg) or raycast(cfg) is required");
        }

        const zo = ctx.zoneOverrides;
        if (zo && zo.collisionEnabled === false) return;

        const from = ctx.target;
        const to = ctx.outPos;
        if (!from || !to) throw new Error("[camera][collision] ctx.target and ctx.outPos are required");

        const ignoreBodyId = (ctx.bodyId | 0) || 0;

        const farR = (zo && zo.camRadius != null) ? +zo.camRadius : this.radius;
        const nearR = (zo && zo.nearRadius != null) ? +zo.nearRadius : this.nearRadius;
        const pearK = (zo && zo.pearK != null) ? +zo.pearK : this.pearK;
        const pearSamples = (zo && zo.pearSamples != null) ? (zo.pearSamples | 0) : this.pearSamples;

        const pad = (zo && zo.surfacePadding != null) ? +zo.surfacePadding : this.surfacePadding;

        const baseFloorPad = (zo && zo.floorPadding != null) ? +zo.floorPadding : this.floorPadding;
        const slopePadScale = (zo && zo.slopePadScale != null) ? +zo.slopePadScale : this.slopePadScale;
        const slopeSlide = (zo && zo.slopeSlide != null) ? +zo.slopeSlide : this.slopeSlide;

        const dt = clamp(U.num(ctx.dt, 1 / 60), 0, 0.05);

        const dbg = getDbg(this.debugDraw || (zo && zo.debugDraw === true));
        const ttl = (zo && zo.debugTTL != null) ? +zo.debugTTL : this.debugTTL;
        const depth = (zo && zo.debugDepth != null) ? !!zo.debugDepth : this.debugDepth;

        ctx._useTerrainHeight = (zo && zo.useTerrainHeight != null) ? !!zo.useTerrainHeight : !!this.useTerrainHeight;
        ctx._terrainWorld = (zo && zo.terrainWorld != null) ? !!zo.terrainWorld : !!this.terrainWorld;

        {
            const passes = clamp((zo && zo.obstaclePasses != null) ? (zo.obstaclePasses | 0) : this.obstaclePasses, 1, 3);

            for (let i = 0; i < passes; i++) {
                const changed = resolvePearObstacle(
                    phys, dbg,
                    from, to,
                    farR, nearR, pearK,
                    pad,
                    ttl, depth,
                    this.debugAxisLen * (i ? 0.8 : 1.0),
                    pearSamples,
                    ignoreBodyId
                );
                if (!changed) break;
            }

            if (dbg && (this.debugPear || (zo && zo.debugPear === true))) {
                const N = Math.max(3, pearSamples | 0);
                const step = Math.max(1, this.debugPearStep | 0);
                for (let i = 0; i <= N; i++) {
                    if ((i % step) !== 0) continue;
                    const t = i / N;
                    const r = pearRadius(nearR, farR, t, pearK);
                    const px = from.x + (to.x - from.x) * t;
                    const py = from.y + (to.y - from.y) * t;
                    const pz = from.z + (to.z - from.z) * t;
                    dbgSphere(dbg, [px, py, pz], r, [0.2, 0.9, 1.0, 0.08], ttl, depth, 0.08, 12);
                }
            }
        }

        const lift = (zo && zo.groundRayLift != null) ? +zo.groundRayLift : this.groundRayLift;
        const lenDown = (zo && zo.maxRayLenDown != null) ? +zo.maxRayLenDown : this.maxRayLenDown;

        const clampRes = pearGroundClamp(
            ctx, dbg,
            from, to,
            nearR, farR, pearK,
            baseFloorPad, slopePadScale,
            pearSamples,
            lift, lenDown,
            ttl, depth,
            (zo && zo.debugMinYSpan != null) ? +zo.debugMinYSpan : this.debugMinYSpan,
            ignoreBodyId
        );

        if (Number.isFinite(clampRes.minYAtCam)) ctx._camMinY = clampRes.minYAtCam;

        if (Number.isFinite(ctx._camMinY) && to.y < ctx._camMinY) {
            const pen = ctx._camMinY - to.y;
            const snapPen = (zo && zo.groundSnapPen != null) ? +zo.groundSnapPen : this.groundSnapPen;

            if (pen > snapPen) {
                to.y = ctx._camMinY;
            } else {
                const smooth = (zo && zo.smooth != null) ? +zo.smooth : this.smooth;
                const a = (smooth <= 0) ? 1 : (1 - Math.exp(-smooth * dt));
                to.y = to.y + (ctx._camMinY - to.y) * a;
            }
        }

        if (slopeSlide > 0 && Number.isFinite(ctx._camMinY)) {
            const gEnd = sampleGround(ctx, to.x, to.y, to.z, lift, lenDown, ctx._useTerrainHeight, ctx._terrainWorld, ignoreBodyId);
            if (gEnd.haveN) {
                const nyClamped = clamp(gEnd.ny, 0, 1);
                if (nyClamped < this.slopeMinNy) {
                    const hx = gEnd.nx;
                    const hz = gEnd.nz;
                    const invH = invSqrt(hx * hx + hz * hz);
                    if (invH > 0) {
                        const ux = hx * invH;
                        const uz = hz * invH;

                        const k = slopeSlide * clamp(Math.max(0, ctx._camMinY - to.y), 0, 1.5);
                        to.x += ux * k;
                        to.z += uz * k;

                        if (dbg) {
                            dbgRay(dbg, [to.x, ctx._camMinY, to.z], [ux, 0, uz], 0.45, [1, 0.2, 0.85, 0.75], ttl, depth, 0.75, true, 0.10);
                        }
                    }
                }
            }
        }
    }
}

module.exports = CameraCollisionSolver;