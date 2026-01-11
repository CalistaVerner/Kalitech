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

// ------------------------------------------------------------
// debug helpers
// ------------------------------------------------------------
function dbgSphere(dbg, p, r, col, ttl, depth, a, seg) {
    if (!dbg || typeof dbg.sphere !== "function") return;
    dbg.sphere(p, r, col, ttl, depth, a, seg || 10);
}

function dbgLine(dbg, a, b, col, ttl, depth, alpha) {
    if (!dbg || typeof dbg.line !== "function") return;
    dbg.line(a, b, col, ttl, depth, alpha);
}

function dbgRay(dbg, o, d, len, col, ttl, depth, alpha, arrow, arrowLen) {
    if (!dbg || typeof dbg.ray !== "function") return;
    dbg.ray(o, d, len, col, ttl, depth, alpha, !!arrow, (arrowLen != null ? arrowLen : 0.12));
}

// ------------------------------------------------------------
// bundleHit: pseudo spherecast (5 rays) along direction for a given radius
// ------------------------------------------------------------
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
            const d = dx * dirN.x + dy * dirN.y + dz * dirN.z; // projected
            if (d >= 0 && d < bestD) {
                bestD = d;
                best = h;
            }
        }
    }

    return best;
}

// ------------------------------------------------------------
// Pear radius function: small near pivot, grows to full radius
// r(t) = lerp(near, far, t^k)
// ------------------------------------------------------------
function pearRadius(nearR, farR, t, k) {
    t = clamp(t, 0, 1);
    const w = Math.pow(t, k);
    return nearR + (farR - nearR) * w;
}

// ------------------------------------------------------------
// Resolve obstacles with "pear volume": multi-sphere cast along segment
// We search earliest hit among spheres and push camera out.
// ------------------------------------------------------------
function resolvePearObstacle(phys, dbg, from, to, farRadius, nearRadius, pearK, pad, ttl, depth, axisLen, samples) {
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const dz = to.z - from.z;

    const nd = normalizeDir(dx, dy, dz);
    if (nd.len <= 1e-6) return false;

    const len = nd.len;

    let best = null;
    let bestT = 1.0;
    let bestR = farRadius;

    // sample t from near->far (skip t=0 exactly)
    const N = Math.max(3, samples | 0);
    for (let i = 1; i <= N; i++) {
        const t = i / N;
        const r = pearRadius(nearRadius, farRadius, t, pearK);
        const skin = r + pad;

        // sphere center along segment
        const sx = from.x + dx * t;
        const sy = from.y + dy * t;
        const sz = from.z + dz * t;

        // cast forward from this sphere center toward camera direction remaining length
        const remain = len * (1.0 - t) + skin;

        const h = bundleHit(phys, {x: sx, y: sy, z: sz}, nd, remain, r);
        if (h && h.hit) {
            best = h;
            bestT = t;
            bestR = r;
            break; // earliest along segment because i grows from near to far
        }
    }

    if (!(best && best.hit)) return false;

    const n = best.normal;
    if (!n) throw new Error("[camera][collision] hit must provide normal:{x,y,z}");

    let nx = +n.x, ny = +n.y, nz = +n.z;
    if (!Number.isFinite(nx) || !Number.isFinite(ny) || !Number.isFinite(nz)) {
        throw new Error("[camera][collision] hit normal must be finite");
    }

    // normalize normal
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

    const skin = bestR + pad;

    // push camera center out of surface by skin (using hit point as reference)
    const cx = (+best.x) - nx * skin;
    const cy = (+best.y) - ny * skin;
    const cz = (+best.z) - nz * skin;

    // slide component: remove normal component from desired movement (from->to)
    const dot = dx * nx + dy * ny + dz * nz;
    const sx = dx - nx * dot;
    const sy = dy - ny * dot;
    const sz = dz - nz * dot;

    if (dbg) {
        dbgSphere(dbg, [+best.x, +best.y, +best.z], 0.08, [1, 0.4, 0.2, 0.95], ttl, depth, 0.95, 12);
        dbgRay(dbg, [+best.x, +best.y, +best.z], [nx, ny, nz], axisLen, [1, 0.4, 0.2, 0.95], ttl, depth, 0.95, true, 0.12);
        dbgSphere(dbg, [cx, cy, cz], 0.06, [1, 0.85, 0.1, 0.95], ttl, depth, 0.95, 10);
    }

    to.x = cx + sx * 0.25;
    to.y = cy + sy * 0.25;
    to.z = cz + sz * 0.25;

    return true;
}

