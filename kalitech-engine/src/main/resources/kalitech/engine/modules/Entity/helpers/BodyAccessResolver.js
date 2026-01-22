"use strict";

function pickFn(obj, names) {
    if (!obj) return null;
    for (let i = 0; i < names.length; i++) {
        const k = names[i];
        const fn = obj[k];
        if (typeof fn === "function") return fn.bind(obj);
    }
    return null;
}

function constFn(v) {
    return function () {
        return v;
    };
}

function toXYZ(a, b, c) {
    if (a != null && typeof a === "object") {
        const x = a.x;
        const y = a.y;
        const z = a.z;
        return {
            x: (typeof x === "function") ? +x() : +x,
            y: (typeof y === "function") ? +y() : +y,
            z: (typeof z === "function") ? +z() : +z
        };
    }
    return {x: +a || 0, y: +b || 0, z: +c || 0};
}

function makeAdapter(raw, physicsApi, bodyId) {
    if (!raw || typeof raw !== "object") return null;

    const pos = pickFn(raw, [
        "position", "getPosition", "pos", "getPos", "worldPosition", "getWorldPosition"
    ]);

    const vel = pickFn(raw, [
        "getVel", "vel", "velocity", "getVelocity",
        "linearVelocity", "getLinearVelocity",
        "getLinearVel", "linearVel", "getLinVel"
    ]);

    const rot = pickFn(raw, [
        "rotation", "getRotation", "getRot",
        "quat", "getQuat", "getQuaternion",
        "orientation", "getOrientation",
        "worldRotation", "getWorldRotation"
    ]);

    const ang = pickFn(raw, [
        "getAngVel", "angVel",
        "angularVelocity", "getAngularVelocity",
        "omega", "getOmega"
    ]);

    const tr = pickFn(raw, [
        "transform", "getTransform",
        "worldTransform", "getWorldTransform"
    ]);

    // Write methods on raw (if present)
    const rawApplyImpulse = pickFn(raw, [
        "applyImpulse", "applyCentralImpulse",
        "impulse", "addImpulse", "applyLinearImpulse",
        "applyImpulseWorld", "applyWorldImpulse"
    ]);

    const rawSetVel = pickFn(raw, [
        "setVel", "setVelocity",
        "setLinearVelocity", "setLinearVel",
        "velocitySet", "linearVelocitySet"
    ]);

    const rawSetPos = pickFn(raw, [
        "setPos", "setPosition",
        "teleport", "warp", "setWorldPosition"
    ]);

    // Fallback to physics api (by id) if raw does not support it
    const pid = bodyId | 0;

    function apiApplyImpulse(x, y, z) {
        if (!physicsApi || pid <= 0) return false;

        const f = physicsApi;

        // Try a few common shapes of API:
        // applyImpulse(id, x,y,z) / applyImpulse(id, vec)
        if (typeof f.applyImpulse === "function") {
            try {
                if (arguments.length === 1 && x && typeof x === "object") f.applyImpulse(pid, x);
                else f.applyImpulse(pid, x, y, z);
                return true;
            } catch (_ignored) {
            }
        }

        // impulse(id, x,y,z) / impulse(id, vec)
        if (typeof f.impulse === "function") {
            try {
                if (arguments.length === 1 && x && typeof x === "object") f.impulse(pid, x);
                else f.impulse(pid, x, y, z);
                return true;
            } catch (_ignored) {
            }
        }

        // addImpulse(id, ...)
        if (typeof f.addImpulse === "function") {
            try {
                if (arguments.length === 1 && x && typeof x === "object") f.addImpulse(pid, x);
                else f.addImpulse(pid, x, y, z);
                return true;
            } catch (_ignored) {
            }
        }

        return false;
    }

    function apiSetVel(x, y, z) {
        if (!physicsApi || pid <= 0) return false;

        const f = physicsApi;

        if (typeof f.setVel === "function") {
            try {
                if (arguments.length === 1 && x && typeof x === "object") f.setVel(pid, x);
                else f.setVel(pid, x, y, z);
                return true;
            } catch (_ignored) {
            }
        }

        if (typeof f.setVelocity === "function") {
            try {
                if (arguments.length === 1 && x && typeof x === "object") f.setVelocity(pid, x);
                else f.setVelocity(pid, x, y, z);
                return true;
            } catch (_ignored) {
            }
        }

        if (typeof f.setLinearVelocity === "function") {
            try {
                if (arguments.length === 1 && x && typeof x === "object") f.setLinearVelocity(pid, x);
                else f.setLinearVelocity(pid, x, y, z);
                return true;
            } catch (_ignored) {
            }
        }

        return false;
    }

    function apiSetPos(x, y, z) {
        if (!physicsApi || pid <= 0) return false;

        const f = physicsApi;

        if (typeof f.setPos === "function") {
            try {
                if (arguments.length === 1 && x && typeof x === "object") f.setPos(pid, x);
                else f.setPos(pid, x, y, z);
                return true;
            } catch (_ignored) {
            }
        }

        if (typeof f.setPosition === "function") {
            try {
                if (arguments.length === 1 && x && typeof x === "object") f.setPosition(pid, x);
                else f.setPosition(pid, x, y, z);
                return true;
            } catch (_ignored) {
            }
        }

        if (typeof f.teleport === "function") {
            try {
                if (arguments.length === 1 && x && typeof x === "object") f.teleport(pid, x);
                else f.teleport(pid, x, y, z);
                return true;
            } catch (_ignored) {
            }
        }

        return false;
    }

    const zeroV = {x: 0, y: 0, z: 0};
    const identQ = {x: 0, y: 0, z: 0, w: 1};

    return Object.freeze({
        raw,

        // Reads (stable)
        position: pos || constFn(zeroV),
        getVel: vel || constFn(zeroV),
        rotation: rot || constFn(identQ),
        getAngVel: ang || constFn(zeroV),
        transform: tr || null,

        // Writes (stable)
        applyImpulse: function (a, b, c) {
            const v = toXYZ(a, b, c);

            if (rawApplyImpulse) {
                // Prefer raw method, try both vec and xyz shapes
                try {
                    rawApplyImpulse(v);
                    return;
                } catch (_ignored) {
                }
                try {
                    rawApplyImpulse(v.x, v.y, v.z);
                    return;
                } catch (_ignored) {
                }
            }

            if (apiApplyImpulse(v, v.y, v.z)) return;
            if (apiApplyImpulse(v.x, v.y, v.z)) return;

            throw new Error("[BodyAccess] applyImpulse not supported by raw body nor physics api (bodyId=" + pid + ")");
        },

        setVel: function (a, b, c) {
            const v = toXYZ(a, b, c);

            if (rawSetVel) {
                try {
                    rawSetVel(v);
                    return;
                } catch (_ignored) {
                }
                try {
                    rawSetVel(v.x, v.y, v.z);
                    return;
                } catch (_ignored) {
                }
            }

            if (apiSetVel(v, v.y, v.z)) return;
            if (apiSetVel(v.x, v.y, v.z)) return;

            throw new Error("[BodyAccess] setVel not supported by raw body nor physics api (bodyId=" + pid + ")");
        },

        setPos: function (a, b, c) {
            const v = toXYZ(a, b, c);

            if (rawSetPos) {
                try {
                    rawSetPos(v);
                    return;
                } catch (_ignored) {
                }
                try {
                    rawSetPos(v.x, v.y, v.z);
                    return;
                } catch (_ignored) {
                }
            }

            if (apiSetPos(v, v.y, v.z)) return;
            if (apiSetPos(v.x, v.y, v.z)) return;

            throw new Error("[BodyAccess] setPos not supported by raw body nor physics api (bodyId=" + pid + ")");
        }
    });
}

function resolveBodyAccess(physicsApi, bodyObj, bodyId) {
    if (!physicsApi) throw new Error("[BodyAccessResolver] physics api is required");

    if (bodyObj && typeof bodyObj === "object") {
        const adapted = makeAdapter(bodyObj, physicsApi, bodyId);
        if (!adapted) throw new Error("[BodyAccessResolver] failed to adapt body object");
        return adapted;
    }

    const id = bodyId | 0;
    if (id <= 0) return null;

    let raw = null;
    if (typeof physicsApi.body === "function") raw = physicsApi.body(id);
    else if (typeof physicsApi.getBody === "function") raw = physicsApi.getBody(id);
    else if (typeof physicsApi.bodyRef === "function") raw = physicsApi.bodyRef(id);

    if (!raw) {
        throw new Error("[BodyAccessResolver] physics body accessor missing or returned null for id=" + id);
    }

    const adapted = makeAdapter(raw, physicsApi, id);
    if (!adapted) throw new Error("[BodyAccessResolver] failed to adapt body for id=" + id);

    return adapted;
}

module.exports = {resolveBodyAccess};