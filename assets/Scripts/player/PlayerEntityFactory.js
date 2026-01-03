// FILE: Scripts/player/PlayerEntityFactory.js
// Author: Calista Verner
"use strict";

function _num(v, fb) {
    v = +v;
    return Number.isFinite(v) ? v : fb;
}

/**
 * SIMPLE CONTRACT (AAA):
 * - Модель игрока берём ТОЛЬКО из:
 *    1) cfg.model
 *    2) player.model
 *    3) player._model (legacy)
 * - Модель ДОЛЖНА иметь: model.setVisible(boolean)
 *   (если нет — мы НОРМАЛИЗУЕМ через engine.surface().setVisible(surfaceId,bool)
 *   и получаем объект с setVisible().
 */
function _pickPlayerModel(cfg, player) {
    if (cfg && cfg.model != null) return cfg.model;
    if (player) {
        if (player.model != null) return player.model;
        if (player._model != null) return player._model;
    }
    return null;
}

function _idOf(h) {
    if (h == null) return 0;
    if (typeof h === "number") return h | 0;
    if (typeof h.id === "number") return h.id | 0;
    if (typeof h.surfaceId === "number") return h.surfaceId | 0;
    return 0;
}

// Make sure model ALWAYS satisfies: setVisible(boolean)
function _ensureModelVisibleApi(handle, fallbackSurfaceId) {
    if (!handle) return null;

    // already satisfies contract
    if (typeof handle.setVisible === "function") return handle;

    const sid =
        (typeof handle.surfaceId === "number" && handle.surfaceId > 0) ? (handle.surfaceId | 0) :
            (typeof handle.id === "number" && handle.id > 0) ? (handle.id | 0) :
                (fallbackSurfaceId | 0);

    if (!sid) {
        throw new Error("[player] model has no surfaceId/id; cannot build setVisible API");
    }

    const surfApi = engine.surface && engine.surface();
    if (!surfApi) {
        throw new Error("[player] engine.surface() missing (required for model visibility)");
    }
    if (typeof surfApi.handle !== "function") {
        throw new Error("[player] engine.surface().handle(id) missing (required for model visibility)");
    }
    if (typeof surfApi.setVisible !== "function") {
        throw new Error("[player] engine.surface().setVisible(handle,bool) missing (required for model visibility)");
    }

    // resolve Java SurfaceHandle once (no per-frame conversions)
    const h = surfApi.handle(sid);

    // Minimal wrapper: only what CameraOrchestrator needs.
    return {
        setVisible(v) { surfApi.setVisible(h, !!v); },
        _raw: handle,
        id: sid,
        surfaceId: sid
    };
}


class PlayerEntity {
    constructor() {
        this.entityId = 0;

        this.surface = null;
        this.body = null;

        this.surfaceId = 0;
        this.bodyId = 0;

        this.bodyRef = null;

        this.model = null;
        this.modelId = 0;

        this.hideModelInFirstPerson = true;
    }

    getModel() { return this.model; }

    setModel(modelHandle) {
        this.model = modelHandle || null;
        this.modelId = _idOf(this.model);
        return this;
    }

    setModelVisible(visible) {
        const m = this.model;
        if (!m) return false;

        if (typeof m.setVisible !== "function") {
            throw new Error("[player] model must implement setVisible(boolean)");
        }

        m.setVisible(!!visible);
        return true;
    }

    _ensureBodyRef() {
        if (this.bodyRef) return this.bodyRef;

        const b = this.body;
        if (b && typeof b.position === "function" && typeof b.velocity === "function") {
            this.bodyRef = b;
            return b;
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
            h.teleport(pos);
            return;
        }

        const b = this._ensureBodyRef();
        if (b && typeof b.warp === "function") {
            b.warp(pos);
            return;
        }

        const id = this.bodyId | 0;
        if (!id) return;

        PHYS.warp(id, pos);
    }

    destroy() {
        const b = this._ensureBodyRef();
        if (b && typeof b.remove === "function") {
            b.remove();
        } else {
            const id = this.bodyId | 0;
            if (id) PHYS.remove(id);
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

        const radius = _num(cfg.radius, 0.35);
        const height = _num(cfg.height, 1.80);
        const mass   = _num(cfg.mass, 80.0);

        const friction    = (cfg.friction != null) ? cfg.friction : 0.9;
        const restitution = (cfg.restitution != null) ? cfg.restitution : 0.0;
        const damping     = (cfg.damping != null) ? cfg.damping : { linear: 0.15, angular: 0.95 };

        const posMode = (cfg.posMode != null) ? String(cfg.posMode) : "feet";
        const pos = (cfg.pos != null) ? cfg.pos : { x: 0, y: 3, z: 0 };

        const pickedModel = _pickPlayerModel(cfg, this.player);
        const hideInFirstPerson = (cfg.hideModelInFirstPerson !== undefined) ? !!cfg.hideModelInFirstPerson : true;

        const snapToGround = (cfg.snapToGround !== undefined) ? !!cfg.snapToGround : true;
        const snapRay = _num(cfg.snapRay, 64.0);
        const snapUp  = _num(cfg.snapUp, 2.0);
        const snapPad = _num(cfg.snapPad, 0.02);

        const h = ENT.create({
            name: (cfg.name != null) ? cfg.name : "player",
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
        e.entityId = h.entityId | 0;
        e.surface = h.surface;
        e.body = h.body;

        e.surfaceId = (h.surfaceId | 0) || _idOf(h.surface);
        e.bodyId    = (h.bodyId | 0)    || _idOf(h.body);

        if (e.bodyId) e.bodyRef = PHYS.ref(e.bodyId);

        e.hideModelInFirstPerson = hideInFirstPerson;

        // ALWAYS assign a model handle for visibility control:
        // - pickedModel if provided
        // - else the player's own surface
        // AND normalize to have setVisible(boolean) via SurfaceApi if needed.
        const chosenModel = pickedModel || h.surface;
        e.setModel(_ensureModelVisibleApi(chosenModel, e.surfaceId));

        // Ground snap after creation
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
                            " bodyId=" + e.bodyId
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