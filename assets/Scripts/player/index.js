// FILE: Scripts/player/index.js
// Author: Calista Verner
"use strict";

/**
 * Player system entrypoint.
 * index.js == Player (Facade / Aggregate Root)
 *
 * OOP contract:
 *  - Every player subsystem gets `player` in constructor and can access:
 *      player.ctx, player.cfg, player.entity/body ids, and helper methods.
 */

const PlayerController = require("./PlayerController.js");
const PlayerCamera = require("./PlayerCamera.js");
const PlayerUI = require("./PlayerUI.js");
const PlayerEvents = require("./PlayerEvents.js");
const { PlayerEntityFactory } = require("./PlayerEntityFactory.js");

// -------------------- domain --------------------

/**
 * PlayerDomain is the single source of truth for player state for the current frame.
 * All subsystems must read from here instead of guessing from engine.* APIs.
 */
class PlayerDomain {
    constructor(player) {
        this.player = player;

        this.ids = { entityId: 0, surfaceId: 0, bodyId: 0 };

        this.input = {
            ax: 0,
            az: 0,
            run: false,
            jump: false,
            lmbDown: false,
            lmbJustPressed: false
        };

        // ✅ one view for everyone (Movement/Shoot/etc)
        this.view = { yaw: 0, pitch: 0, type: "third" };

        // optional derived state (events, UI, etc.)
        this.pose = { x: 0, y: 0, z: 0, grounded: false };

        // frame bookkeeping
        this.frame = { tpf: 0, snap: null };
    }

    syncIdsFromPlayer() {
        const p = this.player;
        this.ids.entityId = p.entityId | 0;
        this.ids.surfaceId = p.surfaceId | 0;
        this.ids.bodyId = p.bodyId | 0;
        return this;
    }
}

class Player {
    constructor(ctx, cfg) {
        this.ctx = ctx;
        this.cfg = cfg || {};

        this.alive = false;

        this.entity = null;
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        // ✅ single source of truth for player state (shared for all subsystems)
        this.dom = new PlayerDomain(this);

        // subsystems (all receive `this`)
        this.factory  = new PlayerEntityFactory(this);
        this.movement = new PlayerController(this);
        this.camera   = new PlayerCamera(this);
        this.ui       = new PlayerUI(this);
        this.events   = new PlayerEvents(this);
    }

    // -------- helpers --------

    getCfg(path, fb) {
        // Simple deep-get: player.getCfg("movement.speed", 6)
        const parts = String(path || "").split(".");
        let o = this.cfg;
        for (let i = 0; i < parts.length; i++) {
            if (!o) return fb;
            o = o[parts[i]];
        }
        return (o !== undefined) ? o : fb;
    }

    _calcGroundedForEvents() {
        if (!this.bodyId) return false;

        let p = null;
        try { p = engine.physics().position(this.bodyId); } catch (_) {}
        if (!p) return false;

        const px = +((typeof p.x === "function") ? p.x() : p.x) || 0;
        const py = +((typeof p.y === "function") ? p.y() : p.y) || 0;
        const pz = +((typeof p.z === "function") ? p.z() : p.z) || 0;

        const m = this.cfg.movement || {};
        const groundRay   = (m.groundRay   != null) ? +m.groundRay   : 1.2;
        const groundEps   = (m.groundEps   != null) ? +m.groundEps   : 0.08;
        const maxSlopeDot = (m.maxSlopeDot != null) ? +m.maxSlopeDot : 0.55;

        const from = { x: px, y: py, z: pz };
        const to   = { x: px, y: py - groundRay, z: pz };

        let hit = null;
        try { hit = engine.physics().raycast({ from, to }); } catch (_) {}
        if (!hit || !hit.hit) return false;

        const n = hit.normal || null;
        const ny = n ? (+((typeof n.y === "function") ? n.y() : n.y) || 1) : 1;
        if (ny < maxSlopeDot) return false;

        const dist = +hit.distance || 9999;
        return dist <= (groundRay - groundEps);
    }

    // -------- lifecycle --------

    init() {
        if (this.alive) return;

        // ---- config (one place) ----
        this.cfg = {
            spawn: { pos: { x: 0, y: 3, z: 0 } },

            movement: { speed: 6.0, runSpeed: 10.0 },

            camera: { type: "third", debug: { enabled: true, everyFrames: 60 } },

            ui: { crosshair: { size: 22, color: { r: 0.2, g: 1.0, b: 0.4, a: 1.0 } } },

            events: { enabled: true, throttleMs: 250 }
        };

        // ---- UI ----
        this.ui.create();

        // ---- spawn entity ----
        this.entity = this.factory.create(this.cfg.spawn);
        this.entityId  = this.entity.entityId | 0;
        this.surfaceId = this.entity.surfaceId | 0;
        this.bodyId    = this.entity.bodyId | 0;

        // publish ids into domain immediately
        this.dom.syncIdsFromPlayer();

        // ---- bind movement ----
        this.movement.bind(); // takes bodyId from player

        // ---- camera ----
        //this.camera.attach().configure();
        this.camera.enableGameplayMouseGrab(true);

        // ---- events ----
        this.events.reset();
        this.events.onSpawn();

        // ---- publish state ----
        try {
            this.ctx.state().set("player", {
                alive: true,
                entityId: this.entityId,
                surfaceId: this.surfaceId,
                bodyId: this.bodyId
            });
        } catch (_) {}

        this.alive = true;
        engine.log().info("[player] init entity=" + (this.entityId | 0) + " bodyId=" + (this.bodyId | 0));
    }

    update(tpf) {
        if (!this.alive) return;

        // ONE snapshot per frame
        let snap = null;
        try { snap = engine.input().consumeSnapshot(); } catch (_) {}

        // frame sync
        this.dom.frame.tpf = tpf;
        this.dom.frame.snap = snap;
        this.dom.syncIdsFromPlayer();

        // 1) camera updates first (so view is authoritative for this frame)
        this.camera.update(tpf, snap);
        if (this.camera.syncDomain) this.camera.syncDomain(this.dom);

        // 2) player logic (movement/shoot/etc) consumes dom.view
        this.movement.update(tpf, snap);

        // events (derived state)
        const grounded = this._calcGroundedForEvents();
        this.dom.pose.grounded = grounded;
        this.events.onState({ grounded, bodyId: this.bodyId | 0 });

        // end frame
        try { if (engine.input().endFrame) engine.input().endFrame(); } catch (_) {}
    }

    destroy() {
        if (!this.alive) return;

        try { this.camera.destroy(); } catch (_) {}
        try { this.ui.destroy(); } catch (_) {}
        try { if (this.entity) this.entity.destroy(); } catch (_) {}

        this.entity = null;
        this.entityId = this.surfaceId = this.bodyId = 0;

        try { this.ctx.state().remove("player"); } catch (_) {}

        this.alive = false;
        engine.log().info("[player] destroy");
    }
}

// -------------------- system hooks --------------------

let _player = null;

module.exports.init = function init(ctx) {
    if (_player && _player.alive) return;
    _player = new Player(ctx, null);
    _player.init();
};

module.exports.update = function update(ctx, tpf) {
    if (!_player) return;
    _player.update(tpf);
};

module.exports.destroy = function destroy(ctx) {
    if (!_player) return;
    _player.destroy();
    _player = null;
};

// optional: expose for debugging / other scripts
module.exports._getPlayer = function () { return _player; };
module.exports.Player = Player;