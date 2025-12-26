// FILE: Scripts/player/PlayerController.js
// Author: Calista Verner
"use strict";

// -------------------- tiny math utils (no allocations) --------------------
function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }
function len2(x, z) { return x * x + z * z; }
function norm2_into(x, z, out) {
    const l2 = x * x + z * z;
    if (l2 < 1e-12) { out.x = 0; out.z = 0; return out; }
    const inv = 1.0 / Math.sqrt(l2);
    out.x = x * inv;
    out.z = z * inv;
    return out;
}
function rotateByYaw_into(localX, localZ, yaw, out) {
    const s = Math.sin(yaw), c = Math.cos(yaw);
    out.x = localX * c + localZ * s;
    out.z = localZ * c - localX * s;
    return out;
}

function num(v, fb) {
    const n = +v;
    return Number.isFinite(n) ? n : (fb || 0);
}
function _readMember(v, key, fb) {
    if (!v) return fb || 0;
    try {
        const m = v[key];
        if (typeof m === "function") return num(m.call(v), fb);
        if (typeof m === "number") return m;
        if (typeof m === "string") return num(m, fb);
    } catch (_) {}
    return fb || 0;
}
function vx(v, fb) { return _readMember(v, "x", fb); }
function vy(v, fb) { return _readMember(v, "y", fb); }
function vz(v, fb) { return _readMember(v, "z", fb); }

// -------------------- Graal Java int[] helpers --------------------
function arrHas(arr, code) {
    if (!arr) return false;
    const n = arr.length | 0;
    for (let i = 0; i < n; i++) if ((arr[i] | 0) === (code | 0)) return true;
    return false;
}
function snapHas(snap, field, code) {
    if (!snap) return false;
    const arr = snap[field];
    return arr ? arrHas(arr, code | 0) : false;
}

// -------------------- defaults / config --------------------
const DEFAULT_KEYS = Object.freeze({
    forward: ["W", "Z", "UP"],
    back:    ["S", "DOWN"],
    left:    ["A", "Q", "LEFT"],
    right:   ["D", "RIGHT"],
    run:     ["SHIFT", "LSHIFT", "RSHIFT"],
    jump:    ["SPACE"]
});

const DEFAULT_DEBUG = Object.freeze({
    enabled: true,
    everyFrames: 60,
    logGround: false,
    logKeys: true
});

function cloneKeys(src) {
    const out = Object.create(null);
    const k = src || DEFAULT_KEYS;
    out.forward = (k.forward || DEFAULT_KEYS.forward).slice();
    out.back    = (k.back    || DEFAULT_KEYS.back).slice();
    out.left    = (k.left    || DEFAULT_KEYS.left).slice();
    out.right   = (k.right   || DEFAULT_KEYS.right).slice();
    out.run     = (k.run     || DEFAULT_KEYS.run).slice();
    out.jump    = (k.jump    || DEFAULT_KEYS.jump).slice();
    return out;
}

function mergeDebug(src) {
    const d = src || {};
    return {
        enabled: (d.enabled !== undefined) ? !!d.enabled : DEFAULT_DEBUG.enabled,
        everyFrames: (d.everyFrames !== undefined) ? (d.everyFrames | 0) : DEFAULT_DEBUG.everyFrames,
        logGround: (d.logGround !== undefined) ? !!d.logGround : DEFAULT_DEBUG.logGround,
        logKeys: (d.logKeys !== undefined) ? !!d.logKeys : DEFAULT_DEBUG.logKeys,
        _f: 0
    };
}

