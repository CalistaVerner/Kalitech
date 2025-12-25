// Author: Calista Verner
// Centralized physics controller (NO guessing through ctx.entity())
// Uses: physics.position/velocity/applyImpulse/lockRotation (+ raycast for ground)
"use strict";

function len2(x, z) { return x * x + z * z; }
function norm2(x, z) {
    const l = Math.sqrt(len2(x, z));
    if (l < 1e-6) return { x: 0, z: 0 };
    return { x: x / l, z: z / l };
}
function axis(neg, pos) {
    return (engine.input().keyDown(pos) ? 1 : 0) - (engine.input().keyDown(neg) ? 1 : 0);
}
function rotateByYaw(localX, localZ, yaw) {
    const s = Math.sin(yaw), c = Math.cos(yaw);
    return { x: localX * c + localZ * s, z: localZ * c - localX * s };
}

class PlayerController {
    constructor() {
        this.enabled = true;

        // ids are injected by PlayerRuntime/Player
        this.entityId = 0;
        this.surfaceId = 0;
        this.bodyId = 0;

        // tuning
        this.speed = 6.0;
        this.runSpeed = 10.0;
        this.airControl = 0.35;
        this.jumpImpulse = 320.0;

        this.groundRay = 1.2;
        this.groundEps = 0.08;
        this.maxSlopeDot = 0.55;

        this.keys = {
            forward: "W", back: "S", left: "A", right: "D",
            run: "SHIFT", jump: "SPACE",
            warp: "R"
        };

        this._jumpLatch = false;
    }

    bind(ids) {
        // ids: { entityId, surfaceId, bodyId }
        this.entityId = (ids && ids.entityId) | 0;
        this.surfaceId = (ids && ids.surfaceId) | 0;
        this.bodyId = (ids && ids.bodyId) | 0;
        return this;
    }

    warp(pos) {
        if (!this.bodyId) return;
        engine.physics().warp(this.bodyId, pos);
    }

    warpXYZ(x, y, z) { this.warp({ x, y, z }); }

    update(tpf) {
        if (!this.enabled) return;
        if (!this.bodyId) return; // KEY FIX: never call physics without bodyId

        const bodyId = this.bodyId;

        // keep upright
        engine.physics().lockRotation(bodyId, true);

        const grounded = this._isGrounded(bodyId);

        // move
        const ix = axis(this.keys.left, this.keys.right);
        const iz = axis(this.keys.back, this.keys.forward);
        const n = norm2(ix, iz);
        const wantMove = len2(n.x, n.z) > 1e-6;

        const yaw = this._cameraYawRad();
        const dir = rotateByYaw(n.x, n.z, yaw);

        const running = engine.input().keyDown(this.keys.run);
        const spd = running ? this.runSpeed : this.speed;

        const v = engine.physics().velocity(bodyId);
        const control = grounded ? 1.0 : this.airControl;

        engine.physics().velocity(bodyId, {
            x: (wantMove ? dir.x * spd : 0) * control,
            y: v.y,
            z: (wantMove ? dir.z * spd : 0) * control
        });

        // jump edge-trigger
        const jumpDown = engine.input().keyDown(this.keys.jump);
        if (jumpDown && !this._jumpLatch && grounded) {
            engine.physics().applyImpulse(bodyId, { x: 0, y: this.jumpImpulse, z: 0 });
        }
        this._jumpLatch = jumpDown;

        // warp test
        if (engine.input().keyDown(this.keys.warp)) {
            this.warpXYZ(0, 3, 0);
        }
    }

    _cameraYawRad() {
        try {
            const y = engine.camera().yaw();
            if (typeof y === "number" && isFinite(y)) return y;
        } catch (_) {}
        try {
            const f = engine.camera().forward();
            return Math.atan2(f.x || 0, f.z || 1);
        } catch (_) {}
        return 0.0;
    }

    _isGrounded(bodyId) {
        const p = engine.physics().position(bodyId);
        const from = { x: p.x, y: p.y, z: p.z };
        const to   = { x: p.x, y: p.y - this.groundRay, z: p.z };
        const hit = engine.physics().raycast({ from, to });
        if (!hit || !hit.hit) return false;

        const n = hit.normal || { x: 0, y: 1, z: 0 };
        if ((n.y || 0) < this.maxSlopeDot) return false;

        return hit.distance <= (this.groundRay - this.groundEps);
    }
}

module.exports = PlayerController;