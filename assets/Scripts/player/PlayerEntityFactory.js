"use strict";

function _num(v, fb) {
    v = +v;
    return Number.isFinite(v) ? v : fb;
}

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

function _ensureModelVisibleApi(handle, fallbackSurfaceId, engineApi) {
    if (!handle) return null;
    if (typeof handle.setVisible === "function") return handle;

    const sid =
        (typeof handle.surfaceId === "number" && handle.surfaceId > 0) ? (handle.surfaceId | 0) :
            (typeof handle.id === "number" && handle.id > 0) ? (handle.id | 0) :
                (fallbackSurfaceId | 0);

    if (!sid) throw new Error("[player] model has no surfaceId/id; cannot build setVisible API");

    const E = engineApi;
    if (!E) throw new Error("[player] engine api is required for model visibility");

    const surfApi = E.surface && E.surface();
    if (!surfApi) throw new Error("[player] engine.surface() missing");
    if (typeof surfApi.handle !== "function") throw new Error("[player] engine.surface().handle(id) missing");
    if (typeof surfApi.setVisible !== "function") throw new Error("[player] engine.surface().setVisible(handle,bool) missing");

    const h = surfApi.handle(sid);

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

    destroy(physics) {
        const b = this.bodyRef || (physics && this.bodyId ? physics.ref(this.bodyId) : null);
        if (b && typeof b.remove === "function") b.remove();
        else if (physics && this.bodyId) physics.remove(this.bodyId);

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

        const E = this.player && this.player.d ? this.player.d.engine : null;
        const PHYS = this.player && this.player.d ? this.player.d.physics : null;
        if (!E) throw new Error("[player] engine not ready (call player.init() first)");
        if (!PHYS || typeof PHYS.ref !== "function") throw new Error("[player] physics.ref required");

        const radius = _num(cfg.radius, 0.35);
        const height = _num(cfg.height, 1.80);
        const mass = _num(cfg.mass, 80.0);

        const friction = (cfg.friction != null) ? cfg.friction : 0.9;
        const restitution = (cfg.restitution != null) ? cfg.restitution : 0.0;
        const damping = (cfg.damping != null) ? cfg.damping : {linear: 0.15, angular: 0.95};

        const pos = (cfg.pos != null) ? cfg.pos : { x: 0, y: 3, z: 0 };

        const pickedModel = _pickPlayerModel(cfg, this.player);
        const hideInFirstPerson = (cfg.hideModelInFirstPerson !== undefined) ? !!cfg.hideModelInFirstPerson : true;

        const h = ENT.create({
            name: (cfg.name != null) ? cfg.name : "player",
            surface: {
                type: "box",
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
                lockRotation: false,
                collider: { type: "box", radius, height }
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
            debug: false
        });

        const e = new PlayerEntity();
        e.entityId = h.entityId | 0;
        e.surface = h.surface;
        e.body = h.body;

        e.surfaceId = (h.surfaceId | 0) || _idOf(h.surface);
        e.bodyId = (h.bodyId | 0) || _idOf(h.body);

        e.bodyRef = e.bodyId ? PHYS.ref(e.bodyId) : null;
        e.hideModelInFirstPerson = hideInFirstPerson;

        const chosenModel = pickedModel || h.surface;
        e.setModel(_ensureModelVisibleApi(chosenModel, e.surfaceId, E));

        return e;
    }
}

module.exports = { PlayerEntity, PlayerEntityFactory };