"use strict";

const U = require("../util.js");

function normalize3_into(x, y, z, out) {
    const l2 = x * x + y * y + z * z;
    if (l2 < 1e-12) { out.x = 0; out.y = 0; out.z = 1; return out; }
    const inv = 1.0 / Math.sqrt(l2);
    out.x = x * inv; out.y = y * inv; out.z = z * inv;
    return out;
}
function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }

const DEFAULT_CFG = Object.freeze({
    enabled: true,
    model: "Models/sharp-boulder-layered.obj",
    scale: 0.5,
    mass: 600.0,
    lockRotation: false,
    materialId: "unshaded.grass",
    spawnOffset: 0.0,
    speed: 20.0,
    invertPitch: false,
    debug: {logShots: false},

    // event topics (override if you want)
    events: {
        fire: "game.shoot.fire",
        hit: "game.shoot.hit"
    }
});

function mergeCfg(src) {
    src = (src && typeof src === "object") ? src : Object.create(null);
    const b = DEFAULT_CFG;
    const d = (src.debug && typeof src.debug === "object") ? src.debug : Object.create(null);
    const e = (src.events && typeof src.events === "object") ? src.events : Object.create(null);

    return {
        enabled: (src.enabled !== undefined) ? !!src.enabled : b.enabled,
        model: (src.model !== undefined) ? String(src.model) : b.model,
        scale: (src.scale !== undefined) ? U.num(src.scale, b.scale) : b.scale,
        mass: (src.mass !== undefined) ? U.num(src.mass, b.mass) : b.mass,
        lockRotation: (src.lockRotation !== undefined) ? !!src.lockRotation : b.lockRotation,
        materialId: (src.materialId !== undefined) ? String(src.materialId) : b.materialId,
        spawnOffset: (src.spawnOffset !== undefined) ? U.num(src.spawnOffset, b.spawnOffset) : b.spawnOffset,
        speed: (src.speed !== undefined) ? U.num(src.speed, b.speed) : b.speed,
        invertPitch: (src.invertPitch !== undefined) ? !!src.invertPitch : b.invertPitch,
        debug: {logShots: (d.logShots !== undefined) ? !!d.logShots : !!(b.debug && b.debug.logShots)},
        events: {
            fire: (e.fire !== undefined) ? String(e.fire) : b.events.fire,
            hit: (e.hit !== undefined) ? String(e.hit) : b.events.hit
        }
    };
}

function idOfSurfaceHandle(g) {
    // максимально терпимо к разным хэндлам
    if (!g) return 0;
    if (typeof g.surfaceId === "number") return g.surfaceId | 0;
    if (typeof g.id === "number") return g.id | 0;
    if (typeof g.id === "function") return (g.id() | 0) || 0;
    if (typeof g.getId === "function") return (g.getId() | 0) || 0;
    if (typeof g.handle === "object" && g.handle && typeof g.handle.id === "number") return g.handle.id | 0;
    return 0;
}

function idOfBodyHandle(g) {
    // если у хэндла есть доступ к physics body id — отлично, иначе 0
    if (!g) return 0;
    if (typeof g.bodyId === "number") return g.bodyId | 0;
    if (typeof g.physicsBodyId === "number") return g.physicsBodyId | 0;
    if (typeof g.physicsId === "number") return g.physicsId | 0;
    if (typeof g.getBodyId === "function") return (g.getBodyId() | 0) || 0;
    return 0;
}

class ShootSystem {
    constructor(player, rootCfg) {
        rootCfg = rootCfg || Object.create(null);
        this.player = player;
        this.cfg = mergeCfg(rootCfg.shoot);
        this._shotId = 0;

        this._dir = { x: 0, y: 0, z: 1 };
        this._origin = { x: 0, y: 0, z: 0 };
        this._spawn = { x: 0, y: 0, z: 0 };
        this._vel = { x: 0, y: 0, z: 0 };

        // active shots registry
        this._shotsBySurface = Object.create(null); // surfaceId -> shotMeta
        this._shotsByBody = Object.create(null);    // bodyId -> surfaceId (optional)

        // collision subscription
        this._ev = null;
        this._subCollision = 0;
        this._bound = false;
    }

    configure(rootCfg) {
        rootCfg = rootCfg || Object.create(null);
        if (rootCfg.shoot) this.cfg = mergeCfg(Object.assign({}, this.cfg, rootCfg.shoot));
        return this;
    }

    _ensureBound() {
        if (this._bound) return;
        this._bound = true;

        const ev = this.player.ctx.engine.api().bus();
        this._ev = ev;

        // если Events API отсутствует — просто работаем без событий
        if (!ev || typeof ev.on !== "function") return;

        // ловим столкновения физики (PhysicsApiImpl уже эмитит эти топики)
        this._subCollision = ev.on("engine.physics.collision.begin", (payload) => {
            try {
                this._onCollisionBegin(payload);
            } catch (_) {
            }
        });
    }

    _emit(topic, payload) {
        const ev = this.player.ctx.engine.api().bus();
        if (!ev || typeof ev.emit !== "function") return;
        ev.emit(topic, payload);
    }

    _dirFromYawPitch_into(yaw, pitch, outDir) {
        const c = this.cfg;
        yaw = U.num(yaw, 0);
        pitch = U.num(pitch, 0);

        const LIM = (Math.PI * 0.5) - 1e-4;
        pitch = clamp(pitch, -LIM, LIM);
        if (c.invertPitch) pitch = -pitch;

        const sy = Math.sin(yaw), cy = Math.cos(yaw);
        const sp = Math.sin(pitch), cp = Math.cos(pitch);
        return normalize3_into(sy * cp, sp, cy * cp, outDir);
    }

