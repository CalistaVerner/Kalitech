// FILE: Scripts/player/PlayerCamera.js
// Author: Calista Verner
"use strict";

const camModes = require("../Camera/CameraOrchestrator.js"); // :contentReference[oaicite:6]{index=6}

class PlayerCamera {
    constructor(cfg) {
        this.cfg = cfg || {};
        this.ready = false;
        this.type = "third";
        this.bodyId = 0;
    }

    attachTo(bodyId) {
        this.bodyId = bodyId | 0;
        try { camModes.attachTo(this.bodyId); } catch (_) {}
        return this;
    }

    configure(cfg) {
        if (cfg) this.cfg = cfg;
        const camCfg = this.cfg || {};

        const camType = camCfg.type || "third";
        this.type = camType;

        camModes.configure({
            debug: camCfg.debug || { enabled: true, everyFrames: 60 },
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
        this.ready = true;
        return this;
    }

    enableGameplayMouseGrab(enable) {
        // output-only: grab/cursorVisible — это допустимо по твоему контракту orchestrator-а
        try {
            if (engine.input().debug) engine.input().debug(true);
            if (engine.input().grabMouse) engine.input().grabMouse(!!enable);
            else if (engine.input().cursorVisible) engine.input().cursorVisible(!enable);
        } catch (_) {}
    }

    update(tpf, snap) {
        if (!this.ready) return;
        try {
            camModes.attachTo(this.bodyId | 0); // hot reload safe
            camModes.update(tpf, snap);
        } catch (_) {}
    }

    destroy() {
        this.ready = false;
        this.bodyId = 0;
        try {
            camModes.attachTo(0);
            camModes.setType("free");
        } catch (_) {}
        this.enableGameplayMouseGrab(false);
    }
}

module.exports = PlayerCamera;