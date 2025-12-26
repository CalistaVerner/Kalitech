// FILE: Scripts/game/Player.js
// Author: Calista Verner
"use strict";

const CrosshairWidget  = require("./CrosshairWidget.js");
const PlayerController = require("./PlayerController.js");
const camModes         = require("../Camera/CameraOrchestrator.js");

function safeJson(v) { try { return JSON.stringify(v); } catch (_) { return String(v); } }

/**
 * Extract numeric id from Kalitech host objects.
 * Supports:
 *  - number
 *  - { id: number }
 *  - { id: function()->number }
 *  - getId(), bodyId(), surfaceId(), handle()
 *  - valueOf() -> number
 */
function idOf(h, kind /* "body"|"surface" */) {
    if (h == null) return 0;

    // already an id
    if (typeof h === "number") return h | 0;

    // attempt valueOf (some host objects implement it)
    try {
        if (typeof h.valueOf === "function") {
            const v = h.valueOf();
            if (typeof v === "number" && isFinite(v)) return v | 0;
        }
    } catch (_) {}

    // direct numeric fields
    try {
        if (typeof h.id === "number") return h.id | 0;
        if (typeof h.bodyId === "number") return h.bodyId | 0;
        if (typeof h.surfaceId === "number") return h.surfaceId | 0;
    } catch (_) {}

    // function getters (THIS is the key fix for your current log)
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

class Player {
    constructor(ctx) {
        this.ctx = ctx;

        this.KEY_PLAYER = "game:player";
        this.KEY_UI     = "game:player:ui";

        this.crosshair  = null;
        this.controller = new PlayerController();

        this.alive = true;

        // handles
        this.entityId = 0;
        this.surface  = null;
        this.body     = null;

        // stable numeric ids
        this.surfaceId = 0;
        this.bodyId    = 0;

        this._cameraReady = false;
        this._camType = "third";
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

        // 1) entity
        this.entityId = engine.entity().create(cfg.name || "player");

        // 2) surface
        this.surface = engine.mesh().capsule({
            name: cfg.surfaceName || "player.body",
            radius: cfg.radius != null ? cfg.radius : 0.35,
            height: cfg.height != null ? cfg.height : 1.8,
            pos: cfg.pos || { x: 0, y: 3, z: 0 },
            attach: true
        });
        this.surfaceId = idOf(this.surface, "surface");

        try { engine.surface().attach(this.surface, this.entityId); } catch (_) {}

        // 3) physics body
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
        this.bodyId = idOf(this.body, "body");

        // IMPORTANT DIAGNOSTIC
        engine.log().info(
            `[player] handles entity=${this.entityId} surfaceId=${this.surfaceId} bodyId=${this.bodyId} ` +
            `surfaceType=${typeof this.surface} bodyType=${typeof this.body}`
        );

        // Store ECS component once
        engine.entity().setComponent(this.entityId, "Player", {
            entityId: this.entityId,
            surfaceId: this.surfaceId,
            bodyId: this.bodyId
        });

        // Bind controller to numeric ids
        this.controller.bind({
            entityId: this.entityId,
            surfaceId: this.surfaceId,
            bodyId: this.bodyId
        });

        // ---------------- CAMERA (JS orchestrator) ----------------
        const camCfg  = cfg.camera || {};
        const camType = camCfg.type || "third";
        this._camType = camType;

        camModes.configure({
            debug: camCfg.debug || { enabled: true, everyFrames: 60 }, // включи пока чиним

            keys: camCfg.keys || { free: "F1", first: "F2", third: "F3", top: "F4" },

            look: camCfg.look || {
                sensitivity: 0.002,
                smoothing: 0.12,
                pitchLimit: Math.PI * 0.49,
                invertX: false,
                invertY: false
            },

            free:  camCfg.free  || { speed: 90, accel: 18, drag: 6.5 },
            first: camCfg.first || { offset: { x: 0, y: 1.65, z: 0 } },
            third: camCfg.third || { distance: 3.4, height: 1.55, side: 0.25, zoomSpeed: 1.0 },
            top:   camCfg.top   || { height: 18, panSpeed: 14, zoomSpeed: 2, pitch: -Math.PI * 0.49 }
        });

        camModes.setType(camType);
        camModes.attachTo(this.bodyId);

        // Hide cursor + "grab mouse" for gameplay (VERY important for deltas)
        try {
            if (engine.input().debug) engine.input().debug(true);
            if (engine.input().grabMouse) engine.input().grabMouse(true);
            else if (engine.input().cursorVisible) engine.input().cursorVisible(false);
        } catch (_) {}

        this._cameraReady = true;
        engine.log().info(`[player] camera attached type=${camType} bodyId=${this.bodyId}`);
        // ---------------------------------------------------------

        // Persist state
        st.set(this.KEY_UI, { crosshair: true });
        st.set(this.KEY_PLAYER, {
            alive: true,
            entityId: this.entityId,
            surfaceId: this.surfaceId,
            bodyId: this.bodyId,
            camera: { type: camType }
        });

        engine.log().info(`[player] init entity=${this.entityId} surfaceId=${this.surfaceId} bodyId=${this.bodyId}`);
        return this;
    }

    update(tpf) {
        if (!this.alive) return;

        this.controller.update(tpf);

        if (this._cameraReady) {
            // keep attached (hot reload safe)
            camModes.attachTo(this.bodyId);
            camModes.update(tpf);
        }
    }

    warp(pos) { this.controller.warp(pos); }
    warpXYZ(x, y, z) { this.controller.warpXYZ(x, y, z); }

    destroy() {
        const st = this.ctx.state();

        if (this.crosshair) {
            this.crosshair.destroy();
            this.crosshair = null;
        }

        // release mouse + show cursor (editor-friendly)
        try {
            if (engine.input().grabMouse) engine.input().grabMouse(false);
            else if (engine.input().cursorVisible) engine.input().cursorVisible(true);
        } catch (_) {}

        // detach camera
        try {
            camModes.attachTo(0);
            camModes.setType("free");
        } catch (_) {}
        this._cameraReady = false;

        // remove physics body
        try { if (this.bodyId > 0) engine.physics().remove(this.bodyId); } catch (_) {}

        this.body = null;
        this.surface = null;
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        st.remove(this.KEY_UI);
        st.remove(this.KEY_PLAYER);

        engine.log().info("[player] destroy");
    }
}

module.exports = Player;