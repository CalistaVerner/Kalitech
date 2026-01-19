// FILE: Scripts/player/PlayerHandsRig.js
"use strict";

function req(v, msg) {
    if (v == null) throw new Error(msg);
    return v;
}

function isFn(v) {
    return typeof v === "function";
}

function isObj(v) {
    return !!v && typeof v === "object";
}

function resolveDomains(pawn) {
    return pawn && pawn.d ? pawn.d : null;
}

function tryCall(obj, names, ...args) {
    if (!obj) return null;
    for (let i = 0; i < names.length; i++) {
        const fn = obj[names[i]];
        if (isFn(fn)) {
            const r = fn.apply(obj, args);
            if (r != null) return r;
        }
    }
    return null;
}

function normalizeMaterialRef(mat) {
    if (mat == null) return null;
    if (typeof mat === "string") return { id: mat };
    if (isObj(mat)) {
        if (typeof mat.id === "string") return { id: mat.id };
        if (typeof mat.name === "string") return { name: mat.name };
    }
    return null;
}

function normalizeVec3(v, fb) {
    if (!v) return fb;
    if (Array.isArray(v) && v.length >= 3) {
        return { x: +v[0] || 0, y: +v[1] || 0, z: +v[2] || 0 };
    }
    if (isObj(v)) {
        return { x: +v.x || 0, y: +v.y || 0, z: +v.z || 0 };
    }
    return fb;
}

function normalizeQuat(v, fb) {
    if (!v) return fb;
    if (Array.isArray(v) && v.length >= 4) {
        return { x: +v[0] || 0, y: +v[1] || 0, z: +v[2] || 0, w: +v[3] || 1 };
    }
    if (isObj(v)) {
        return { x: +v.x || 0, y: +v.y || 0, z: +v.z || 0, w: (v.w != null) ? (+v.w || 0) : 1 };
    }
    return fb;
}

function getStateDomain(ctx) {
    if (!ctx) return null;
    try {
        if (ctx.stateDomain && typeof ctx.stateDomain.get === "function") return ctx.stateDomain;
    } catch (_) {
    }
    try {
        if (typeof ctx.state === "function") return ctx.state();
    } catch (_) {
    }
    try {
        if (ctx.state && typeof ctx.state.get === "function") return ctx.state;
    } catch (_) {
    }
    return null;
}

function resolveRigApiSafe(ctx) {
    // We DO NOT call @builtin/modules/Rig/Rig.resolveRigApi because it throws.
    // We replicate its lookup (ctx.get / state.get) without throwing.
    try {
        if (ctx && typeof ctx.get === "function") {
            const v = ctx.get("rig.api") || ctx.get("RIG");
            if (v) return v;
        }
    } catch (_) {
    }

    const st = getStateDomain(ctx);
    try {
        if (st && typeof st.get === "function") {
            const v = st.get("rig.api") || st.get("RIG");
            if (v) return v;
        }
    } catch (_) {
    }

    // If native RigApi is exported directly to globalThis (rare, but possible)
    try {
        if (globalThis.RIG && typeof globalThis.RIG.bindToSpatial === "function") {
            return globalThis.RIG;
        }
    } catch (_) {
    }

    return null;
}

function logOnce(key, msg) {
    const sd = getStateDomain(globalThis.ENGINE && globalThis.ENGINE.ctx ? globalThis.ENGINE.ctx : null);
    const flag = "__warn_once__" + String(key);
    try {
        if (sd && typeof sd.get === "function" && sd.get(flag) === true) return;
        if (sd && typeof sd.set === "function") sd.set(flag, true);
    } catch (_) {
    }

    // fallback to console
    try {
        if (globalThis.LOG && typeof globalThis.LOG.warn === "function") globalThis.LOG.warn(msg);
        else if (typeof console !== "undefined" && console && typeof console.warn === "function") console.warn(msg);
    } catch (_) {
    }
}

class PlayerHandsRig {
    constructor(pawn) {
        this.pawn = req(pawn, "[PlayerHandsRig] pawn required");
        this.d = resolveDomains(pawn);

        this._profileId = "player.hands.v1";

        this.handsSpatial = null;
        this.binding = null;
    }

