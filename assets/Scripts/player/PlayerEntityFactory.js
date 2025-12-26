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
        this.body = null;

        this.surfaceId = 0;
        this.bodyId = 0;
    }

    warp(pos) {
        if (!pos) return;

        const h = this.body;
        if (h && typeof h.teleport === "function") {
            try { h.teleport(pos); } catch (e) { try { engine.log().error("[player] warp.teleport failed: " + e); } catch (_) {} }
            return;
        }

        // fallback: old api (may be no-op in your engine)
        const bodyId = this.bodyId | 0;
        if (!bodyId) return;
        try { engine.physics().position(bodyId, pos); } catch (e) {
            try { engine.log().error("[player] warp.physics.position failed: " + e); } catch (_) {}
        }
    }


    destroy() {
        try { if (this.bodyId > 0) engine.physics().remove(this.bodyId); } catch (_) {}
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
        // If cfg not provided — take from player
        if (cfg == null && this.player) cfg = (this.player.cfg && this.player.cfg.spawn) || {};
        cfg = cfg || {};

        const e = new PlayerEntity();

        // 1) entity
        e.entityId = engine.entity().create(cfg.name || "player");

        // 2) surface
        e.surface = engine.mesh().capsule({
            name: cfg.surfaceName || "player.body",
            radius: cfg.radius != null ? cfg.radius : 0.35,
            height: cfg.height != null ? cfg.height : 1.8,
            pos: cfg.pos || { x: 0, y: 3, z: 0 },
            attach: true
        });
        e.surfaceId = idOf(e.surface, "surface");
        try { engine.surface().attach(e.surface, e.entityId); } catch (_) {}

        // 3) physics body
        e.body = engine.physics().body({
            surface: e.surface,
            mass: cfg.mass != null ? cfg.mass : 80.0,
            friction: cfg.friction != null ? cfg.friction : 0.9,
            restitution: cfg.restitution != null ? cfg.restitution : 0.0,
            damping: cfg.damping || { linear: 0.15, angular: 0.95 },
            lockRotation: true,
            collider: {
                type: "capsule",
                radius: cfg.radius != null ? cfg.radius : 0.35,
                height: cfg.height != null ? cfg.height : 1.8
            }
        });
        e.bodyId = idOf(e.body, "body");

        // ECS component once
        engine.entity().setComponent(e.entityId, "Player", {
            entityId: e.entityId,
            surfaceId: e.surfaceId,
            bodyId: e.bodyId
        });

        engine.log().info(
            "[player] handles entity=" + (e.entityId | 0) +
            " surfaceId=" + (e.surfaceId | 0) +
            " bodyId=" + (e.bodyId | 0)
        );

        return e;
    }
}

module.exports = { PlayerEntity, PlayerEntityFactory };