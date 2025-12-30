// FILE: Scripts/player/index.js
// Author: Calista Verner
"use strict";

const U = require("./util.js");
const FrameContext = require("./FrameContext.js");
const CharacterConfig = require("./CharacterConfig.js");

const PlayerController = require("./PlayerController.js");
const PlayerCamera = require("./PlayerCamera.js");
const PlayerUI = require("./PlayerUI.js");
const PlayerEvents = require("./PlayerEvents.js");
const { PlayerEntityFactory } = require("./PlayerEntityFactory.js");

// -------------------- grounded via collisions (AAA fallback) --------------------
// Мы НЕ ломаем ваш FrameContext.probeGroundCapsule(), но добавляем “взрослый” резерв:
// если raycast почему-то не видит землю (как в логах), но коллизии есть — grounded будет жить.
function _getEventBusMaybe() {
    // Поддерживаем разные варианты глобального события/шины (у вас могло называться иначе).
    // Ничего не "try/catch-глушим" — если API нет, просто вернем null.
    const g = (typeof globalThis !== "undefined") ? globalThis : null;
    if (!g) return null;

    // Самые вероятные имена из вашего стиля builtins:
    const cand = [
        g.EVT,
        g.EVENTS,
        g.BUS,
        g.BUS_EVENTS,
        g.Events,
        g.events
    ];
    for (let i = 0; i < cand.length; i++) {
        const b = cand[i];
        if (b && typeof b.on === "function" && typeof b.off === "function") return b;
        if (b && typeof b.on === "function" && typeof b.removeListener === "function") return b;
    }
    return null;
}

class GroundContactTracker {
    constructor() {
        this._bus = null;

        this._playerBodyId = 0;
        this._contacts = new Set(); // bodyId set of current contacts
        this._enabled = false;

        this._onBegin = (e) => this._handleBegin(e);
        this._onEnd = (e) => this._handleEnd(e);
    }

    bind(playerBodyId) {
        this._playerBodyId = playerBodyId | 0;
        this._contacts.clear();

        const bus = _getEventBusMaybe();
        this._bus = bus;

        if (!bus) {
            if (LOG && LOG.warn) LOG.warn("[player] ground contact tracker: event bus not found (EVT/EVENTS). Using raycast-only grounded.");
            this._enabled = false;
            return;
        }

        // Поддержка двух сигнатур: off() или removeListener()
        const offFn = (typeof bus.off === "function") ? "off" : (typeof bus.removeListener === "function" ? "removeListener" : null);
        if (!offFn) {
            if (LOG && LOG.warn) LOG.warn("[player] ground contact tracker: bus has on() but no off/removeListener(). Using raycast-only grounded.");
            this._enabled = false;
            return;
        }

        // На всякий случай — переподписка без дублей
        try {
            bus[offFn]("engine.physics.collision.begin", this._onBegin);
            bus[offFn]("engine.physics.collision.end", this._onEnd);
        } catch (_) {}

        bus.on("engine.physics.collision.begin", this._onBegin);
        bus.on("engine.physics.collision.end", this._onEnd);

        this._enabled = true;

        if (LOG && LOG.info) LOG.info("[player] ground contact tracker: subscribed (playerBodyId=" + this._playerBodyId + ")");
    }

    unbind() {
        const bus = this._bus;
        if (bus) {
            const offFn = (typeof bus.off === "function") ? "off" : (typeof bus.removeListener === "function" ? "removeListener" : null);
            if (offFn) {
                try { bus[offFn]("engine.physics.collision.begin", this._onBegin); } catch (_) {}
                try { bus[offFn]("engine.physics.collision.end", this._onEnd); } catch (_) {}
            }
        }
        this._bus = null;
        this._playerBodyId = 0;
        this._contacts.clear();
        this._enabled = false;
    }

    _extractBodyId(x) {
        if (!x) return 0;
        // Java-side payload you emit: { a:{bodyId,surfaceId}, b:{...} }
        const bid = x.bodyId;
        if (typeof bid === "number") return bid | 0;
        if (typeof bid === "string") return (bid | 0) || 0;
        return 0;
    }

    _handleBegin(e) {
        if (!this._enabled) return;
        if (!e || typeof e !== "object") return;

        const a = e.a, b = e.b;
        const aId = this._extractBodyId(a);
        const bId = this._extractBodyId(b);
        const pId = this._playerBodyId;

        if (!pId) return;

        if (aId === pId && bId > 0) this._contacts.add(bId);
        else if (bId === pId && aId > 0) this._contacts.add(aId);
    }