    _readOrigin_into(frame, outOrigin) {
        outOrigin.x = U.num(frame.pose.x, 0);
        outOrigin.y = U.num(frame.pose.y, 0) + (frame.character ? U.num(frame.character.eyeHeight, 1.55) : 1.55);
        outOrigin.z = U.num(frame.pose.z, 0);
        return outOrigin;
    }

    _registerShot(g, meta) {
        const surfaceId = idOfSurfaceHandle(g);
        const bodyId = idOfBodyHandle(g);

        if (surfaceId > 0) this._shotsBySurface[surfaceId] = meta;
        if (bodyId > 0 && surfaceId > 0) this._shotsByBody[bodyId] = surfaceId;

        meta.surfaceId = surfaceId | 0;
        meta.bodyId = bodyId | 0;
        return meta;
    }

    _fire(frame, bodyId) {
        const c = this.cfg;
        if (!c.enabled || !bodyId) return;

        this._ensureBound();

        const yaw = frame.view ? frame.view.yaw : 0;
        const pitch = frame.view ? frame.view.pitch : 0;

        this._readOrigin_into(frame, this._origin);
        this._dirFromYawPitch_into(yaw, pitch, this._dir);

        const off = c.spawnOffset;
        this._spawn.x = this._origin.x + this._dir.x * off;
        this._spawn.y = this._origin.y + this._dir.y * off;
        this._spawn.z = this._origin.z + this._dir.z * off;

        const shotIndex = (++this._shotId) | 0;
        const name = "shot-" + shotIndex;

        const g = MSH.loadModel(c.model, {
            scale: c.scale,
            name,
            pos: [this._spawn.x, this._spawn.y, this._spawn.z],
            physics: {
                mass: c.mass,
                lockRotation: c.lockRotation,
                collider: { type: "dynamicMesh", halfExtents: [1.2, 0.6, 2.4] }
            }
        });

        if (g && typeof g.setMaterial === "function") g.setMaterial(MAT.getMaterial(c.materialId));

        const speed = c.speed;
        this._vel.x = this._dir.x * speed;
        this._vel.y = this._dir.y * speed;
        this._vel.z = this._dir.z * speed;
        if (g && typeof g.velocity === "function") g.velocity(this._vel);

        //SND.create({ soundFile: "Sounds/hit.ogg", volume: 1.0, pitch: 1.0, looping: false }).play();

        const meta = this._registerShot(g, {
            shotIndex,
            name,
            spawn: {x: this._spawn.x, y: this._spawn.y, z: this._spawn.z},
            dir: {x: this._dir.x, y: this._dir.y, z: this._dir.z},
            vel: {x: this._vel.x, y: this._vel.y, z: this._vel.z},
            yaw: +yaw || 0,
            pitch: +pitch || 0,
            ownerBodyId: bodyId | 0
        });

        // ✅ EVENT: shot fired
        this._emit(c.events.fire, {
            shotIndex: meta.shotIndex,
            name: meta.name,
            surfaceId: meta.surfaceId,
            bodyId: meta.bodyId,
            ownerBodyId: meta.ownerBodyId,
            spawn: meta.spawn,
            dir: meta.dir,
            vel: meta.vel,
            yaw: meta.yaw,
            pitch: meta.pitch
        });

        if (c.debug && c.debug.logShots && LOG && LOG.info) {
            LOG.info("[shoot] " + name + " sid=" + meta.surfaceId + " bid=" + meta.bodyId + " yaw=" + yaw + " pitch=" + pitch);
        }
    }

    _onCollisionBegin(payload) {
        const c = this.cfg;
        if (!c.enabled) return;
        if (!payload || !payload.a || !payload.b) return;

        const a = payload.a;
        const b = payload.b;

        const aSid = (a.surfaceId | 0) || 0;
        const bSid = (b.surfaceId | 0) || 0;

        let shotSid = 0;
        let otherSid = 0;

        if (aSid > 0 && this._shotsBySurface[aSid]) {
            shotSid = aSid;
            otherSid = bSid;
        } else if (bSid > 0 && this._shotsBySurface[bSid]) {
            shotSid = bSid;
            otherSid = aSid;
        } else return;

        const shotMeta = this._shotsBySurface[shotSid];
        if (!shotMeta) return;

        // ✅ EVENT: shot hit something
        this._emit(c.events.hit, {
            shotIndex: shotMeta.shotIndex,
            name: shotMeta.name,
            shot: {surfaceId: shotSid, bodyId: shotMeta.bodyId | 0},
            other: {surfaceId: otherSid | 0, bodyId: (shotSid === aSid ? (b.bodyId | 0) : (a.bodyId | 0))},
            step: payload.step | 0,
            dt: +payload.dt || 0,
            contact: payload.contact || null
        });

        // обычно после первого контакта снаряд "умирает":
        // если хочешь — раскомментируй удаление из реестра (и/или remove сущности)
        delete this._shotsBySurface[shotSid];
        if (shotMeta.bodyId > 0) delete this._shotsByBody[shotMeta.bodyId];
    }

    update(frame, bodyId) {
        if (!this.cfg.enabled) return;
        if (!frame || !frame.input) return;

        // подписка на collision может быть нужна даже до первого выстрела
        this._ensureBound();

        if (!frame.input.lmbJustPressed) return;
        this._fire(frame, bodyId | 0);
    }

    destroy() {
        // отписка от collision
        const ev = this.player.ctx.engine.api().bus();
        if (ev && typeof ev.off === "function" && this._subCollision) {
            try {
                ev.off(this._subCollision | 0);
            } catch (_) {
            }
        }

        this._subCollision = 0;
        this._ev = null;
        this._bound = false;

        this._shotsBySurface = Object.create(null);
        this._shotsByBody = Object.create(null);
    }
}

module.exports = ShootSystem;
