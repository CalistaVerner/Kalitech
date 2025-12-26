// FILE: Scripts/player/PlayerController.js
// Author: Calista Verner
"use strict";

function len2(x, z) { return x * x + z * z; }
function norm2(x, z) {
    const l = Math.sqrt(len2(x, z));
    if (l < 1e-6) return { x: 0, z: 0 };
    return { x: x / l, z: z / l };
}
function rotateByYaw(localX, localZ, yaw) {
    const s = Math.sin(yaw), c = Math.cos(yaw);
    return { x: localX * c + localZ * s, z: localZ * c - localX * s };
}

function num(v, fallback) {
    const n = +v;
    return Number.isFinite(n) ? n : (fallback || 0);
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

function anyKeyDown(keys) {
    for (let i = 0; i < keys.length; i++) {
        if (engine.input().keyDown(keys[i])) return true;
    }
    return false;
}
function axisKeys(negKeys, posKeys) {
    const pos = anyKeyDown(posKeys) ? 1 : 0;
    const neg = anyKeyDown(negKeys) ? 1 : 0;
    return pos - neg;
}

class PlayerController {
    constructor() {
        this.enabled = true;
        this.bodyId = 0;

        this.speed = 6.0;
        this.runSpeed = 10.0;
        this.airControl = 0.35;
        this.jumpImpulse = 320.0;

        this.groundRay = 1.2;
        this.groundEps = 0.08;
        this.maxSlopeDot = 0.55;

        // ✅ support multiple layouts:
        // - QWERTY/QWERTZ: WASD
        // - AZERTY: ZQSD
        // - arrows as fallback
        this.keys = {
            forward: ["W", "Z", "UP"],
            back:    ["S", "DOWN"],
            left:    ["A", "Q", "LEFT"],
            right:   ["D", "RIGHT"],

            // shift variants (some systems expose LSHIFT/RSHIFT)
            run:     ["SHIFT", "LSHIFT", "RSHIFT"],
            jump:    ["SPACE"]
        };

        this._jumpLatch = false;

        this.debug = {
            enabled: true,
            everyFrames: 60,
            _f: 0,
            logGround: false,
            logKeys: true
        };
    }

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
        try { engine.log().info("[player] bind bodyId=" + (this.bodyId | 0)); } catch (_) {}
        return this;
    }

    update(tpf) {
        if (!this.enabled) return;

        const bodyId = this.bodyId | 0;
        if (!bodyId) return;

        // keep upright
        try { engine.physics().lockRotation(bodyId, true); } catch (_) {}

        const grounded = this._isGrounded(bodyId);

        let yaw = 0;
        try { yaw = +engine.camera().yaw() || 0; } catch (_) {}

        const ix = axisKeys(this.keys.left, this.keys.right);
        const iz = axisKeys(this.keys.back, this.keys.forward);

        const n = norm2(ix, iz);
        const wantMove = (n.x * n.x + n.z * n.z) > 1e-6;

        const dir = rotateByYaw(n.x, n.z, yaw);

        const running = anyKeyDown(this.keys.run);
        const spd = running ? this.runSpeed : this.speed;

        let v = null;
        try { v = engine.physics().velocity(bodyId); } catch (_) {}
        const vy0 = vy(v, 0);

        const control = grounded ? 1.0 : this.airControl;

        try {
            engine.physics().velocity(bodyId, {
                x: (wantMove ? dir.x * spd : 0) * control,
                y: vy0,
                z: (wantMove ? dir.z * spd : 0) * control
            });
        } catch (_) {}

        const jumpDown = anyKeyDown(this.keys.jump);
        if (jumpDown && !this._jumpLatch && grounded) {
            try { engine.physics().applyImpulse(bodyId, { x: 0, y: this.jumpImpulse, z: 0 }); } catch (_) {}
        }
        this._jumpLatch = jumpDown;

        // debug
        if (this.debug.enabled) {
            this.debug._f++;
            if ((this.debug._f % this.debug.everyFrames) === 0) {
                let p = null, vel = null;
                try { p = engine.physics().position(bodyId); } catch (_) {}
                try { vel = engine.physics().velocity(bodyId); } catch (_) {}

                let msg =
                    "[player][dbg] bodyId=" + bodyId +
                    " grounded=" + grounded +
                    " yaw=" + yaw.toFixed(3) +
                    " pos=" + (p ? (vx(p, 0).toFixed(2) + "," + vy(p, 0).toFixed(2) + "," + vz(p, 0).toFixed(2)) : "null") +
                    " vel=" + (vel ? (vx(vel, 0).toFixed(2) + "," + vy(vel, 0).toFixed(2) + "," + vz(vel, 0).toFixed(2)) : "null") +
                    " axis(ix,iz)=(" + ix + "," + iz + ")" +
                    " spd=" + spd.toFixed(2) +
                    " ctl=" + control.toFixed(2);

                if (this.debug.logKeys) {
                    // show both layouts quickly
                    msg +=
                        " keyW=" + (engine.input().keyDown("W") ? 1 : 0) +
                        " keyZ=" + (engine.input().keyDown("Z") ? 1 : 0) +
                        " keyA=" + (engine.input().keyDown("A") ? 1 : 0) +
                        " keyQ=" + (engine.input().keyDown("Q") ? 1 : 0) +
                        " keyS=" + (engine.input().keyDown("S") ? 1 : 0) +
                        " keyD=" + (engine.input().keyDown("D") ? 1 : 0) +
                        " SHIFT=" + (anyKeyDown(this.keys.run) ? 1 : 0) +
                        " SPACE=" + (jumpDown ? 1 : 0);
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

        const from = { x: px, y: py0, z: pz };
        const to   = { x: px, y: py0 - this.groundRay, z: pz };

        let hit = null;
        try { hit = engine.physics().raycast({ from, to }); } catch (_) { hit = null; }
        if (!hit || !hit.hit) return false;

        const n = hit.normal || { x: 0, y: 1, z: 0 };
        const ny = _readMember(n, "y", 1);

        if (ny < this.maxSlopeDot) return false;

        const dist = num(hit.distance, 9999);
        const ok = dist <= (this.groundRay - this.groundEps);

        if (this.debug.enabled && this.debug.logGround) {
            try { engine.log().info("[player][ground] dist=" + dist.toFixed(3) + " ny=" + ny.toFixed(3) + " ok=" + (ok ? 1 : 0)); } catch (_) {}
        }

        return ok;
    }

    getBodyId() { return this.bodyId | 0; }
}

module.exports = PlayerController;