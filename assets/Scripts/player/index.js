// Author: Calista Verner
"use strict";

/**
 * Player system entrypoint.
 * index.js == Player (Facade / Aggregate Root)
 *
 * OOP contract:
 *  - Every player subsystem gets `player` in constructor and can access:
 *      player.ctx, player.cfg, player.entity/body ids, and helper methods.
 *
 * Updated:
 *  - Uses PHYS.ref(bodyId) wrapper once (obj.velocity(), obj.position(), ...)
 *  - Keeps ids for compatibility/debug, but gameplay code should use player.body
 */

const PlayerController = require("./PlayerController.js");
const PlayerCamera = require("./PlayerCamera.js");
const PlayerUI = require("./PlayerUI.js");
const PlayerEvents = require("./PlayerEvents.js");
const { PlayerEntityFactory } = require("./PlayerEntityFactory.js");

// -------------------- domain --------------------

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
        this.pose = { x: 0, y: 0, z: 0, grounded: false, speed: 0, fallSpeed: 0 };

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

        // ✅ PHYS wrapper bound to bodyId (use this everywhere in gameplay code)
        this.body = null;

        // ✅ single source of truth
        this.dom = new PlayerDomain(this);

        // subsystems
        this.factory  = new PlayerEntityFactory(this);
        this.movement = new PlayerController(this);
        this.camera   = new PlayerCamera(this);
        this.ui       = new PlayerUI(this);
        this.events   = new PlayerEvents(this);
    }

    // -------- helpers --------

    getCfg(path, fb) {
        const parts = String(path || "").split(".");
        let o = this.cfg;
        for (let i = 0; i < parts.length; i++) {
            if (!o) return fb;
            o = o[parts[i]];
        }
        return (o !== undefined) ? o : fb;
    }

    _calcGroundedForEvents() {
        const b = this.body;
        if (!b) return false;

        const p = b.position();
        if (!p) return false;

        const px = +((typeof p.x === "function") ? p.x() : p.x) || 0;
        const py = +((typeof p.y === "function") ? p.y() : p.y) || 0;
        const pz = +((typeof p.z === "function") ? p.z() : p.z) || 0;

        const m = this.cfg.movement || {};
        const groundRay   = (m.groundRay   != null) ? +m.groundRay   : 1.2;
        const groundEps   = (m.groundEps   != null) ? +m.groundEps   : 0.08;
        const maxSlopeDot = (m.maxSlopeDot != null) ? +m.maxSlopeDot : 0.55;

        // маленький подъём старта, чтобы не ловить “почти ноль” на полу/ступеньке
        const from = { x: px, y: py + 0.05, z: pz };
        const to   = { x: px, y: py - groundRay, z: pz };

        let hit = null;
        try {
            // wrapper просто прокидывает на PHYS.raycast (world query)
            // позже сюда можно добавить ignoreBody: this.bodyId (когда будет в Java)
            hit = b.raycast({ from, to });
        } catch (_) {
            hit = null;
        }

        if (!hit || !hit.hit) return false;

        const n = hit.normal || null;
        const ny = n ? (+((typeof n.y === "function") ? n.y() : n.y) || 1) : 1;
        if (ny < maxSlopeDot) return false;

        const dist = +hit.distance || 9999;
        return dist <= (groundRay - groundEps);
    }

    _readSpeedAndFallSpeed() {
        const b = this.body;
        if (!b) return { speed: 0, fallSpeed: 0 };

        try {
            const v = b.velocity(); // ✅ no bodyId passing
            const x = +((typeof v.x === "function") ? v.x() : v.x) || 0;
            const y = +((typeof v.y === "function") ? v.y() : v.y) || 0;
            const z = +((typeof v.z === "function") ? v.z() : v.z) || 0;

            const speed = Math.hypot(x, y, z);
            const fallSpeed = (y < 0) ? (-y) : 0; // positive when falling down
            return { speed, fallSpeed };
        } catch (_) {}

        return { speed: 0, fallSpeed: 0 };
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

        // ✅ bind physics wrapper once
        try {
            this.body = (this.bodyId | 0) ? PHYS.ref(this.bodyId | 0) : null;
        } catch (_) {
            this.body = null;
        }

        // publish ids into domain immediately
        this.dom.syncIdsFromPlayer();

        // ---- bind movement ----
        // movement can now read player.body (preferred) or player.bodyId (legacy)
        this.movement.bind();

        // ---- camera ----
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
        LOG.info("[player] init entity=" + (this.entityId | 0) + " bodyId=" + (this.bodyId | 0));
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

        // safety: if bodyId changed (respawn/recreate), rebuild wrapper
        if ((!this.body && (this.bodyId | 0) > 0) || (this.body && (this.body.id && this.body.id() !== (this.bodyId | 0)))) {
            try { this.body = PHYS.ref(this.bodyId | 0); } catch (_) { this.body = null; }
        }

        // --- derive grounded/speed BEFORE camera ---
        const grounded = this._calcGroundedForEvents();
        const motion = this._readSpeedAndFallSpeed();

        this.dom.pose.grounded = grounded;
        this.dom.pose.speed = motion.speed;
        this.dom.pose.fallSpeed = motion.fallSpeed;

        // enrich snapshot for camera dynamics (safe, optional)
        if (snap) {
            snap.grounded = grounded;
            snap.speed = motion.speed;
        }

        // 1) camera updates first (so view is authoritative for this frame)
        this.camera.update(tpf, snap);
        if (this.camera.syncDomain) this.camera.syncDomain(this.dom);

        // 2) player logic consumes dom.view
        this.movement.update(tpf, snap);

        // NOW we know the authoritative input state (run/jump) from InputRouter via dom.input
        if (snap && this.dom && this.dom.input) {
            snap.run = !!this.dom.input.run;
            snap.jump = !!this.dom.input.jump;
        }

        // events (derived state) + camera hooks (jump/land)
        this.events.onState({
            grounded,
            bodyId: this.bodyId | 0,
            // later можно передать и this.body (wrapper), если событиям нужно скорость/позиция
            jump: (this.dom && this.dom.input) ? !!this.dom.input.jump : false,
            fallSpeed: motion.fallSpeed
        });

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
        this.body = null;

        try { this.ctx.state().remove("player"); } catch (_) {}

        this.alive = false;
        LOG.info("[player] destroy");
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