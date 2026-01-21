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

        if (!globalThis.ENT || typeof globalThis.ENT.create !== "function") {
            throw new Error("[player] ENT.create(cfg) required");
        }

        const e = ENT.create({
            name: (cfg.name != null) ? String(cfg.name) : "player",

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
                friction: num(cfg.friction, 0.9),
                restitution: num(cfg.restitution, 0.0),
                damping: (cfg.damping != null) ? cfg.damping : {linear: 0.15, angular: 0.95}

                // collider: оставь ТОЛЬКО если твой PhysicsApiImpl это реально поддерживает
                // collider: { type: "capsule", radius, height }
            },

            debug: !!cfg.debug
        });

        if (!e || typeof e.uuidString !== "function") {
            throw new Error("[player] ENT.create() must return EntityHandle");
        }

        const uuid = e.uuidString();
        if (!uuid) throw new Error("[player] ENT.create() returned empty uuid");

        if (!e.hasBody || !e.hasBody()) {
            throw new Error("[player] player must have physics body");
        }

        // Java хранит данные: пишем компоненты в ECS через Java API
        // Названия компонентов выбирай те, которые реально у тебя существуют на Java стороне.
        e.setComponent("Player", {
            capsule: {radius, height, mass}
        });

        // UI mirror (если нужно прямо сейчас)
        // e.hydrateCore();

        return e; // EntityHandle
    }
}

module.exports = {PlayerEntityFactory};