class PlayerController {
    /**
     * OOP: new PlayerController(player)
     * Legacy: new PlayerController(cfg)
     *
     * cfg:
     *  {
     *    enabled, speed, runSpeed, airControl, jumpImpulse,
     *    groundRay, groundEps, maxSlopeDot,
     *    keys: {forward/back/left/right/run/jump: [names...]},
     *    debug: {enabled, everyFrames, logGround, logKeys}
     *  }
     */
    constructor(playerOrCfg) {
        // OOP mode: new PlayerController(player)
        // Legacy mode: new PlayerController(cfg)
        const isPlayer = !!(playerOrCfg && typeof playerOrCfg === "object" && (playerOrCfg.cfg || playerOrCfg.getCfg || playerOrCfg.ctx));
        const player = isPlayer ? playerOrCfg : null;
        const cfg = isPlayer ? ((player && player.cfg && player.cfg.movement) || {}) : (playerOrCfg || {});

        this.player = player;

        const cfg2 = cfg || {};

        this.enabled = (cfg2.enabled !== undefined) ? !!cfg2.enabled : true;
        this.bodyId = 0;

        // movement
        this.speed      = (cfg2.speed      !== undefined) ? num(cfg2.speed, 6.0) : 6.0;
        this.runSpeed   = (cfg2.runSpeed   !== undefined) ? num(cfg2.runSpeed, 10.0) : 10.0;
        this.airControl = (cfg2.airControl !== undefined) ? clamp(num(cfg2.airControl, 0.35), 0, 1) : 0.35;
        this.jumpImpulse = (cfg2.jumpImpulse !== undefined) ? num(cfg2.jumpImpulse, 320.0) : 320.0;

        // ground check
        this.groundRay   = (cfg2.groundRay   !== undefined) ? num(cfg2.groundRay, 1.2) : 1.2;
        this.groundEps   = (cfg2.groundEps   !== undefined) ? num(cfg2.groundEps, 0.08) : 0.08;
        this.maxSlopeDot = (cfg2.maxSlopeDot !== undefined) ? num(cfg2.maxSlopeDot, 0.55) : 0.55;

        // keys (multi-layout)
        this.keys = cloneKeys(cfg2.keys);

        // debug
        this.debug = mergeDebug(cfg2.debug);

        // keyCode cache (name -> code)
        this._kc = Object.create(null);

        // compiled lists
        this._codes = null;

        // reuse objects to avoid GC
        this._tmpN = { x: 0, z: 0 };
        this._tmpDir = { x: 0, z: 0 };
        this._tmpFrom = { x: 0, y: 0, z: 0 };
        this._tmpTo   = { x: 0, y: 0, z: 0 };
        this._tmpVel  = { x: 0, y: 0, z: 0 };
        this._tmpJump = { x: 0, y: 0, z: 0 };
    }

    // ---------- config helpers ----------
    configure(cfg) {
        // lightweight runtime reconfigure (optional)
        cfg = cfg || {};
        if (cfg.enabled !== undefined) this.enabled = !!cfg.enabled;

        if (cfg.speed !== undefined) this.speed = num(cfg.speed, this.speed);
        if (cfg.runSpeed !== undefined) this.runSpeed = num(cfg.runSpeed, this.runSpeed);
        if (cfg.airControl !== undefined) this.airControl = clamp(num(cfg.airControl, this.airControl), 0, 1);
        if (cfg.jumpImpulse !== undefined) this.jumpImpulse = num(cfg.jumpImpulse, this.jumpImpulse);

        if (cfg.groundRay !== undefined) this.groundRay = num(cfg.groundRay, this.groundRay);
        if (cfg.groundEps !== undefined) this.groundEps = num(cfg.groundEps, this.groundEps);
        if (cfg.maxSlopeDot !== undefined) this.maxSlopeDot = num(cfg.maxSlopeDot, this.maxSlopeDot);

        if (cfg.debug) this.debug = mergeDebug(Object.assign({}, this.debug, cfg.debug));

        if (cfg.keys) {
            this.keys = cloneKeys(cfg.keys);
            this._codes = null; // recompile keycodes lazily
            this._kc = Object.create(null);
        }
        return this;
    }

    // ---------- input compilation ----------
    _keyCode(name) {
        const k = String(name || "").trim().toUpperCase();
        if (!k) return -1;
        const cached = this._kc[k];
        if (cached !== undefined) return cached | 0;

        let code = -1;
        try {
            const inp = engine.input();
            code = (inp && inp.keyCode) ? (inp.keyCode(k) | 0) : -1;
        } catch (_) { code = -1; }

        this._kc[k] = code | 0;
        return code | 0;
    }

