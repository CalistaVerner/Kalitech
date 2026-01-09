"use strict";

const {bodyIdOf, surfaceIdOf} = require("./PhysicsIds.js");

function needEvents() {
    const E = ENGINE.physics;
    if (!E || typeof E.on !== "function") {
        throw new Error("[PHYS] EVENTS is required for collision subscriptions");
    }
    return E;
}

function normalizeFilter(filter) {
    if (!filter) return null;
    if (typeof filter !== "object") throw new Error("[PHYS] collision filter must be an object");

    const f = Object.assign({}, filter);
    if (f.a != null) f.a = bodyIdOf(f.a);
    if (f.b != null) f.b = bodyIdOf(f.b);
    if (f.bodyId != null) f.bodyId = bodyIdOf(f.bodyId);
    if (f.surfaceId != null) f.surfaceId = surfaceIdOf(f.surfaceId);
    return f;
}

function match(filter, evt) {
    if (!filter) return true;
    if (!evt) return false;

    const a = evt.a || {};
    const b = evt.b || {};

    if (filter.a && a.bodyId !== filter.a && b.bodyId !== filter.a) return false;
    if (filter.b && a.bodyId !== filter.b && b.bodyId !== filter.b) return false;
    if (filter.bodyId && a.bodyId !== filter.bodyId && b.bodyId !== filter.bodyId) return false;
    if (filter.surfaceId && a.surfaceId !== filter.surfaceId && b.surfaceId !== filter.surfaceId) return false;

    return true;
}

function onCollision(topic, filter, fn) {
    if (typeof filter === "function") {
        fn = filter;
        filter = null;
    }
    if (typeof fn !== "function") throw new Error("[PHYS] handler must be a function");

    const E = needEvents();
    const f = normalizeFilter(filter);

    return E.on(topic, (e) => {
        if (match(f, e)) return fn(e);
    });
}

module.exports = Object.freeze({
    onCollisionBegin: (f, fn) => onCollision("engine.physics.collision.begin", f, fn),
    onCollisionStay: (f, fn) => onCollision("engine.physics.collision.stay", f, fn),
    onCollisionEnd: (f, fn) => onCollision("engine.physics.collision.end", f, fn),
    onPostStep(fn) {
        const E = needEvents();
        if (typeof fn !== "function") throw new Error("[PHYS] handler must be a function");
        return E.on("engine.physics.postStep", fn);
    },
});