// ------------------------------------------------------------
// Ground height sampler: physics ray + optional terrain fallback
// returns {y, nx,ny,nz, haveN, src}
// ------------------------------------------------------------
function sampleGround(ctx, x, yHint, z, lift, len, useTerr, terrWorld) {
    const phys = ctx.physics;

    // -----------------------------
    // 1) Physics raycast (down)
    // -----------------------------
    let groundYPhys = NaN;
    let nxP = 0, nyP = 1, nzP = 0;
    let haveP = false;

    const startY = yHint + Math.max(0.25, lift);
    const down = phys.raycast(x, startY, z, 0, -1, 0, Math.max(0.01, len));
    if (down && down.hit) {
        groundYPhys = +down.y;
        if (down.normal) {
            nxP = +down.normal.x;
            nyP = +down.normal.y;
            nzP = +down.normal.z;
            haveP = Number.isFinite(nxP) && Number.isFinite(nyP) && Number.isFinite(nzP);
        }
    }

    // -----------------------------
    // 2) Terrain fallback (auto world/local)
    // -----------------------------
    let yW = NaN, yL = NaN;
    let groundYTerr = NaN;

    let nxTW = 0, nyTW = 1, nzTW = 0, haveTW = false;
    let nxTL = 0, nyTL = 1, nzTL = 0, haveTL = false;

    let terrChosenWorld = !!terrWorld; // default requested
    let haveT = false;
    let nxT = 0, nyT = 1, nzT = 0;

    if (useTerr) {
        const terrApi = requireTerrainApi();
        const terrainH = requireTerrainHandle();

        // Get both heights (world + local). This is cheap and kills "wrong space" instantly.
        yW = +terrApi.heightAt(terrainH, x, z, true);
        yL = +terrApi.heightAt(terrainH, x, z, false);

        const dw = Number.isFinite(yW) ? Math.abs(yW - yHint) : Infinity;
        const dl = Number.isFinite(yL) ? Math.abs(yL - yHint) : Infinity;

        // Choose whichever is closer to current sample Y (yHint). This matches camera space.
        terrChosenWorld = (dw <= dl);

        groundYTerr = terrChosenWorld ? yW : yL;

        // normals (match chosen space)
        const nW = terrApi.normalAt(terrainH, x, z, true);
        if (nW) {
            nxTW = +nW.x;
            nyTW = +nW.y;
            nzTW = +nW.z;
            haveTW = Number.isFinite(nxTW) && Number.isFinite(nyTW) && Number.isFinite(nzTW);
        }
        const nL = terrApi.normalAt(terrainH, x, z, false);
        if (nL) {
            nxTL = +nL.x;
            nyTL = +nL.y;
            nzTL = +nL.z;
            haveTL = Number.isFinite(nxTL) && Number.isFinite(nyTL) && Number.isFinite(nzTL);
        }

        if (Number.isFinite(groundYTerr)) {
            haveT = terrChosenWorld ? haveTW : haveTL;
            if (haveT) {
                nxT = terrChosenWorld ? nxTW : nxTL;
                nyT = terrChosenWorld ? nyTW : nyTL;
                nzT = terrChosenWorld ? nzTW : nzTL;
            }
        }
    }

    // -----------------------------
    // 3) Choose final ground (physics first, else best by closeness)
    // -----------------------------
    let yG = NaN;
    let nx = 0, ny = 1, nz = 0;
    let haveN = false;
    let src = "none";

    const havePhysY = Number.isFinite(groundYPhys);
    const haveTerrY = Number.isFinite(groundYTerr);

    if (havePhysY) {
        // If physics sees ground at all, it’s the most authoritative for collision.
        yG = groundYPhys;
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
        src = "phys";
    } else if (haveTerrY) {
        // No physics hit: trust terrain fallback, but ONLY the chosen (world/local) variant.
        yG = groundYTerr;
        if (haveT) {
            nx = nxT;
            ny = nyT;
            nz = nzT;
            haveN = true;
        }
        src = terrChosenWorld ? "terrW" : "terrL";
    }

    // -----------------------------
    // 4) Normalize normal
    // -----------------------------
    if (haveN) {
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
            haveN = false;
        }
    }

    // -----------------------------
    // 5) Debug dump (throttled by caller via ctx._dbgLog flag)
    // -----------------------------
    if (ctx && ctx._dbgLog && typeof LOG !== "undefined" && LOG && typeof LOG.debug === "function") {
        LOG.debug(
            "[camera][ground] hintY=" + (Number.isFinite(yHint) ? yHint.toFixed(3) : "NaN") +
            " physY=" + (havePhysY ? groundYPhys.toFixed(3) : "NaN") +
            " terrY=" + (haveTerrY ? groundYTerr.toFixed(3) : "NaN") +
            " yW=" + (Number.isFinite(yW) ? yW.toFixed(3) : "NaN") +
            " yL=" + (Number.isFinite(yL) ? yL.toFixed(3) : "NaN") +
            " terrChosenWorld=" + terrChosenWorld +
            " chosen=" + (Number.isFinite(yG) ? yG.toFixed(3) : "NaN") +
            " src=" + src +
            " haveN=" + haveN
        );
    }

    return {y: yG, nx, ny, nz, haveN, src};
}