    _compileKeys() {
        const pack = (names) => {
            const out = [];
            const n = names ? (names.length | 0) : 0;
            for (let i = 0; i < n; i++) {
                const c = this._keyCode(names[i]);
                if (c >= 0) out.push(c | 0);
            }
            return out;
        };

        this._codes = {
            forward: pack(this.keys.forward),
            back:    pack(this.keys.back),
            left:    pack(this.keys.left),
            right:   pack(this.keys.right),
            run:     pack(this.keys.run),
            jump:    pack(this.keys.jump),

            // debug convenience (single codes)
            W: this._keyCode("W") | 0,
            Z: this._keyCode("Z") | 0,
            A: this._keyCode("A") | 0,
            Q: this._keyCode("Q") | 0,
            S: this._keyCode("S") | 0,
            D: this._keyCode("D") | 0,
            SHIFT: this._keyCode("LSHIFT") | 0,
            SPACE: this._keyCode("SPACE") | 0
        };
    }

    _ensureKeyCodes() {
        if (!this._codes) this._compileKeys();
    }

    _anyDownCodes(snap, codes) {
        const kd = snap && snap.keysDown;
        if (!kd) return false;
        for (let i = 0; i < codes.length; i++) if (arrHas(kd, codes[i] | 0)) return true;
        return false;
    }

    _anyJustPressedCodes(snap, codes) {
        const jp = snap && snap.justPressed;
        if (!jp) return false;
        for (let i = 0; i < codes.length; i++) if (arrHas(jp, codes[i] | 0)) return true;
        return false;
    }

    _axisCodes(snap, negCodes, posCodes) {
        const pos = this._anyDownCodes(snap, posCodes) ? 1 : 0;
        const neg = this._anyDownCodes(snap, negCodes) ? 1 : 0;
        return pos - neg;
    }

    // ---------- binding ----------
    _resolveBodyId(any) {
        const x = any && (any.bodyId !== undefined ? any.bodyId : any);
        if (typeof x === "number") return x | 0;

        if (x && typeof x === "object") {
            try {
                if (typeof x.id === "function") return (x.id() | 0);
                if (typeof x.getBodyId === "function") return (x.getBodyId() | 0);
                if (typeof x.bodyId === "number") return (x.bodyId | 0);
                if (typeof x.id === "number") return (x.id | 0);
            } catch (_) {}
        }
        return 0;
    }

    bind(ids) {
        if (ids == null && this.player) ids = this.player;

        this.bodyId = this._resolveBodyId(ids);
        this._ensureKeyCodes(); // compile once on bind
        try { engine.log().info("[player] bind bodyId=" + (this.bodyId | 0)); } catch (_) {}
        return this;
    }

    // --------
    _readYaw() {
        // camera yaw from orchestrator (or fallback)
        let yaw = 0;
        try {
            const cam = engine.camera();
            if (cam && cam.yaw) yaw = num(cam.yaw(), 0);
            else if (cam && cam.rotationYaw) yaw = num(cam.rotationYaw(), 0);
        } catch (_) {}
        return yaw;
    }

    _groundCheck() {
        const bodyId = this.bodyId | 0;
        if (!bodyId) return false;

        let p = null;
        try { p = engine.physics().position(bodyId); } catch (_) {}
        if (!p) return false;

        const px = vx(p, 0), py = vy(p, 0), pz = vz(p, 0);

        this._tmpFrom.x = px; this._tmpFrom.y = py; this._tmpFrom.z = pz;
        this._tmpTo.x   = px; this._tmpTo.y   = py - this.groundRay; this._tmpTo.z = pz;

        let hit = null;
        try { hit = engine.physics().raycast({ from: this._tmpFrom, to: this._tmpTo }); } catch (_) {}
        if (!hit || !hit.hit) return false;

        const n = hit.normal || null;
        const ny = n ? (num((typeof n.y === "function") ? n.y() : n.y, 1)) : 1;
        if (ny < this.maxSlopeDot) return false;

        const dist = num(hit.distance, 9999);
        return dist <= (this.groundRay - this.groundEps);
    }

