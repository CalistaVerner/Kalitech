// Author: Calista Verner
"use strict";

const CrosshairWidget  = require("./CrosshairWidget.js");
const PlayerController = require("./PlayerController.js");

class Player {
    constructor(ctx) {
        this.ctx = ctx;

        this.KEY_PLAYER = "game:player";
        this.KEY_UI = "game:player:ui";

        this.crosshair = null;
        this.controller = new PlayerController();

        this.alive = true;

        // centralized ids/handles (single source of truth)
        this.entityId = 0;
        this.surface = null;
        this.body = null;
    }

    init(cfg) {
        cfg = cfg || {};
        const st = this.ctx.state();

        // UI
        this.crosshair = new CrosshairWidget(this.ctx).create({
            size: 22,
            thickness: 2,
            color: { r: 0.2, g: 1.0, b: 0.4, a: 1.0 }
        });

        // --- CENTRALIZED SPAWN HERE ---
        // 1) entity
        this.entityId = engine.entity().create(cfg.name || "player");

        // 2) visual surface (your MeshApi capsule)
        this.surface = engine.mesh().capsule({
            name: cfg.surfaceName || "player.body",
            radius: cfg.radius != null ? cfg.radius : 0.35,
            height: cfg.height != null ? cfg.height : 1.8,
            pos: cfg.pos || { x: 0, y: 3, z: 0 },
            attach: true
        });

        // attach surface to entity (if exists)
        try { engine.surface().attach(this.surface, this.entityId); } catch (_) {}

        // 3) physics body (THIS is what makes it a physical object)
        this.body = engine.physics().body({
            surface: this.surface,
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

        // Store ECS component once (optional for tools/other systems)
        engine.entity().setComponent(this.entityId, "Player", {
            entityId: this.entityId,
            surfaceId: this.surface.id,
            bodyId: this.body.id
        });

        // Bind controller to ids (NO guessing later)
        this.controller.bind({
            entityId: this.entityId,
            surfaceId: this.surface.id,
            bodyId: this.body.id
        });

        // Persist state (hot reload / debug)
        st.set(this.KEY_UI, { crosshair: true });
        st.set(this.KEY_PLAYER, {
            alive: true,
            entityId: this.entityId,
            surfaceId: this.surface.id,
            bodyId: this.body.id
        });

        engine.log().info(`[player] init entity=${this.entityId} surface=${this.surface.id} body=${this.body.id}`);
        return this;
    }

    update(tpf) {
        if (!this.alive) return;

        // controller drives physics each tick
        this.controller.update(tpf);

        // later: gameplay logic / shooting etc.
    }

    warp(pos) { this.controller.warp(pos); }
    warpXYZ(x, y, z) { this.controller.warpXYZ(x, y, z); }

    destroy() {
        const st = this.ctx.state();

        if (this.crosshair) {
            this.crosshair.destroy();
            this.crosshair = null;
        }

        // remove physics body (optional but clean)
        try { if (this.body) engine.physics().remove(this.body.id); } catch (_) {}

        this.body = null;
        this.surface = null;
        this.entityId = 0;

        st.remove(this.KEY_UI);
        st.remove(this.KEY_PLAYER);

        engine.log().info("[player] destroy");
    }
}

module.exports = Player;