    _handleEnd(e) {
        if (!this._enabled) return;
        if (!e || typeof e !== "object") return;

        const a = e.a, b = e.b;
        const aId = this._extractBodyId(a);
        const bId = this._extractBodyId(b);
        const pId = this._playerBodyId;

        if (!pId) return;

        if (aId === pId && bId > 0) this._contacts.delete(bId);
        else if (bId === pId && aId > 0) this._contacts.delete(aId);
    }

    hasContacts() {
        return this._contacts.size > 0;
    }
}

// -------------------- domain --------------------

class PlayerDomain {
    constructor(player) {
        this.player = player;
        this.ids = { entityId: 0, surfaceId: 0, bodyId: 0 };
        this.input = { ax: 0, az: 0, run: false, jump: false, lmbDown: false, lmbJustPressed: false };
        this.view = { yaw: 0, pitch: 0, type: "third" };
        this.pose = { x: 0, y: 0, z: 0, vx: 0, vy: 0, vz: 0, grounded: false, speed: 0, fallSpeed: 0 };
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

        // fluent builder state
        this._model = null;

        this.alive = false;

        this.entity = null;
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;
        this.body = null;

        this.dom = new PlayerDomain(this);
        this.frame = new FrameContext();

        this.characterCfg = new CharacterConfig();

        this.factory = new PlayerEntityFactory(this);
        this.movement = new PlayerController(this);
        this.camera = new PlayerCamera(this);
        this.ui = new PlayerUI(this);
        this.events = new PlayerEvents(this);

        // ✅ grounded tracking
        this._groundContacts = new GroundContactTracker();
        this._groundedLast = false;          // for diagnostics
        this._groundedSource = "none";       // "probe" | "contacts" | "both" | "none"

        this._dbg = { t: 0, every: 120 };
        this._dbgGround = { t: 0, every: 90, missedRaycasts: 0 };

        this._didSpawnSnap = false;
    }

    // -------------------- Fluent AAA API --------------------
    withConfig(cfg) {
        this.cfg = cfg || {};
        return this;
    }

    withModel(modelHandle) {
        this._model = modelHandle || null;
        return this;
    }

    withCamera(type) {
        if (!this.cfg) this.cfg = {};
        if (!this.cfg.camera) this.cfg.camera = {};
        this.cfg.camera.type = String(type || "third").toLowerCase();
        if (this.camera) this.camera.type = this.cfg.camera.type;
        return this;
    }

    create(ctx) {
        if (ctx) this.ctx = ctx;
        this.init();
        return this;
    }

    init() {
        if (this.alive) return;

        this.cfg = U.deepMerge({
            character: { radius: 0.35, height: 1.80, mass: 80.0, eyeHeight: 1.65 },
            spawn: { pos: { x: 129, y: 3, z: -300 }, radius: 0.35, height: 1.80, mass: 80.0 },
            camera: { type: "third" },
            ui: { crosshair: { size: 22, color: { r: 0.2, g: 1.0, b: 0.4, a: 1.0 } } },
            events: { enabled: true, throttleMs: 250 }
        }, this.cfg || {});

        this.characterCfg.loadFrom(this.cfg, this.movement.getMovementCfg());

        this.ui.create();

        this.entity = this.factory.create(this.cfg.spawn);
        this.entityId = this.entity.entityId | 0;
        this.surfaceId = this.entity.surfaceId | 0;
        this.bodyId = this.entity.bodyId | 0;

        const bid = this.bodyId | 0;
        if (!bid) {
            LOG.error("[player] init failed: bodyId=0 (entity factory/physics mismatch)");
            throw new Error("[player] bodyId is 0");
        }
        if (!PHYS || typeof PHYS.ref !== "function") {
            throw new Error("[player] PHYS.ref missing");
        }
        this.body = PHYS.ref(bid);
        if (!this.body) {
            LOG.error("[player] init failed: PHYS.ref(bodyId) returned null. bodyId=" + bid);
            throw new Error("[player] body wrapper missing");
        }

        // ✅ subscribe collision begin/end for grounded fallback
        this._groundContacts.bind(bid);

        this.dom.syncIdsFromPlayer();
        this.movement.bind();

        this.camera.enableGameplayMouseGrab(true);
        try {
            this.camera.attach(bid);
        } catch (e) {
            LOG.error("[player] camera.attach failed bodyId=" + bid + " err=" + (e && (e.stack || e.message) || String(e)));
            throw e;
        }

        this.events.reset();
        this.events.onSpawn();

        try {
            if (this.ctx && this.ctx.state) {
                this.ctx.state().set("player", { alive: true, entityId: this.entityId, surfaceId: this.surfaceId, bodyId: this.bodyId });
            }
        } catch (e) {
            LOG.warn("[player] ctx.state().set failed: " + (e && (e.message || e.stack) || String(e)));
        }

        this.alive = true;
        LOG.info("[player] init ok entity=" + (this.entityId | 0) + " bodyId=" + bid + " camera=" + (this.cfg && this.cfg.camera && this.cfg.camera.type ? this.cfg.camera.type : "third"));
    }