    _applyPlanarVelocity(vxNew, vzNew) {
        const bodyId = this.bodyId | 0;
        if (!bodyId) return;

        let vel = null;
        try { vel = engine.physics().velocity(bodyId); } catch (_) {}
        if (!vel) return;

        const vyOld = vy(vel, 0);

        this._tmpVel.x = vxNew;
        this._tmpVel.y = vyOld;
        this._tmpVel.z = vzNew;

        try { engine.physics().velocity(bodyId, this._tmpVel); } catch (_) {}
    }

    update(tpf, snap) {
        if (!this.enabled) return;
        const bodyId = this.bodyId | 0;
        if (!bodyId) return;

        this._ensureKeyCodes();

        // If player exists — always keep bodyId in sync (hot reload safe)
        if (this.player) this.bodyId = (this.player.bodyId | 0);

        // input axes
        const c = this._codes;
        const ax = this._axisCodes(snap, c.left, c.right);
        const az = this._axisCodes(snap, c.back, c.forward);

        const run = this._anyDownCodes(snap, c.run);
        const wantJump = this._anyJustPressedCodes(snap, c.jump);

        const grounded = this._groundCheck();

        // debug keys (optional)
        if (this.debug && this.debug.enabled) {
            this.debug._f = (this.debug._f + 1) | 0;
            if ((this.debug._f % (this.debug.everyFrames | 0)) === 0) {
                if (this.debug.logKeys) {
                    try {
                        engine.log().info("[player][keys] ax=" + ax + " az=" + az +
                            " run=" + (run ? "1" : "0") + " jump=" + (wantJump ? "1" : "0"));
                    } catch (_) {}
                }
                if (this.debug.logGround) {
                    try { engine.log().info("[player][ground] grounded=" + (grounded ? "1" : "0")); } catch (_) {}
                }
            }
        }

        // early out: no move input
        if (ax === 0 && az === 0) {
            // jump from standstill still allowed
            if (grounded && wantJump) {
                this._tmpJump.x = 0; this._tmpJump.y = this.jumpImpulse; this._tmpJump.z = 0;
                try { engine.physics().applyImpulse(bodyId, this._tmpJump); } catch (_) {}
            }
            return;
        }

        // local dir -> normalize
        norm2_into(ax, az, this._tmpN);

        // rotate by camera yaw to world-space
        const yaw = this._readYaw();
        rotateByYaw_into(this._tmpN.x, this._tmpN.z, yaw, this._tmpDir);

        // target speed
        const sp = run ? this.runSpeed : this.speed;

        // current vel
        let vel = null;
        try { vel = engine.physics().velocity(bodyId); } catch (_) {}
        const vxOld = vx(vel, 0);
        const vzOld = vz(vel, 0);

        // desired planar velocity
        const vxT = this._tmpDir.x * sp;
        const vzT = this._tmpDir.z * sp;

        // blend if in air
        const k = grounded ? 1.0 : clamp(this.airControl, 0, 1);

        const vxNew = vxOld + (vxT - vxOld) * k;
        const vzNew = vzOld + (vzT - vzOld) * k;

        this._applyPlanarVelocity(vxNew, vzNew);

        // jump
        if (grounded && wantJump) {
            this._tmpJump.x = 0; this._tmpJump.y = this.jumpImpulse; this._tmpJump.z = 0;
            try { engine.physics().applyImpulse(bodyId, this._tmpJump); } catch (_) {}
        }
    }

    warp(pos) {
        const bodyId = this.bodyId | 0;
        if (!bodyId || !pos) return;
        try { engine.physics().position(bodyId, pos); } catch (_) {}
    }

    warpXYZ(x, y, z) {
        this.warp({ x: +x || 0, y: +y || 0, z: +z || 0 });
    }

    getBodyId() { return this.bodyId | 0; }
}

module.exports = PlayerController;