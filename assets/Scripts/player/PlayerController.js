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
     * @param {Object=} cfg
     *  {
     *    enabled, speed, runSpeed, airControl, jumpImpulse,
     *    groundRay, groundEps, maxSlopeDot,
     *    keys: {forward/back/left/right/run/jump: [names...]},
     *    debug: {enabled, everyFrames, logGround, logKeys}
     *  }
     */
    constructor(cfg) {
        cfg = cfg || {};

        this.enabled = (cfg.enabled !== undefined) ? !!cfg.enabled : true;
        this.bodyId = 0;

        // movement
        this.speed      = (cfg.speed      !== undefined) ? num(cfg.speed, 6.0) : 6.0;
        this.runSpeed   = (cfg.runSpeed   !== undefined) ? num(cfg.runSpeed, 10.0) : 10.0;
        this.airControl = (cfg.airControl !== undefined) ? clamp(num(cfg.airControl, 0.35), 0, 1) : 0.35;
        this.jumpImpulse = (cfg.jumpImpulse !== undefined) ? num(cfg.jumpImpulse, 320.0) : 320.0;

        // ground check
        this.groundRay   = (cfg.groundRay   !== undefined) ? num(cfg.groundRay, 1.2) : 1.2;
        this.groundEps   = (cfg.groundEps   !== undefined) ? num(cfg.groundEps, 0.08) : 0.08;
        this.maxSlopeDot = (cfg.maxSlopeDot !== undefined) ? num(cfg.maxSlopeDot, 0.55) : 0.55;

        // keys (multi-layout)
        this.keys = cloneKeys(cfg.keys);

        // debug
        this.debug = mergeDebug(cfg.debug);

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
        this.bodyId = this._resolveBodyId(ids);
        this._ensureKeyCodes(); // compile once on bind
        try { engine.log().info("[player] bind bodyId=" + (this.bodyId | 0)); } catch (_) {}
        return this;
    }

    // ---------- update ----------
    update(tpf, snap) {
        if (!this.enabled) return;

        const bodyId = this.bodyId | 0;
        if (!bodyId) return;

        this._ensureKeyCodes();

        // keep upright
        try { engine.physics().lockRotation(bodyId, true); } catch (_) {}

        const grounded = this._isGrounded(bodyId);

        let yaw = 0;
        try { yaw = +engine.camera().yaw() || 0; } catch (_) {}

        // axes from snapshot
        const ix = this._axisCodes(snap, this._codes.left, this._codes.right);
        const iz = this._axisCodes(snap, this._codes.back, this._codes.forward);

        // normalize without alloc
        norm2_into(ix, iz, this._tmpN);
        const wantMove = len2(this._tmpN.x, this._tmpN.z) > 1e-6;

        // rotate by camera yaw (world dir)
        rotateByYaw_into(this._tmpN.x, this._tmpN.z, yaw, this._tmpDir);

        const running = this._anyDownCodes(snap, this._codes.run);
        const spd = running ? this.runSpeed : this.speed;

        // current velocity
        let v = null;
        try { v = engine.physics().velocity(bodyId); } catch (_) { v = null; }
        const vx0 = vx(v, 0), vy0 = vy(v, 0), vz0 = vz(v, 0);

        // in air keep inertia; on ground snap more
        const control = grounded ? 1.0 : this.airControl;

        const tx = wantMove ? (this._tmpDir.x * spd) : 0.0;
        const tz = wantMove ? (this._tmpDir.z * spd) : 0.0;

        // blend current -> target
        const nxv = vx0 + (tx - vx0) * control;
        const nzv = vz0 + (tz - vz0) * control;

        // reuse object
        const vel = this._tmpVel;
        vel.x = nxv; vel.y = vy0; vel.z = nzv;
        try { engine.physics().velocity(bodyId, vel); } catch (_) {}

        // jump: edge press
        const jumpPressed = this._anyJustPressedCodes(snap, this._codes.jump);
        if (jumpPressed && grounded) {
            const j = this._tmpJump;
            j.x = 0; j.y = this.jumpImpulse; j.z = 0;
            try { engine.physics().applyImpulse(bodyId, j); } catch (_) {}
        }

        // debug
        if (this.debug.enabled) {
            this.debug._f++;
            if ((this.debug._f % (this.debug.everyFrames | 0)) === 0) {
                let p = null, vel2 = null;
                try { p = engine.physics().position(bodyId); } catch (_) {}
                try { vel2 = engine.physics().velocity(bodyId); } catch (_) {}

                let msg =
                    "[player][dbg] bodyId=" + bodyId +
                    " grounded=" + grounded +
                    " yaw=" + yaw.toFixed(3) +
                    " pos=" + (p ? (vx(p, 0).toFixed(2) + "," + vy(p, 0).toFixed(2) + "," + vz(p, 0).toFixed(2)) : "null") +
                    " vel=" + (vel2 ? (vx(vel2, 0).toFixed(2) + "," + vy(vel2, 0).toFixed(2) + "," + vz(vel2, 0).toFixed(2)) : "null") +
                    " axis(ix,iz)=(" + ix + "," + iz + ")" +
                    " spd=" + spd.toFixed(2) +
                    " ctl=" + control.toFixed(2);

                if (this.debug.logKeys && snap) {
                    msg +=
                        " W=" + (snapHas(snap, "keysDown", this._codes.W) ? 1 : 0) +
                        " Z=" + (snapHas(snap, "keysDown", this._codes.Z) ? 1 : 0) +
                        " A=" + (snapHas(snap, "keysDown", this._codes.A) ? 1 : 0) +
                        " Q=" + (snapHas(snap, "keysDown", this._codes.Q) ? 1 : 0) +
                        " S=" + (snapHas(snap, "keysDown", this._codes.S) ? 1 : 0) +
                        " D=" + (snapHas(snap, "keysDown", this._codes.D) ? 1 : 0) +
                        " SHIFT=" + (this._anyDownCodes(snap, this._codes.run) ? 1 : 0) +
                        " SPACE(jp)=" + (snapHas(snap, "justPressed", this._codes.SPACE) ? 1 : 0);
                }

                try { engine.log().info(msg); } catch (_) {}
            }
        }
    }

    _isGrounded(bodyId) {
        let p = null;
        try { p = engine.physics().position(bodyId); } catch (_) { p = null; }
        if (!p) return false;

        const px = vx(p, 0), py0 = vy(p, 0), pz = vz(p, 0);

        // reuse ray objects
        const from = this._tmpFrom;
        const to = this._tmpTo;

        from.x = px; from.y = py0; from.z = pz;
        to.x   = px; to.y   = py0 - this.groundRay; to.z = pz;

        let hit = null;
        try { hit = engine.physics().raycast({ from, to }); } catch (_) { hit = null; }
        if (!hit || !hit.hit) return false;

        const n = hit.normal || null;
        const ny = n ? _readMember(n, "y", 1) : 1;

        if (ny < this.maxSlopeDot) return false;

        const dist = num(hit.distance, 9999);
        return dist <= (this.groundRay - this.groundEps);
    }

    // ---------- utilities ----------
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