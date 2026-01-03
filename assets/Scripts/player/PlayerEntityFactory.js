// FILE: Scripts/player/PlayerEntityFactory.js
// Author: Calista Verner
"use strict";

const U = require("./util.js");

function _num(v, fb) {
    const n = +v;
    return Number.isFinite(n) ? n : fb;
}

/**
 * SIMPLE CONTRACT (no миллион фоллбэков):
 * - Модель игрока берём ТОЛЬКО из:
 *    1) cfg.model
 *    2) player._model (fluent builder)
 * - Модель должна иметь API: model.setVisible(boolean)
 *   (если нет — мы логируем warnOnce и просто храним handle)
 */
function _pickPlayerModel(cfg, player) {
    if (cfg && cfg.model != null) return cfg.model;
    if (player && player._model != null) return player._model;
    return null;
}

function _idOf(h) {
    if (h == null) return 0;
    if (typeof h === "number") return h | 0;
    // без магии: только прямое поле id (если есть)
    try {
        if (typeof h.id === "number") return h.id | 0;
    } catch (_) {}
    return 0;
}

class PlayerEntity {
    constructor() {
        this.entityId = 0;

        this.surface = null;
        this.body = null;

        this.surfaceId = 0;
        this.bodyId = 0;

        this.bodyRef = null; // PHYS.ref(bodyId) or native handle with velocity/position

        // NEW: player model handle
        this.model = null;
        this.modelId = 0;

        // Simple view flag (camera/orchestrator will toggle)
        this.hideModelInFirstPerson = true;
    }

    getModel() { return this.model; }

    setModel(modelHandle) {
        this.model = modelHandle || null;
        this.modelId = _idOf(this.model);
        return this;
    }

    setModelVisible(visible) {
        if (!this.model) return false;

        const fn = this.model.setVisible;
        if (typeof fn === "function") {
            try {
                fn.call(this.model, !!visible);
                return true;
            } catch (e) {
                U.warnOnce("player_model_setVisible_throw", "[player] model.setVisible(..) threw: " + U.errStr(e));
                return false;
            }
        }

        // никаких больше фоллбэков — просто предупреждаем один раз
        U.warnOnce("player_model_setVisible_missing", "[player] model has no setVisible(boolean). Provide model with setVisible API.");
        return false;
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
        // physics destroy
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

        this.model = null;
        this.modelId = 0;

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
        const mass   = _num(cfg.mass, 80.0);

        const friction    = (cfg.friction != null) ? cfg.friction : 0.9;
        const restitution = (cfg.restitution != null) ? cfg.restitution : 0.0;
        const damping     = cfg.damping ?? { linear: 0.15, angular: 0.95 };

        const posMode = (cfg.posMode != null) ? String(cfg.posMode) : "feet";
        const pos = cfg.pos ?? { x: 0, y: 3, z: 0 };

        // NEW: simple model pick
        this.modelHandle = _pickPlayerModel(cfg, this.player);

        // NEW: simple flag
        const hideInFirstPerson = (cfg.hideModelInFirstPerson !== undefined) ? !!cfg.hideModelInFirstPerson : true;

        // Spawn QoL
        const snapToGround = (cfg.snapToGround !== undefined) ? !!cfg.snapToGround : true;
        const snapRay = _num(cfg.snapRay, 64.0);
        const snapUp  = _num(cfg.snapUp, 2.0);
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
                    capsule: { radius, height, mass },
                    view: { hideModelInFirstPerson: hideInFirstPerson }
                })
            },
            debug: true
        });

        const e = new PlayerEntity();
        e.entityId  = h.entityId | 0;
        e.surface   = h.surface;
        e.body      = h.body;
        e.surfaceId = (h.surfaceId | 0) || _idOf(h.surface);
        e.bodyId    = (h.bodyId | 0)    || _idOf(h.body);

        if ((e.bodyId | 0) > 0) e.bodyRef = PHYS.ref(e.bodyId | 0);

        // NEW: attach model is NOT factory job anymore (no unknown host APIs).
        // We just store it so camera/orchestrator can hide/show it.
        e.hideModelInFirstPerson = hideInFirstPerson;
        if (this.modelHandle) e.setModel(this.modelHandle);

        // Ground snap AFTER creation (old behavior)
        if (snapToGround && posMode === "feet" && e.bodyRef && typeof e.bodyRef.raycast === "function") {
            const px = _num(pos.x, 0), pz = _num(pos.z, 0);
            const feetY = _num(pos.y, 3);
            const from = { x: px, y: feetY + snapUp, z: pz };
            const to   = { x: px, y: feetY - snapRay, z: pz };

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
