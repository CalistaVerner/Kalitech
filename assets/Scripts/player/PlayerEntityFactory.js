"use strict";

function num(v, fb) {
    v = +v;
    return Number.isFinite(v) ? v : fb;
}

class PlayerEntityFactory {
    constructor(player) {
        this.player = player || null;
    }

    create(spawnCfg) {
        const player = this.player;
        if (!player || !player.d) throw new Error("[player] factory requires player with domains");

        const cfg = spawnCfg || (player.cfg && player.cfg.spawn) || Object.create(null);

        const radius = num(cfg.radius, 0.35);
        const height = num(cfg.height, 1.80);
        const mass = num(cfg.mass, 80.0);

        const pos = cfg.pos || {x: 0, y: 3, z: 0};
        const hideInFirstPerson = (cfg.hideModelInFirstPerson !== undefined) ? !!cfg.hideModelInFirstPerson : true;

        if (!globalThis.ENT || typeof globalThis.ENT.create !== "function") {
            throw new Error("[player] ENT.create(cfg) required (engine Entity module)");
        }

        const h = ENT.create({
            name: (cfg.name != null) ? cfg.name : "player",
            surface: {
                type: "box",
                radius,
                height,
                pos,
                attach: true,
                physics: { mass, lockRotation: true }
            },
            body: {
                mass,
                lockRotation: false,
                collider: {type: "box", radius, height},
                friction: (cfg.friction != null) ? cfg.friction : 0.9,
                restitution: (cfg.restitution != null) ? cfg.restitution : 0.0,
                damping: (cfg.damping != null) ? cfg.damping : {linear: 0.15, angular: 0.95}
            },
            components: {
                Player: (ctx) => ({
                    entityId: ctx.entityId,
                    surfaceId: ctx.surfaceId,
                    bodyId: ctx.bodyId,
                    capsule: { radius, height, mass },
                    view: { hideModelInFirstPerson: hideInFirstPerson }
                })
            }
        });

        if (!h || !h.core) throw new Error("[player] ENT.create() must return {core}");
        if ((h.core.bodyId | 0) <= 0) throw new Error("[player] ENT.create() returned invalid core.bodyId");
        if (!h.core.bodyAccess) throw new Error("[player] ENT.create() must provide core.bodyAccess (engine-filled)");

        return h;
    }
}

module.exports = {PlayerEntityFactory};