// ------------------------------------------------------------
// Pear ground clamp:
// ensure for every sample sphere center p(t): p(t).y >= ground + r(t) + floorPadEff
// If violated: lift the whole camera up.
// ------------------------------------------------------------
function pearGroundClamp(ctx, dbg, from, to, nearR, farR, pearK, baseFloorPad, slopePadScale, samples, lift, lenDown, ttl, depth, debugMinYSpan) {
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const dz = to.z - from.z;

    const N = Math.max(3, samples | 0);

    const useTerr = !!ctx._useTerrainHeight;
    const terrWorld = !!ctx._terrainWorld;

    let needLift = 0.0;
    let minYAtCam = -Infinity;

    // We compute worst-case required lift among samples.
    // IMPORTANT: we use each sample's (x,z) and current y as hint for ray start.
    for (let i = 0; i <= N; i++) {
        const t = i / N;

        const px = from.x + dx * t;
        const py = from.y + dy * t;
        const pz = from.z + dz * t;

        const r = pearRadius(nearR, farR, t, pearK);

        const g = sampleGround(ctx, px, py, pz, lift, lenDown, useTerr, terrWorld);
        if (!Number.isFinite(g.y)) continue;

        const nyClamped = clamp(g.ny, 0, 1);
        const slope = 1.0 - nyClamped;
        const floorPadEff = baseFloorPad + slope * slopePadScale;

        const reqCenterY = g.y + r + floorPadEff;

        const pen = reqCenterY - py;
        if (pen > needLift) needLift = pen;

        // minY for camera center at t=1 (far end) for postSmooth clamp
        if (i === N) {
            minYAtCam = reqCenterY;

            if (dbg) {
                const s = debugMinYSpan;
                dbgLine(dbg, [to.x - s, reqCenterY, to.z], [to.x + s, reqCenterY, to.z], [1, 0.2, 0.85, 0.75], ttl, depth, 0.75);
            }
        }

        if (dbg && i % 2 === 0) {
            // draw sample sphere + ground point
            dbgSphere(dbg, [px, py, pz], Math.max(0.02, r), [0.25, 1, 0.6, 0.12], ttl, depth, 0.12, 10);
            dbgSphere(dbg, [px, g.y, pz], 0.06, [0.8, 0.95, 0.55, 0.35], ttl, depth, 0.35, 10);
        }
    }

    if (needLift > 0) {
        to.y += needLift;
    }

    return {minYAtCam, lifted: needLift};
}