    _ensureBodyWrapper() {
        const id = this.bodyId | 0;
        if (!id) { this.body = null; return; }

        if (!this.body) {
            if (!PHYS || typeof PHYS.ref !== "function") {
                throw new Error("[player] PHYS.ref missing (cannot reacquire body wrapper)");
            }
            this.body = PHYS.ref(id);
            if (!this.body) throw new Error("[player] PHYS.ref returned null while reacquiring body wrapper; bodyId=" + id);
            this.camera.attach(id);
            this._groundContacts.bind(id);
            return;
        }

        if (typeof this.body.id === "function") {
            const wid = this.body.id() | 0;
            if (wid !== id) {
                if (!PHYS || typeof PHYS.ref !== "function") {
                    throw new Error("[player] PHYS.ref missing (cannot refresh mismatched wrapper)");
                }
                this.body = PHYS.ref(id);
                if (!this.body) throw new Error("[player] PHYS.ref returned null while refreshing body wrapper; bodyId=" + id);
                this.camera.attach(id);
                this._groundContacts.bind(id);
            }
        }
    }

    _trySpawnSnap(frame) {
        // Одноразовая попытка “сесть на землю” после старта:
        // важно, потому что у вас в логах: spawn snap missed ground (fromY=5 toY=-61)
        if (this._didSpawnSnap) return;
        this._didSpawnSnap = true;

        if (!PHYS || typeof PHYS.raycastEx !== "function") {
            if (LOG && LOG.warn) LOG.warn("[player] spawn snap skipped: PHYS.raycastEx missing");
            return;
        }
        if (!this.body || typeof this.body.position !== "function") return;

        const p = this.body.position();
        const px = U.vx(p, 0), py = U.vy(p, 0), pz = U.vz(p, 0);

        // Луч вниз под игроком (дальше, чем обычно, чтобы найти землю гарантированно)
        const from = { x: px, y: py + 2.0, z: pz };
        const to   = { x: px, y: py - 80.0, z: pz };

        const hit = PHYS.raycastEx({
            from,
            to,
            ignoreBodyId: this.bodyId | 0,
            staticOnly: true
        });

        const ok = hit && (hit.hit === true || hit.hasHit === true || (typeof hit.fraction === "number" && hit.fraction >= 0 && hit.fraction < 1));
        if (!ok) {
            if (LOG && LOG.warn) LOG.warn("[player] spawn snap: no hit (raycastEx). Check ground collider / physics add timing.");
            return;
        }

        // Уложим капсулу так, чтобы низ стоял на точке попадания
        const cc = this.characterCfg;
        const r = U.num(cc.radius, 0.35);
        const h = U.num(cc.height, 1.8);
        const centerToFoot = Math.max(0.001, (h * 0.5) - r);

        const hitY = U.num(hit.point && hit.point.y, py);
        const newY = hitY + centerToFoot + 0.01; // маленький зазор
        try {
            this.body.warp({ x: px, y: newY, z: pz });
            if (LOG && LOG.info) LOG.info("[player] spawn snap OK: y " + py.toFixed(3) + " -> " + newY.toFixed(3) + " (hitY=" + hitY.toFixed(3) + ")");
        } catch (e) {
            LOG.warn("[player] spawn snap warp failed: " + (e && (e.message || e.stack) || String(e)));
        }
    }