    config() {
        const cfg = this.pawn.cfg || Object.create(null);

        const handsRig = (cfg.handsRig && isObj(cfg.handsRig)) ? cfg.handsRig : Object.create(null);
        const view = (cfg.view && isObj(cfg.view)) ? cfg.view : Object.create(null);

        const enabled = (handsRig.enabled !== undefined) ? !!handsRig.enabled : !!view.firstPersonHands;

        return {
            enabled,
            profileId: (typeof handsRig.profileId === "string" && handsRig.profileId) ? handsRig.profileId : this._profileId,

            modelPath: (typeof handsRig.modelPath === "string" && handsRig.modelPath) ? handsRig.modelPath : null,
            material: normalizeMaterialRef(handsRig.material || handsRig.materialId || handsRig.mat),

            attach: (typeof handsRig.attach === "string" && handsRig.attach) ? handsRig.attach : "camera",
            attachSocket: (typeof handsRig.attachSocket === "string" && handsRig.attachSocket) ? handsRig.attachSocket : "m.arm.socket.r",

            offset: normalizeVec3(handsRig.offset, { x: 0.12, y: -0.12, z: 0.35 }),
            rotation: normalizeQuat(handsRig.rotation, { x: 0, y: 0, z: 0, w: 1 }),
            scale: normalizeVec3(handsRig.scale, { x: 1, y: 1, z: 1 })
        };
    }

    static buildProfile(profileId) {
        const id = String(profileId || "player.hands.v1");
        return {
            id,
            skeleton: {
                root: null,
                roles: {
                    upperArmL: "m.arm.fk.l",
                    lowerArmL: "m.forearm.fk.l",
                    handL: "m.hand.fk.l",

                    upperArmR: "m.arm.fk.r",
                    lowerArmR: "m.forearm.fk.r",
                    handR: "m.hand.fk.r",

                    armIkL: "m.arm.ik.l",
                    forearmIkL: "m.forearm.ik.l",
                    elbowPoleL: "m.elbow.touch.pole.ik.l",

                    armIkR: "m.arm.ik.r",
                    forearmIkR: "m.forearm.ik.r",
                    elbowPoleR: "m.elbow.touch.pole.ik.r",

                    socketL: "m.arm.socket.l",
                    socketR: "m.arm.socket.r"
                },
                aliases: [
                    { bone: "m.arm.fk.l", aliases: ["upperArmL", "arm.l", "arm_fk_l"] },
                    { bone: "m.forearm.fk.l", aliases: ["lowerArmL", "forearm.l", "forearm_fk_l"] },
                    { bone: "m.hand.fk.l", aliases: ["handL", "hand.l", "hand_fk_l"] },

                    { bone: "m.arm.fk.r", aliases: ["upperArmR", "arm.r", "arm_fk_r"] },
                    { bone: "m.forearm.fk.r", aliases: ["lowerArmR", "forearm.r", "forearm_fk_r"] },
                    { bone: "m.hand.fk.r", aliases: ["handR", "hand.r", "hand_fk_r"] },

                    { bone: "m.arm.ik.l", aliases: ["armIkL", "arm_ik_l"] },
                    { bone: "m.forearm.ik.l", aliases: ["forearmIkL", "forearm_ik_l"] },
                    { bone: "m.elbow.touch.pole.ik.l", aliases: ["elbowPoleL", "pole_ik_l"] },

                    { bone: "m.arm.ik.r", aliases: ["armIkR", "arm_ik_r"] },
                    { bone: "m.forearm.ik.r", aliases: ["forearmIkR", "forearm_ik_r"] },
                    { bone: "m.elbow.touch.pole.ik.r", aliases: ["elbowPoleR", "pole_ik_r"] },

                    { bone: "m.arm.socket.l", aliases: ["socketL", "weapon_socket_l"] },
                    { bone: "m.arm.socket.r", aliases: ["socketR", "weapon_socket_r"] }
                ]
            },
            sockets: {
                weapon_r: { boneRole: "socketR" },
                weapon_l: { boneRole: "socketL" },

                hand_r: { boneRole: "handR" },
                hand_l: { boneRole: "handL" },

                // IK targets / helpers
                ik_hand_r: { boneRole: "forearmIkR" },
                ik_hand_l: { boneRole: "forearmIkL" },
                ik_pole_r: { boneRole: "elbowPoleR" },
                ik_pole_l: { boneRole: "elbowPoleL" }
            }

        };
    }

    _loadHandsSpatial(modelPath) {
        if (!modelPath) return null;

        const assets = (this.d && this.d.assets) ? this.d.assets : globalThis.ASSETS;
        if (!assets) return null;

        return tryCall(assets, ["loadModel", "model", "load", "loadSpatial", "spatial"], modelPath);
    }

    _applyMaterial(spatial, matRef) {
        if (!spatial || !matRef) return false;

        const mesh = (this.d && this.d.mesh) ? this.d.mesh : globalThis.MESH;
        if (!mesh) return false;

        const id = matRef.id || matRef.name;
        const ok = tryCall(mesh, ["applyMaterial", "setMaterial", "material", "setMat", "mat"], spatial, id);
        return ok != null;
    }

