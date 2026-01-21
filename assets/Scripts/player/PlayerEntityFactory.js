"use strict";

function num(v, fb) {
    v = +v;
    return Number.isFinite(v) ? v : fb;
}

function v3(pos, fb) {
    const p = pos || fb || {x: 0, y: 3, z: 0};
    return {
        x: num(p.x, 0),
        y: num(p.y, 3),
        z: num(p.z, 0)
    };
}

function bool(v, fb) {
    return (v !== undefined) ? !!v : !!fb;
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

        const pos = v3(cfg.pos, {x: 0, y: 3, z: 0});
        //const hideInFirstPerson = bool(cfg.hideModelInFirstPerson, true);

        if (!globalThis.ENT || typeof globalThis.ENT.create !== "function") {
            throw new Error("[player] ENT.create(cfg) required (engine Entity module)");
        }

        // Single source of truth for physics: cfg.body only.
        // Surface must be purely visual/attachable, never "surface.physics" in player.
        const pack = ENT.create({
            name: (cfg.name != null) ? String(cfg.name) : "player",
            requireCore: true,

            surface: {
                type: "capsule",
                name: "player.surface",
                radius,
                height,
                pos,
                attach: true
            },

            body: {
                mass,
                lockRotation: false,
                friction: (cfg.friction != null) ? num(cfg.friction, 0.9) : 0.9,
                restitution: (cfg.restitution != null) ? num(cfg.restitution, 0.0) : 0.0,
                damping: (cfg.damping != null) ? cfg.damping : {linear: 0.15, angular: 0.95},

                collider: {
                    type: "capsule",
                    radius,
                    height
                }
            },

            components: {
                Player: (ctx) => ({
                    uuid: ctx.uuid,
                    surfaceId: ctx.surfaceId | 0,
                    bodyId: ctx.bodyId | 0,
                    capsule: { radius, height, mass },
                    //view: { hideModelInFirstPerson: hideModelInFirstPerson }
                })
            }
        });

        if (!pack || !pack.core) throw new Error("[player] ENT.create() must return {core}");
        const core = pack.core;

        if (typeof core.uuid !== "string" || !core.uuid) {
            throw new Error("[player] ENT.create() must return core.uuid (UUID-only)");
        }
        if ((core.bodyId | 0) <= 0) throw new Error("[player] ENT.create() returned invalid core.bodyId");
        if (!core.bodyAccess) throw new Error("[player] ENT.create() must provide core.bodyAccess (engine-filled)");

        // Optional: keep backward compat fields if older code expects them
        if (pack.handle && !pack.handle.core) pack.handle.core = core;

        return pack; // { core, handle, uuid, surfaceId, bodyId }
    }
}

module.exports = {PlayerEntityFactory};