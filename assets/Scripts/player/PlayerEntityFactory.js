// FILE: Scripts/player/PlayerEntityFactory.js
// Author: Calista Verner
"use strict";

function idOf(h, kind /* "body"|"surface" */) {
    if (h == null) return 0;
    if (typeof h === "number") return h | 0;

    try {
        if (typeof h.valueOf === "function") {
            const v = h.valueOf();
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
    } catch (_) {}

    try {
        if (typeof h.id === "number") return h.id | 0;
        if (typeof h.bodyId === "number") return h.bodyId | 0;
        if (typeof h.surfaceId === "number") return h.surfaceId | 0;
    } catch (_) {}

    const fnNames =
        kind === "body"
            ? ["id", "getId", "bodyId", "getBodyId", "handle"]
            : ["id", "getId", "surfaceId", "getSurfaceId", "handle"];

    for (let i = 0; i < fnNames.length; i++) {
        const n = fnNames[i];
        try {
            const fn = h[n];
            if (typeof fn === "function") {
                const v = fn.call(h);
                if (typeof v === "number" && isFinite(v)) return v | 0;
            }
        } catch (_) {}
    }

    return 0;
}

class PlayerEntity {
    constructor() {
        this.entityId = 0;
        this.surface = null;
        this.body = null;      // can be a PHYS.ref wrapper OR Java handle

        this.surfaceId = 0;
        this.bodyId = 0;

        // ✅ optional cached wrapper
        this.bodyRef = null;
    }

    _ensureBodyRef() {
        // prefer explicit wrapper if already stored
        if (this.bodyRef) return this.bodyRef;

        // if body itself looks like wrapper (has position/velocity/yaw)
        const b = this.body;
        if (b && typeof b.position === "function" && typeof b.velocity === "function") {
            this.bodyRef = b;
            return this.bodyRef;
        }

        // fallback: build wrapper from id
        const id = this.bodyId | 0;
        if (!id) return null;

        try {
            this.bodyRef = PHYS.ref(id);
            return this.bodyRef;
        } catch (_) {
            return null;
        }
    }

    warp(pos) {
        if (!pos) return;

        // 1) if Java handle has teleport()
        const h = this.body;
        if (h && typeof h.teleport === "function") {
            try { h.teleport(pos); } catch (e) { try { engine.log().error("[player] warp.teleport failed: " + e); } catch (_) {} }
            return;
        }

        // 2) canonical: wrapper
        const b = this._ensureBodyRef();
        if (b && typeof b.warp === "function") {
            try { b.warp(pos); } catch (e) { try { engine.log().error("[player] warp.body.warp failed: " + e); } catch (_) {} }
            return;
        }

        // 3) last resort: PHYS.warp by id
        const bodyId = this.bodyId | 0;
        if (!bodyId) return;
        try { PHYS.warp(bodyId, pos); } catch (e) {
            try { engine.log().error("[player] warp.PHYS.warp failed: " + e); } catch (_) {}
        }
    }

    destroy() {
        // ✅ canonical remove
        const b = this._ensureBodyRef();
        if (b && typeof b.remove === "function") {
            try { b.remove(); } catch (_) {}
        } else if ((this.bodyId | 0) > 0) {
            // fallback to PHYS.remove (still not engine.physics)
            try { PHYS.remove(this.bodyId | 0); } catch (_) {}
        }

        this.bodyRef = null;
        this.body = null;
        this.surface = null;
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;
    }
}

class PlayerEntityFactory {
    constructor(player) {
        this.player = player || null;
    }

    create(cfg) {
        if (cfg == null && this.player) cfg = (this.player.cfg && this.player.cfg.spawn) || {};
        cfg = cfg || {};

        const h = ENT.create({
            name: cfg.name ?? "player",
            surface: {
                type: "capsule",
                name: cfg.surfaceName,
                radius: cfg.radius ?? 0.35,
                height: cfg.height ?? 1.8,
                pos: cfg.pos ?? { x: 0, y: 3, z: 0 },
                attach: true,
                physics: { mass: cfg.mass ?? 80, lockRotation: true }
            },
            body: {
                mass: cfg.mass ?? 80.0,
                friction: cfg.friction ?? 0.9,
                restitution: cfg.restitution ?? 0.0,
                damping: cfg.damping ?? { linear: 0.15, angular: 0.95 },
                lockRotation: true,
                collider: {
                    type: "capsule",
                    radius: cfg.radius,
                    height: cfg.height
                }
            },
            components: {
                Player: (ctx) => ({
                    entityId: ctx.entityId,
                    surfaceId: ctx.surfaceId,
                    bodyId: ctx.bodyId
                })
            },
            debug: true
        });

        const e = new PlayerEntity();
        e.entityId = h.entityId | 0;
        e.surface = h.surface;
        e.body = h.body;
        e.surfaceId = h.surfaceId | 0;
        e.bodyId = h.bodyId | 0;

        // ✅ сразу даём удобный wrapper
        try { if ((e.bodyId | 0) > 0) e.bodyRef = PHYS.ref(e.bodyId | 0); } catch (_) {}

        return e;
    }
}

module.exports = { PlayerEntity, PlayerEntityFactory };