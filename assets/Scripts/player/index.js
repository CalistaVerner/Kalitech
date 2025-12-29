// Author: Calista Verner
"use strict";

/**
 * Player system entrypoint.
 * index.js == Player (Facade / Aggregate Root)
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

        this.view = { yaw: 0, pitch: 0, type: "third" };

        // ✅ добавили vx/vy/vz, чтобы системы не читали velocity повторно
        this.pose = { x: 0, y: 0, z: 0, grounded: false, speed: 0, fallSpeed: 0, vx: 0, vy: 0, vz: 0 };

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

        this.body = null;

        this.dom = new PlayerDomain(this);

        this.factory  = new PlayerEntityFactory(this);
        this.movement = new PlayerController(this);
        this.camera   = new PlayerCamera(this);
        this.ui       = new PlayerUI(this);
        this.events   = new PlayerEvents(this);
    }

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

        const from = { x: px, y: py + 0.05, z: pz };
        const to   = { x: px, y: py - groundRay, z: pz };

        let hit = null;
        try { hit = b.raycast({ from, to }); }
        catch (_) { hit = null; }

        if (!hit || !hit.hit) return false;

        const n = hit.normal || null;
        const ny = n ? (+((typeof n.y === "function") ? n.y() : n.y) || 1) : 1;
        if (ny < maxSlopeDot) return false;

        const dist = +hit.distance || 9999;
        return dist <= (groundRay - groundEps);
    }

    _readSpeedAndFallSpeed() {
        const b = this.body;
        if (!b) return { speed: 0, fallSpeed: 0, vx: 0, vy: 0, vz: 0 };

        try {
            const v = b.velocity();
            const x = +((typeof v.x === "function") ? v.x() : v.x) || 0;
            const y = +((typeof v.y === "function") ? v.y() : v.y) || 0;
            const z = +((typeof v.z === "function") ? v.z() : v.z) || 0;

            const speed = Math.hypot(x, y, z);
            const fallSpeed = (y < 0) ? (-y) : 0;
            return { speed, fallSpeed, vx: x, vy: y, vz: z };
        } catch (_) {}

        return { speed: 0, fallSpeed: 0, vx: 0, vy: 0, vz: 0 };
    }

    init() {
        if (this.alive) return;

        this.cfg = {
            character: { radius: 0.35, height: 1.80, mass: 80.0, eyeHeight: 1.65 },
            spawn: { pos: { x: 129, y: 3, z: -300 }, radius: 0.35, height: 1.80, mass: 80.0 },
            movement: { groundRay: 1.2, groundEps: 0.08, maxSlopeDot: 0.55 },
            camera: { type: "third", debug: { enabled: true, everyFrames: 60 } },
            ui: { crosshair: { size: 22, color: { r: 0.2, g: 1.0, b: 0.4, a: 1.0 } } },
            events: { enabled: true, throttleMs: 250 }
        };

        this.ui.create();

        this.entity = this.factory.create(this.cfg.spawn);
        this.entityId  = this.entity.entityId | 0;
        this.surfaceId = this.entity.surfaceId | 0;
        this.bodyId    = this.entity.bodyId | 0;

        try { this.body = (this.bodyId | 0) ? PHYS.ref(this.bodyId | 0) : null; }
        catch (_) { this.body = null; }

        this.dom.syncIdsFromPlayer();

        this.movement.bind();

        this.camera.enableGameplayMouseGrab(true);
        try { this.camera.attach(); } catch (_) {}

        this.events.reset();
        this.events.onSpawn();

        try {
            this.ctx.state().set("player", { alive: true, entityId: this.entityId, surfaceId: this.surfaceId, bodyId: this.bodyId });
        } catch (_) {}

        this.alive = true;
        LOG.info("[player] init entity=" + (this.entityId | 0) + " bodyId=" + (this.bodyId | 0));
    }

    update(tpf) {
        if (!this.alive) return;

        let snap = null;
        try { snap = INP.consumeSnapshot(); } catch (_) {}

        this.dom.frame.tpf = tpf;
        this.dom.frame.snap = snap;
        this.dom.syncIdsFromPlayer();

        let needReattach = false;

        if (!this.body && (this.bodyId | 0) > 0) needReattach = true;

        if (this.body && typeof this.body.id === "function") {
            try { if ((this.body.id() | 0) !== (this.bodyId | 0)) needReattach = true; } catch (_) {}
        }

        if (needReattach && (this.bodyId | 0) > 0) {
            try { this.body = PHYS.ref(this.bodyId | 0); } catch (_) { this.body = null; }
            try { this.camera.attach(); } catch (_) {}
        }

        const grounded = this._calcGroundedForEvents();
        const motion = this._readSpeedAndFallSpeed();

        this.dom.pose.grounded = grounded;
        this.dom.pose.speed = motion.speed;
        this.dom.pose.fallSpeed = motion.fallSpeed;
        this.dom.pose.vx = motion.vx;
        this.dom.pose.vy = motion.vy;
        this.dom.pose.vz = motion.vz;

        if (snap) {
            snap.grounded = grounded;
            snap.speed = motion.speed;
            snap.vx = motion.vx;
            snap.vy = motion.vy;
            snap.vz = motion.vz;
        }

        this.camera.update(tpf, snap);
        if (this.camera.syncDomain) this.camera.syncDomain(this.dom);

        this.movement.update(tpf, snap);

        if (snap && this.dom && this.dom.input) {
            snap.run = !!this.dom.input.run;
            snap.jump = !!this.dom.input.jump;
        }

        this.events.onState({
            grounded,
            bodyId: this.bodyId | 0,
            jump: (this.dom && this.dom.input) ? !!this.dom.input.jump : false,
            fallSpeed: motion.fallSpeed
        });

        INP.endFrame();
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

module.exports._getPlayer = function () { return _player; };
module.exports.Player = Player;