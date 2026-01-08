"use strict";

function num(v, fb) {
    v = +v;
    return Number.isFinite(v) ? v : fb;
}

function idOf(h) {
    if (h == null) return 0;
    if (typeof h === "number") return h | 0;
    if (typeof h.bodyId === "number") return h.bodyId | 0;
    if (typeof h.surfaceId === "number") return h.surfaceId | 0;
    if (typeof h.id === "number") return h.id | 0;
    if (typeof h.id === "function") return (h.id() | 0) || 0;
    if (typeof h.getId === "function") return (h.getId() | 0) || 0;
    return 0;
}

function pickHandle(root, names) {
    if (!root) return null;
    for (let i = 0; i < names.length; i++) {
        const v = root[names[i]];
        if (v && typeof v === "object") return v;
    }
    return null;
}

function ensureModelVisibleApi(handle, fallbackSurfaceId, engineApi) {
    if (!handle) return null;
    if (typeof handle.setVisible === "function") return handle;

    const sid =
        (typeof handle.surfaceId === "number" && handle.surfaceId > 0) ? (handle.surfaceId | 0) :
            (typeof handle.id === "number" && handle.id > 0) ? (handle.id | 0) :
                (fallbackSurfaceId | 0);

    if (!sid) throw new Error("[player] model has no surfaceId/id");

    const E = engineApi;
    const surfApi = E.surface && E.surface();
    if (!surfApi || typeof surfApi.handle !== "function" || typeof surfApi.setVisible !== "function") {
        throw new Error("[player] engine.surface().handle/setVisible missing");
    }

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

        this.model = null;
        this.modelId = 0;

        this.hideModelInFirstPerson = true;
    }

    getModel() { return this.model; }

    setModel(modelHandle) {
        this.model = modelHandle || null;
        this.modelId = idOf(this.model);
        return this;
    }

    destroy(physics) {
        const b = this.body;

        if (b && typeof b.remove === "function") {
            b.remove();
        } else if (physics && this.bodyId && typeof physics.remove === "function") {
            physics.remove(this.bodyId | 0);
        }

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
        cfg = cfg || (this.player && this.player.cfg && this.player.cfg.spawn) || {};

        const E = this.player && this.player.d ? this.player.d.engine : null;
        const PHYS = this.player && this.player.d ? this.player.d.physics : null;
        if (!E) throw new Error("[player] engine not ready");
        if (!PHYS) throw new Error("[player] physics api missing");

        const radius = num(cfg.radius, 0.35);
        const height = num(cfg.height, 1.80);
        const mass = num(cfg.mass, 80.0);

        const friction = (cfg.friction != null) ? cfg.friction : 0.9;
        const restitution = (cfg.restitution != null) ? cfg.restitution : 0.0;
        const damping = (cfg.damping != null) ? cfg.damping : {linear: 0.15, angular: 0.95};

        const pos = (cfg.pos != null) ? cfg.pos : { x: 0, y: 3, z: 0 };
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
        e.entityId = (h && h.entityId) ? (h.entityId | 0) : 0;

        // Surface/body handles могут называться по-разному или отсутствовать
        e.surface = pickHandle(h, ["surface", "surfaceHandle", "model", "spatial"]) || null;
        e.body = pickHandle(h, ["body", "bodyHandle", "physicsBody", "rigidBody", "bodyRef"]) || null;

        e.surfaceId = (h && h.surfaceId) ? (h.surfaceId | 0) : idOf(e.surface);
        e.bodyId = (h && h.bodyId) ? (h.bodyId | 0) : idOf(e.body);

        e.hideModelInFirstPerson = hideInFirstPerson;

        const chosenModel = cfg.model || e.surface;
        e.setModel(ensureModelVisibleApi(chosenModel, e.surfaceId, E));

        if (e.bodyId <= 0) throw new Error("[player] ENT.create() did not return bodyId");

        return e;
    }
}

module.exports = { PlayerEntity, PlayerEntityFactory };
