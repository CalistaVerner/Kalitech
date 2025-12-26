// FILE: Scripts/player/index.js
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
const {PlayerEntityFactory} = require("./PlayerEntityFactory.js");

// -------------------- internal state --------------------

let alive = false;

let entity = null;
let entityId = 0;
let surfaceId = 0;
let bodyId = 0;

// subsystems
let movement = null;
let camera = null;
let ui = null;
let events = null;
let factory = null;

// config snapshot
let CFG = null;

// -------------------- helpers --------------------

function calcGroundedForEvents(cfg) {
    if (!bodyId) return false;

    let p = null;
    try {
        p = engine.physics().position(bodyId);
    } catch (_) {
    }
    if (!p) return false;

    const px = +((typeof p.x === "function") ? p.x() : p.x) || 0;
    const py = +((typeof p.y === "function") ? p.y() : p.y) || 0;
    const pz = +((typeof p.z === "function") ? p.z() : p.z) || 0;

    const m = cfg.movement || {};
    const groundRay = m.groundRay != null ? +m.groundRay : 1.2;
    const groundEps = m.groundEps != null ? +m.groundEps : 0.08;
    const maxSlopeDot = m.maxSlopeDot != null ? +m.maxSlopeDot : 0.55;

    const from = {x: px, y: py, z: pz};
    const to = {x: px, y: py - groundRay, z: pz};

    let hit = null;
    try {
        hit = engine.physics().raycast({from, to});
    } catch (_) {
    }
    if (!hit || !hit.hit) return false;

    const n = hit.normal || null;
    const ny = n ? (+((typeof n.y === "function") ? n.y() : n.y) || 1) : 1;
    if (ny < maxSlopeDot) return false;

    const dist = +hit.distance || 9999;
    return dist <= (groundRay - groundEps);
}

// -------------------- lifecycle --------------------

module.exports.init = function init(ctx) {
    if (alive) return;

    // ---- config (one place, OOP-composition style) ----
    CFG = {
        spawn: {
            pos: {x: 0, y: 3, z: 0}
        },

        movement: {
            speed: 6.0, runSpeed: 10.0
        },

        camera: {
            type: "third", debug: {enabled: true, everyFrames: 60}
        },

        ui: {
            crosshair: {
                size: 22, color: {r: 0.2, g: 1.0, b: 0.4, a: 1.0}
            }
        },

        events: {
            enabled: true, throttleMs: 250
        }
    };

    // ---- subsystems ----
    factory = new PlayerEntityFactory();
    movement = new PlayerController(CFG.movement);
    camera = new PlayerCamera(CFG.camera);
    ui = new PlayerUI(ctx, CFG.ui);
    events = new PlayerEvents(ctx, CFG.events);

    // ---- UI ----
    ui.create();

    // ---- spawn entity ----
    entity = factory.create(CFG.spawn);
    entityId = entity.entityId | 0;
    surfaceId = entity.surfaceId | 0;
    bodyId = entity.bodyId | 0;

    // ---- bind movement ----
    movement.bind({bodyId, entityId, surfaceId});

    // ---- camera ----
    camera.attachTo(bodyId).configure(CFG.camera);
    camera.enableGameplayMouseGrab(true);

    // ---- events ----
    events.reset();
    events.onSpawn({
        entityId, surfaceId, bodyId
    });

    // ---- publish state ----
    try {
        ctx.state().set("player", {
            alive: true, entityId, surfaceId, bodyId
        });
    } catch (_) {
    }

    alive = true;

    engine.log().info("[player] init entity=" + entityId + " bodyId=" + bodyId);
};

module.exports.update = function update(ctx, tpf) {
    if (!alive) return;

    // ONE snapshot per frame
    let snap = null;
    try {
        snap = engine.input().consumeSnapshot();
    } catch (_) {
    }

    // movement
    movement.update(tpf, snap);

    // camera
    camera.update(tpf, snap);

    // events (derived state)
    const grounded = calcGroundedForEvents(CFG);
    events.onState({entityId, surfaceId, bodyId}, {grounded, bodyId});

    // end frame
    try {
        if (engine.input().endFrame) engine.input().endFrame();
    } catch (_) {
    }
};

module.exports.destroy = function destroy(ctx) {
    if (!alive) return;

    try {
        camera.destroy();
    } catch (_) {
    }
    try {
        ui.destroy();
    } catch (_) {
    }
    try {
        if (entity) entity.destroy();
    } catch (_) {
    }

    entity = null;
    entityId = surfaceId = bodyId = 0;

    try {
        ctx.state().remove("player");
    } catch (_) {
    }

    alive = false;

    engine.log().info("[player] destroy");
};