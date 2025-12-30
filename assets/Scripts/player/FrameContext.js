// FILE: Scripts/player/FrameContext.js
// Author: Calista Verner
"use strict";

const U = require("./util.js");

class FrameContext {
    constructor() {
        this.dt = 0;
        this.snap = null;

        this.ids = { entityId: 0, surfaceId: 0, bodyId: 0 };
        this.input = {
            ax: 0, az: 0,
            run: false,
            jump: false,
            lmbDown: false,
            lmbJustPressed: false,
            dx: 0, dy: 0, wheel: 0
        };

        this.view = { yaw: 0, pitch: 0, type: "third" };

        this.pose = {
            x: 0, y: 0, z: 0,
            vx: 0, vy: 0, vz: 0,
            speed: 0,
            fallSpeed: 0,
            grounded: false
        };

        this.ground = {
            hasHit: false,
            grounded: false,
            ny: 1,
            nx: 0,
            nz: 0,
            distance: 9999,      // distance from start of ray
            footDistance: 9999,  // distance from footY (>=0 means below foot)
            steep: false
        };

        this.character = { radius: 0.35, height: 1.80, eyeHeight: 1.65 };

        this._fromA = [0, 0, 0];
        this._toA = [0, 0, 0];
    }

    begin(player, dt, snap) {
        this.dt = U.num(dt, 0);
        this.snap = snap || null;

        this.ids.entityId = player.entityId | 0;
        this.ids.surfaceId = player.surfaceId | 0;
        this.ids.bodyId = player.bodyId | 0;

        const cc = player.characterCfg;
        if (cc) {
            this.character.radius = U.num(cc.radius, 0.35);
            this.character.height = U.num(cc.height, 1.80);
            this.character.eyeHeight = U.num(cc.eyeHeight, 1.65);
        }
        return this;
    }

    _raycastEx(fx, fy, fz, tx, ty, tz, ignoreBodyId) {
        return PHYS.raycastEx({
            from: [fx, fy, fz],
            to:   [tx, ty, tz],
            ignoreBodyId: ignoreBodyId | 0
        });
    }

    probeGroundCapsule(body, cfg) {
        const g = this.ground;

        g.hasHit = false;
        g.grounded = false;
        g.steep = false;
        g.ny = 1; g.nx = 0; g.nz = 0;
        g.distance = 9999;
        g.footDistance = 9999;

        if (!body || !cfg) return false;
        if (typeof body.position !== "function") throw new Error("[frame] body.position missing");

        const p = body.position();
        if (!p) return false;

        const px = U.vx(p, 0), py = U.vy(p, 0), pz = U.vz(p, 0);

        const r = U.num(cfg.radius, 0.35);
        const h = U.num(cfg.height, 1.80);

        // center - (halfHeight - radius)
        const footY = py - ((h * 0.5) - r);

        const rayDown = U.num(cfg.groundRay, 0.55);
        const startUp = U.num(cfg.groundStart, 0.20);
        const eps = U.num(cfg.groundEps, 0.08);
        const maxSlopeDot = U.num(cfg.maxSlopeDot, 0.55);
        const ring = U.clamp(U.num(cfg.probeRing, 0.85), 0.1, 1.2) * r;

        const startY = footY + startUp;
        const endY = footY - rayDown;

        const ignoreId = (typeof body.id === "function") ? (body.id() | 0) : 0;

        let bestWalk = null, bestWalkDist = 9999;
        let bestAny = null, bestAnyDist = 9999;

        const test = (ox, oz) => {
            const hx = px + ox;
            const hz = pz + oz;

            const hit = this._raycastEx(hx, startY, hz, hx, endY, hz, ignoreId);

            // IMPORTANT: some engine builds return null -> treat as "no hit"
            if (!hit || hit.hit !== true) return;

            const dist = U.num(hit.distance, NaN);
            if (!Number.isFinite(dist)) return;

            const n = hit.normal;
            const ny = n ? U.num(n.y, 1) : 1;

            if (dist < bestAnyDist) { bestAny = hit; bestAnyDist = dist; }
            if (ny >= maxSlopeDot && dist < bestWalkDist) { bestWalk = hit; bestWalkDist = dist; }
        };

        test(0, 0);
        test(ring, 0);
        test(-ring, 0);
        test(0, ring);
        test(0, -ring);

        const chosen = bestWalk || bestAny;
        if (!chosen) return false;

        const n = chosen.normal || { x: 0, y: 1, z: 0 };
        const dist = (chosen === bestWalk) ? bestWalkDist : bestAnyDist;

        g.hasHit = true;
        g.distance = dist;
        g.nx = U.num(n.x, 0);
        g.ny = U.num(n.y, 1);
        g.nz = U.num(n.z, 0);

        // dist is measured FROM startY вниз
        // точка хита по Y: hitY = startY - dist
        // расстояние от footY: footDist = (startY - dist) - footY = startUp - dist
        // когда стоим на земле: dist ≈ startUp  => footDist ≈ 0
        const footDist = startUp - dist;
        g.footDistance = footDist;

        // ✅ правильный контакт: хит должен быть в пределах "под ногой" (около startUp)
        const inContact = dist <= (startUp + eps);

        const walkable = inContact && (g.ny >= maxSlopeDot);

        g.grounded = walkable;
        g.steep = inContact && !walkable;

        return g.grounded;
    }
}

module.exports = FrameContext;