class CameraCollisionSolver {
    constructor() {
        this.enabled = true;

        // ------------------------------------
        // Pear volume tuning (THE MAIN FEATURE)
        // ------------------------------------
        this.radius = 0.25;          // far end radius (camera end)
        this.nearRadius = 0.05;      // pivot end radius (attachment end)
        this.pearK = 1.9;            // shape exponent: 1.6..2.2 typical
        this.pearSamples = 8;        // spheres along pivot->camera (quality)

        // obstacle padding
        this.surfacePadding = 0.08;

        // ground clamp
        this.floorPadding = 0.20;
        this.maxRayLenDown = 10.0;
        this.groundRayLift = 1.2;

        // smoothing (only for small penetrations of Y)
        this.smooth = 18.0;
        this.groundSnapPen = 0.55;

        // terrain fallback
        this.useTerrainHeight = true;
        this.terrainWorld = true;

        // slope response
        this.slopePadScale = 0.45;
        this.slopeSlide = 0.35;
        this.slopeMinNy = 0.35;

        // debug
        this.debugDraw = true;
        this.debugTTL = 0.06;
        this.debugDepth = false;
        this.debugAxisLen = 0.45;
        this.debugMinYSpan = 0.55;

        this.debugPear = true;
        this.debugPearStep = 2; // draw every Nth sphere segment

        // stability
        this.obstaclePasses = 2;

        this.logEnabled = true;     // включатель
        this.logEvery = 12;         // раз в N кадров
        this._logTick = 0;

    }