    _syncPose(frame) {
        const b = this.body;
        if (!b) return;

        if (typeof b.position !== "function") throw new Error("[player] body.position missing");
        const p = b.position();
        if (p) {
            frame.pose.x = U.vx(p, 0);
            frame.pose.y = U.vy(p, 0);
            frame.pose.z = U.vz(p, 0);
        }

        if (typeof b.velocity !== "function") throw new Error("[player] body.velocity missing");
        const v = b.velocity();
        const vx = U.vx(v, 0);
        const vy = U.vy(v, 0);
        const vz = U.vz(v, 0);
        frame.pose.vx = vx;
        frame.pose.vy = vy;
        frame.pose.vz = vz;
        frame.pose.speed = Math.hypot(vx, vy, vz);
        frame.pose.fallSpeed = vy < 0 ? -vy : 0;

        // ✅ Attempt snap once (helps if ground gets added slightly позже)
        this._trySpawnSnap(frame);

        // --- Primary: raycast ground probe ---
        let groundedProbe = false;
        try {
            groundedProbe = frame.probeGroundCapsule(b, this.characterCfg);
        } catch (e) {
            // Это важно видеть, не глушим
            const msg = (e && (e.message || e.stack) || String(e));
            if (LOG && LOG.error) LOG.error("[player] probeGroundCapsule failed: " + msg);
            groundedProbe = false;
        }

        // --- Fallback: collision contacts ---
        // Контакты сами по себе не гарантируют “стоим на полу”, но в вашем кейсе это лучше, чем вечное grounded=false.
        // Ограничим: считаем grounded по контактам, только если не летим вверх и не падаем быстро.
        const vyAbs = Math.abs(vy);
        const contacts = this._groundContacts.hasContacts();
        const groundedContacts = contacts && (vy <= 1.25) && (vyAbs <= 12.0);

        const grounded = groundedProbe || groundedContacts;
        frame.pose.grounded = grounded;

        // source tag for logs
        if (groundedProbe && groundedContacts) this._groundedSource = "both";
        else if (groundedProbe) this._groundedSource = "probe";
        else if (groundedContacts) this._groundedSource = "contacts";
        else this._groundedSource = "none";

        // Diagnostics for your конкретный баг: probe always misses (hasHit=false dist=9999)
        const dbgG = this._dbgGround;
        dbgG.t = (dbgG.t + 1) | 0;

        const g = frame.ground;
        const hasHit = !!(g && g.hasHit);
        if (!hasHit) dbgG.missedRaycasts = Math.min(1_000_000, (dbgG.missedRaycasts + 1) | 0);
        else dbgG.missedRaycasts = 0;

        if ((dbgG.t % dbgG.every) === 0 && LOG && LOG.info) {
            LOG.info(
                "[player] ground: grounded=" + !!grounded +
                " src=" + this._groundedSource +
                " probe(hasHit=" + !!hasHit +
                " dist=" + U.num(g ? g.distance : 9999, 9999).toFixed(4) +
                " ny=" + U.num(g ? g.ny : 1, 1).toFixed(3) +
                " steep=" + !!(g && g.steep) + ")" +
                " contacts=" + contacts +
                " vy=" + vy.toFixed(3) +
                " posY=" + U.num(frame.pose.y, 0).toFixed(3)
            );
        }

        if (dbgG.missedRaycasts === 300 && LOG && LOG.warn) {
            LOG.warn("[player] ground probe missed 300 frames подряд (raycast returns null). " +
                "Likely causes: ground collider too small/outside, physics add timing (batched add), or raycast filters. " +
                "Fallback 'contacts' will be used if available.");
        }
    }

    update(tpf) {
        if (!this.alive) return;

        if (!INP || typeof INP.consumeSnapshot !== "function") {
            throw new Error("[player] INP.consumeSnapshot missing");
        }
        let snap = null;
        try {
            snap = INP.consumeSnapshot();
        } catch (e) {
            const msg = (e && (e.message || e.stack) || String(e));
            LOG.warn("[player] INP.consumeSnapshot failed: " + msg);
            snap = null;
        }

        this.characterCfg.loadFrom(this.cfg, this.movement.getMovementCfg());

        this.frame.begin(this, tpf, snap);
        this.dom.syncIdsFromPlayer();

        this._ensureBodyWrapper();
        this._syncPose(this.frame);

        const fp = this.frame.pose;
        const dp = this.dom.pose;
        dp.x = fp.x; dp.y = fp.y; dp.z = fp.z;
        dp.vx = fp.vx; dp.vy = fp.vy; dp.vz = fp.vz;
        dp.speed = fp.speed;
        dp.fallSpeed = fp.fallSpeed;
        dp.grounded = fp.grounded;

        this.camera.update(this.frame);
        this.camera.syncDomain(this.dom, this.frame);

        this.movement.update(this.frame);

        this.events.onState({
            grounded: !!fp.grounded,
            bodyId: this.bodyId | 0,
            jump: !!(this.dom && this.dom.input && this.dom.input.jump),
            fallSpeed: fp.fallSpeed
        });

        if (INP.endFrame) INP.endFrame();
    }

    destroy() {
        if (!this.alive) return;

        // ✅ unsubscribe contacts
        this._groundContacts.unbind();

        try { this.camera.destroy(); } catch (e) { LOG.warn("[player] camera.destroy failed: " + (e && (e.message || e.stack) || String(e))); }
        try { this.ui.destroy(); } catch (e) { LOG.warn("[player] ui.destroy failed: " + (e && (e.message || e.stack) || String(e))); }
        try { if (this.entity) this.entity.destroy(); } catch (e) { LOG.warn("[player] entity.destroy failed: " + (e && (e.message || e.stack) || String(e))); }

        this.entity = null;
        this.entityId = this.surfaceId = this.bodyId = 0;
        this.body = null;

        try { if (this.ctx && this.ctx.state) this.ctx.state().remove("player"); }
        catch (e) { LOG.warn("[player] ctx.state().remove failed: " + (e && (e.message || e.stack) || String(e))); }

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