    _setLocalTransform(spatial, offset, rotation, scale) {
        if (!spatial) return;

        const mesh = (this.d && this.d.mesh) ? this.d.mesh : globalThis.MESH;
        if (!mesh) return;

        tryCall(mesh, ["setLocalPosition", "localPos", "setPos"], spatial, offset);
        tryCall(mesh, ["setLocalRotation", "localRot", "setRot"], spatial, rotation);
        tryCall(mesh, ["setLocalScale", "localScale", "setScale"], spatial, scale);
    }

    _attachSpatial(spatial, attachMode, socketName) {
        if (!spatial) return false;

        if (attachMode === "camera") {
            const cam = (this.d && this.d.camera) ? this.d.camera : (globalThis.CAM || null);
            if (cam) {
                const ok = tryCall(cam, ["attach", "attachSpatial", "attachModel", "attachToCamera", "attachToView"], spatial);
                if (ok != null) return true;
            }
        }

        const model = (this.pawn && isFn(this.pawn.getModel)) ? this.pawn.getModel() : null;
        if (!model) return false;

        const mesh = (this.d && this.d.mesh) ? this.d.mesh : globalThis.MESH;
        if (mesh) {
            const ok = tryCall(mesh, ["attachToSocket", "attachSocket", "attachToBone", "attachBone"], model, String(socketName || ""), spatial);
            if (ok != null) return true;
        }

        if (isFn(model.attachChild)) {
            model.attachChild(spatial);
            return true;
        }

        return false;
    }

    bind() {
        const cfg = this.config();
        const ctx = this.pawn.ctx || null;

        // 1) Load optional hands model
        if (cfg.enabled && cfg.modelPath) {
            const hands = this._loadHandsSpatial(cfg.modelPath);
            if (hands) {
                this.handsSpatial = hands;
                this._applyMaterial(hands, cfg.material);
                this._setLocalTransform(hands, cfg.offset, cfg.rotation, cfg.scale);
                this._attachSpatial(hands, cfg.attach, cfg.attachSocket);
            }
        }

        // 2) Rig bind (optional): only if RigApi exists
        const rigApi = resolveRigApiSafe(ctx);
        if (!rigApi) {
            logOnce("rig_api_missing",
                "[player.hands] RigApi missing (RigSystem disabled). Hands model will render, but rig binding is skipped."
            );
            return this;
        }

        // If globalThis.RIG is Rig.js facade, use it; otherwise use native api directly.
        const profile = PlayerHandsRig.buildProfile(cfg.profileId);

        try {
            if (globalThis.RIG && typeof globalThis.RIG.registerMany === "function" && typeof globalThis.RIG.bind === "function") {
                globalThis.RIG.registerMany(ctx || null, [profile]);
                const target = this.handsSpatial || req(this.pawn.getModel(), "[PlayerHandsRig] pawn.getModel() required");
                this.binding = globalThis.RIG.bind(ctx || null, profile.id, target);
                return this;
            }
        } catch (_) {
            // fallthrough to native
        }

        // Native api path
        try {
            if (typeof rigApi.registerMany === "function") rigApi.registerMany([profile]);
            else if (typeof rigApi.registerProfile === "function") rigApi.registerProfile(profile);

            const target = this.handsSpatial || req(this.pawn.getModel(), "[PlayerHandsRig] pawn.getModel() required");
            if (typeof rigApi.bindToSpatial === "function") {
                this.binding = rigApi.bindToSpatial(globalThis, profile.id, target);
            }
        } catch (e) {
            // If something went wrong, still do not crash the world update
            logOnce("rig_bind_failed", "[player.hands] rig bind failed: " + String(e && e.message ? e.message : e));
        }

        return this;
    }

    destroy() {
        const b = this.binding;
        this.binding = null;

        if (b && isFn(b.destroy)) {
            try { b.destroy(); } catch (_) {}
        } else if (b && isFn(b.dispose)) {
            try { b.dispose(); } catch (_) {}
        }

        const hands = this.handsSpatial;
        this.handsSpatial = null;

        if (hands) {
            const mesh = (this.d && this.d.mesh) ? this.d.mesh : globalThis.MESH;
            if (mesh) tryCall(mesh, ["detach", "detachSpatial", "remove"], hands);

            if (isFn(hands.removeFromParent)) {
                try { hands.removeFromParent(); } catch (_) {}
            }
        }

        this.d = null;
        this.pawn = null;
    }
}

module.exports = { PlayerHandsRig };