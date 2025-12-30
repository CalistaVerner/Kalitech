// FILE: Scripts/player/PlayerEntityFactory.js
// Author: Calista Verner
"use strict";

const U = require("./util.js");

// Robust numeric id extraction for handle-ish objects.
// We don't silently swallow host exceptions: we log once and keep going.
function idOf(h, kind) {
    if (h == null) return 0;
    if (typeof h === "number") return h | 0;

    // valueOf() is common with Graal wrappers
    const vo = h && h.valueOf;
    if (typeof vo === "function") {
        try {
            const v = vo.call(h);
            if (typeof v === "number" && Number.isFinite(v)) return v | 0;
        } catch (e) {
            U.warnOnce("idOf_valueOf_" + kind, "[player] idOf(" + kind + "): valueOf() threw: " + U.errStr(e));
        }
    }

    // direct fields
    try {
        if (typeof h.id === "number") return h.id | 0;
        if (typeof h.bodyId === "number") return h.bodyId | 0;
        if (typeof h.surfaceId === "number") return h.surfaceId | 0;
    } catch (e) {
        U.warnOnce("idOf_fields_" + kind, "[player] idOf(" + kind + "): reading fields threw: " + U.errStr(e));
    }

    // method fallbacks
    const fnNames =
        kind === "body"
            ? ["id", "getId", "bodyId", "getBodyId", "handle"]
            : ["id", "getId", "surfaceId", "getSurfaceId", "handle"];

    for (let i = 0; i < fnNames.length; i++) {
        const n = fnNames[i];
        const fn = h[n];
        if (typeof fn !== "function") continue;
        try {
            const v = fn.call(h);
            if (typeof v === "number" && Number.isFinite(v)) return v | 0;
        } catch (e) {
            U.warnOnce("idOf_fn_" + kind + "_" + n, "[player] idOf(" + kind + "): " + n + "() threw: " + U.errStr(e));
        }
    }

    return 0;
}

function _num(v, fb) {
    const n = +v;
    return Number.isFinite(n) ? n : fb;
}

class PlayerEntity {
    constructor() {
        this.entityId = 0;
        this.surface = null;
        this.body = null;

        this.surfaceId = 0;
        this.bodyId = 0;

        this.bodyRef = null; // PHYS.ref(bodyId) or native handle with velocity/position
    }

    _ensureBodyRef() {
        if (this.bodyRef) return this.bodyRef;

        const b = this.body;
        if (b && typeof b.position === "function" && typeof b.velocity === "function") {
            this.bodyRef = b;
            return this.bodyRef;
        }

        const id = this.bodyId | 0;
        if (!id) return null;

        this.bodyRef = PHYS.ref(id);
        return this.bodyRef;
    }

    warp(pos) {
        if (!pos) return;

        const h = this.body;
        if (h && typeof h.teleport === "function") {
            try { h.teleport(pos); }
            catch (e) { throw new Error("[player] warp.teleport failed: " + U.errStr(e)); }
            return;
        }

        const b = this._ensureBodyRef();
        if (b && typeof b.warp === "function") {
            try { b.warp(pos); }
            catch (e) { throw new Error("[player] warp.body.warp failed: " + U.errStr(e)); }
            return;
        }

        const bodyId = this.bodyId | 0;
        if (!bodyId) return;

        try { PHYS.warp(bodyId, pos); }
        catch (e) { throw new Error("[player] warp.PHYS.warp failed: " + U.errStr(e)); }
    }

    destroy() {
        const b = this._ensureBodyRef();
        if (b && typeof b.remove === "function") {
            try { b.remove(); }
            catch (e) { throw new Error("[player] body.remove failed: " + U.errStr(e)); }
        } else if ((this.bodyId | 0) > 0) {
            try { PHYS.remove(this.bodyId | 0); }
            catch (e) { throw new Error("[player] PHYS.remove failed: " + U.errStr(e)); }
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

        // --- human defaults ---
        const radius = _num(cfg.radius, 0.35);
        const height = _num(cfg.height, 1.80);
        const mass = _num(cfg.mass, 80.0);

        const friction = (cfg.friction != null) ? cfg.friction : 0.9;
        const restitution = (cfg.restitution != null) ? cfg.restitution : 0.0;
        const damping = cfg.damping ?? { linear: 0.15, angular: 0.95 };

        const posMode = (cfg.posMode != null) ? String(cfg.posMode) : "feet";
        const pos = cfg.pos ?? { x: 0, y: 3, z: 0 };

        // Spawn QoL (avoid "falling" spawn which breaks jump buffer)
        const snapToGround = (cfg.snapToGround !== undefined) ? !!cfg.snapToGround : true;
        const snapRay = _num(cfg.snapRay, 64.0);
        const snapUp = _num(cfg.snapUp, 2.0);
        const snapPad = _num(cfg.snapPad, 0.02);

        const h = ENT.create({
            name: cfg.name ?? "player",
            surface: {
                type: "capsule",
                name: cfg.surfaceName,
                radius,
                height,
                pos,
                attach: true,
                physics: { mass, lockRotation: true }
            },
            body: {
                mass,
                friction,
                restitution,
                damping,
                lockRotation: true,
                collider: { type: "capsule", radius, height }
            },
            components: {
                Player: (ctx) => ({
                    entityId: ctx.entityId,
                    surfaceId: ctx.surfaceId,
                    bodyId: ctx.bodyId,
                    capsule: { radius, height, mass }
                })
            },
            debug: true
        });

        const e = new PlayerEntity();
        e.entityId = h.entityId | 0;
        e.surface = h.surface;
        e.body = h.body;
        e.surfaceId = idOf(h.surface, "surface") || (h.surfaceId | 0);
        e.bodyId = idOf(h.body, "body") || (h.bodyId | 0);

        if ((e.bodyId | 0) > 0) e.bodyRef = PHYS.ref(e.bodyId | 0);

        // Ground snap AFTER creation (raycast in world; keep format stable)
        if (snapToGround && posMode === "feet" && e.bodyRef && typeof e.bodyRef.raycast === "function") {
            const px = _num(pos.x, 0), pz = _num(pos.z, 0);
            const feetY = _num(pos.y, 3);
            const from = { x: px, y: feetY + snapUp, z: pz };
            const to = { x: px, y: feetY - snapRay, z: pz };

            const hit = e.bodyRef.raycast({ from, to });
            if (hit && hit.hit) {
                const dist = _num(hit.distance, NaN);
                if (Number.isFinite(dist)) {
                    const hitY = (feetY + snapUp) - dist;
                    const snappedFeetY = hitY + snapPad;
                    const snappedCenterY = snappedFeetY + (height * 0.5 - radius);

                    e.warp({ x: px, y: snappedCenterY, z: pz });

                    if (LOG && LOG.info) {
                        LOG.info(
                            "[player] spawn snap feetY=" + feetY +
                            " -> hitY=" + hitY.toFixed(3) +
                            " centerY=" + snappedCenterY.toFixed(3) +
                            " bodyId=" + (e.bodyId | 0)
                        );
                    }
                }
            } else if (LOG && LOG.warn) {
                LOG.warn("[player] spawn snap missed ground (fromY=" + (feetY + snapUp) + " toY=" + (feetY - snapRay) + ")");
            }
        }

        return e;
    }
}

module.exports = { PlayerEntity, PlayerEntityFactory };