    solve(ctx) {
        if (!this.enabled) return;

        // publish clamp for postSmooth stage
        ctx._camMinY = -Infinity;

        const phys = ctx && ctx.physics;
        if (!phys || typeof phys.raycast !== "function") {
            throw new Error("[camera][collision] ctx.physics.raycast(...) is required");
        }

        // ---- cheap throttled logging ----
        const doLog = this.logEnabled && ((this._logTick++ % Math.max(1, this.logEvery | 0)) === 0);
        const log = (typeof LOG !== "undefined" && LOG && typeof LOG.debug === "function") ? LOG : null;

        if (doLog && log) {
            const modeId = ctx && ctx.mode && ctx.mode.id ? ctx.mode.id : "??";
            const zo = ctx.zoneOverrides;

            const useTerr = (zo && zo.useTerrainHeight != null) ? !!zo.useTerrainHeight : !!this.useTerrainHeight;
            const terrWorld = (zo && zo.terrainWorld != null) ? !!zo.terrainWorld : !!this.terrainWorld;

            log.debug(
                "[camera][collision] solve mode=" + modeId +
                " useTerr=" + useTerr +
                " terrWorld=" + terrWorld +
                " TERRAIN.global=" + (typeof TERRAIN !== "undefined" && !!TERRAIN)
            );

            // target/outPos snapshot
            const f = ctx.target, t = ctx.outPos;
            if (f && t) {
                log.debug(
                    "[camera][collision] from=(" + f.x.toFixed(3) + "," + f.y.toFixed(3) + "," + f.z.toFixed(3) + ")" +
                    " to=(" + t.x.toFixed(3) + "," + t.y.toFixed(3) + "," + t.z.toFixed(3) + ")"
                );
            }
        }


        const zo = ctx.zoneOverrides;
        if (zo && zo.collisionEnabled === false) return;

        const farR = (zo && zo.camRadius != null) ? +zo.camRadius : this.radius;
        const nearR = (zo && zo.nearRadius != null) ? +zo.nearRadius : this.nearRadius;
        const pearK = (zo && zo.pearK != null) ? +zo.pearK : this.pearK;
        const pearSamples = (zo && zo.pearSamples != null) ? (zo.pearSamples | 0) : this.pearSamples;

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

        // terrain mode flags for sampling helper
        ctx._useTerrainHeight = (zo && zo.useTerrainHeight != null) ? !!zo.useTerrainHeight : !!this.useTerrainHeight;
        ctx._terrainWorld = (zo && zo.terrainWorld != null) ? !!zo.terrainWorld : !!this.terrainWorld;

        // ------------------------------------------------------------
        // 1) Obstacles with PEAR volume (pivot -> camera)
        // ------------------------------------------------------------
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
                    pearSamples
                );
                if (!changed) break;
            }

            if (dbg && (this.debugPear || (zo && zo.debugPear === true))) {
                // visualize pear as spheres along the segment
                const N = Math.max(3, pearSamples | 0);
                for (let i = 0; i <= N; i++) {
                    if ((i % (this.debugPearStep | 0)) !== 0) continue;
                    const t = i / N;
                    const r = pearRadius(nearR, farR, t, pearK);
                    const px = from.x + (to.x - from.x) * t;
                    const py = from.y + (to.y - from.y) * t;
                    const pz = from.z + (to.z - from.z) * t;
                    dbgSphere(dbg, [px, py, pz], r, [0.2, 0.9, 1.0, 0.08], ttl, depth, 0.08, 12);
                }
            }
        }

        // ------------------------------------------------------------
        // 2) Ground clamp for PEAR volume (the "lower boundary" rule)
        // ------------------------------------------------------------
        const lift = (zo && zo.groundRayLift != null) ? +zo.groundRayLift : this.groundRayLift;
        const lenDown = (zo && zo.maxRayLenDown != null) ? +zo.maxRayLenDown : this.maxRayLenDown;
        ctx._dbgLog = doLog;
        const clampRes = pearGroundClamp(
            ctx, dbg,
            from, to,
            nearR, farR, pearK,
            baseFloorPad, slopePadScale,
            pearSamples,
            lift, lenDown,
            ttl, depth,
            (zo && zo.debugMinYSpan != null) ? +zo.debugMinYSpan : this.debugMinYSpan
        );

        if (doLog && log) {
            log.debug(
                "[camera][ground] minYAtCam=" + (Number.isFinite(clampRes.minYAtCam) ? clampRes.minYAtCam.toFixed(3) : "NaN") +
                " camY=" + (Number.isFinite(to.y) ? to.y.toFixed(3) : "NaN") +
                " lifted=" + (Number.isFinite(clampRes.lifted) ? clampRes.lifted.toFixed(3) : "NaN")
            );
        }

        // publish minY for postSmooth: IMPORTANT to prevent zoom-smoothing from dipping
        if (Number.isFinite(clampRes.minYAtCam)) {
            ctx._camMinY = clampRes.minYAtCam;
        }

        // If after lifting we are still below minY (rare: smoothing), clamp again
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

        // Optional: micro-slide along slope near camera end (uses normal at camera end)
        // We approximate normal by sampling ground at camera end:
        if (slopeSlide > 0 && Number.isFinite(ctx._camMinY)) {
            const gEnd = sampleGround(ctx, to.x, to.y, to.z, lift, lenDown, ctx._useTerrainHeight, ctx._terrainWorld);
            if (gEnd.haveN) {
                const nyClamped = clamp(gEnd.ny, 0, 1);
                if (nyClamped < this.slopeMinNy) {
                    const hx = gEnd.nx;
                    const hz = gEnd.nz;
                    const hlen2 = hx * hx + hz * hz;
                    const invH = invSqrt(hlen2);
                    if (invH > 0) {
                        const ux = hx * invH;
                        const uz = hz * invH;

                        const pen = Math.max(0, ctx._camMinY - to.y);
                        const k = slopeSlide * clamp(pen, 0, 1.5);

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