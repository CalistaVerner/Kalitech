"use strict";

const U = require("./util.js");

class FrameContext {
    constructor() {
        this.dt = 0;
        this.snap = null;

        this.physics = null;   // PHYS (API3)
        this.bodyAccess = null;
        this.bodyId = 0;

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
            steep: false,
            nx: 0, ny: 1, nz: 0,
            distance: 9999,
            footDistance: 9999
        };

        this.character = { radius: 0.35, height: 1.80, eyeHeight: 1.65 };
    }

    begin(player, dt, snap) {
        this.dt = U.num(dt, 0);
        this.snap = snap || null;

        const P = player.d.physics;
        if (!P) throw new Error("[player] PHYS missing in domains");
        this.physics = P;

        const cc = player.characterCfg;
        this.character.radius = U.num(cc.radius, 0.35);
        this.character.height = U.num(cc.height, 1.80);
        this.character.eyeHeight = U.num(cc.eyeHeight, 1.65);

        return this;
    }

    _raycastEx(fx, fy, fz, tx, ty, tz, ignoreBodyId) {
        return this.physics.raycastEx({
            from: [fx, fy, fz],
            to: [tx, ty, tz],
            ignoreBodyId: ignoreBodyId | 0
        });
    }

    probeGroundCapsule(bodyAccess, cfg, ignoreBodyId) {
        const g = this.ground;

        g.hasHit = false;
        g.grounded = false;
        g.steep = false;
        g.nx = 0;
        g.ny = 1;
        g.nz = 0;
        g.distance = 9999;
        g.footDistance = 9999;

        const p = bodyAccess && typeof bodyAccess.position === "function" ? bodyAccess.position() : null;
        if (!p) return false;

        const px = U.vx(p, 0), py = U.vy(p, 0), pz = U.vz(p, 0);

        const r = U.num(cfg.radius, 0.35);
        const h = U.num(cfg.height, 1.80);

        const footY = py - ((h * 0.5) - r);

        const rayDown = U.num(cfg.groundRay, 0.55);
        const startUp = U.num(cfg.groundStart, 0.20);
        const eps = U.num(cfg.groundEps, 0.08);
        const maxSlopeDot = U.num(cfg.maxSlopeDot, 0.55);
        const ring = U.clamp(U.num(cfg.probeRing, 0.85), 0.1, 1.2) * r;

        const startY = footY + startUp;
        const endY = footY - rayDown;

        const ignoreId = (ignoreBodyId | 0) || 0;

        let bestWalk = null, bestWalkDist = 9999;
        let bestAny = null, bestAnyDist = 9999;

        const test = (ox, oz) => {
            const hx = px + ox;
            const hz = pz + oz;

            const hit = this._raycastEx(hx, startY, hz, hx, endY, hz, ignoreId);
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

        const footDist = startUp - dist;
        g.footDistance = footDist;

        const inContact = dist <= (startUp + eps);
        const walkable = inContact && (g.ny >= maxSlopeDot);

        g.grounded = walkable;
        g.steep = inContact && !walkable;

        return g.grounded;
    }
}

module.exports = FrameContext;