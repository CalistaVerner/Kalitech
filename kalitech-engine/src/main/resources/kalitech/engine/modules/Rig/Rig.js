"use strict";

// FILE: kalitech/engine/modules/Rig/Rig.js
// Author: KΛYLΛ

function resolveStateDomain(ctx) {
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

function resolveRigApi(ctx) {
    if (ctx) {
        if (typeof ctx.get === "function") {
            const v = ctx.get("rig.api") || ctx.get("RIG");
            if (v) return v;
        }

        const st = resolveStateDomain(ctx);
        if (st && typeof st.get === "function") {
            const v = st.get("rig.api") || st.get("RIG");
            if (v) return v;
        }
    }

    if (globalThis.RIG && typeof globalThis.RIG.bindToSpatial === "function") {
        return globalThis.RIG;
    }

    throw new Error("[RIG] RigApi not available. Ensure RigSystem is enabled in the world config.");
}

function normalizeRoleMap(roles) {
    if (!roles) throw new Error("[RIG] roles are required");

    const out = Object.create(null);

    if (Array.isArray(roles)) {
        for (let i = 0; i < roles.length; i++) {
            const item = roles[i];
            if (!item) continue;

            if (typeof item === "string") {
                out[item] = item;
                continue;
            }

            if (Array.isArray(item)) {
                const role = item[0];
                const bone = item[1];
                if (!role || !bone) throw new Error("[RIG] roles array requires [role, boneName]");
                out[String(role)] = String(bone);
                continue;
            }

            if (typeof item === "object") {
                const role = item.role || item.id || item.name;
                const bone = item.bone || item.boneName;
                if (!role || !bone) throw new Error("[RIG] roles array objects require role+boneName");
                out[String(role)] = String(bone);
                continue;
            }

            throw new Error("[RIG] roles array item must be string, [role, bone], or {role,boneName}");
        }
        return out;
    }

    if (typeof roles === "object") {
        for (const key of Object.keys(roles)) {
            const value = roles[key];
            if (typeof value === "string") {
                out[key] = value;
                continue;
            }
            if (Array.isArray(value)) {
                if (value.length > 0) out[key] = String(value[0]);
                continue;
            }
            if (value && typeof value === "object") {
                const bone = value.bone || value.boneName;
                if (bone) out[key] = String(bone);
            }
        }
        return out;
    }

    throw new Error("[RIG] roles must be an object or array");
}

function normalizeAliases(aliases) {
    if (!aliases) return null;
    if (Array.isArray(aliases)) {
        const out = Object.create(null);
        for (let i = 0; i < aliases.length; i++) {
            const entry = aliases[i];
            if (!entry || typeof entry !== "object") continue;
            const bone = entry.bone || entry.boneName;
            const al = entry.aliases || entry.alias || entry.values;
            if (!bone || !al) continue;
            out[String(bone)] = Array.isArray(al) ? al.map(String) : [String(al)];
        }
        return out;
    }

    if (typeof aliases === "object") return aliases;
    return null;
}

function normalizeSockets(sockets) {
    if (!sockets) return null;
    if (!Array.isArray(sockets)) return sockets;

    const out = Object.create(null);
    for (let i = 0; i < sockets.length; i++) {
        const entry = sockets[i];
        if (!entry || typeof entry !== "object") continue;
        const id = entry.id || entry.name || entry.socketId;
        if (!id) throw new Error("[RIG] socket entry requires id");
        const clone = Object.assign({}, entry);
        delete clone.id;
        delete clone.name;
        delete clone.socketId;
        out[String(id)] = clone;
    }
    return out;
}

function normalizeProfile(input) {
    if (!input || typeof input !== "object") throw new Error("[RIG] profile object is required");

    const id = input.id || input.profileId;
    if (!id) throw new Error("[RIG] profile.id is required");

    const skeleton = input.skeleton || Object.create(null);
    const roles = normalizeRoleMap(input.roles || skeleton.roles);

    return Object.freeze({
        id: String(id),
        skeleton: {
            root: input.root || skeleton.root || null,
            roles,
            aliases: normalizeAliases(input.aliases || skeleton.aliases)
        },
        sockets: normalizeSockets(input.sockets)
    });
}

function normalizeRegisterArgs(arg0, arg1) {
    if (arg1 === undefined) {
        return {ctx: null, profile: arg0};
    }
    return {ctx: arg0, profile: arg1};
}

function normalizeBindArgs(arg0, arg1, arg2) {
    if (arg2 === undefined) {
        return {ctx: null, profileId: arg0, spatial: arg1};
    }
    return {ctx: arg0, profileId: arg1, spatial: arg2};
}

function normalizeListArgs(arg0, arg1) {
    if (arg1 === undefined) {
        return {ctx: null, spatial: arg0};
    }
    return {ctx: arg0, spatial: arg1};
}

function create(engine) {
    if (!engine) throw new Error("[RIG] engine is required");

    function api(ctx) {
        return resolveRigApi(ctx);
    }

    function registerProfile(arg0, arg1) {
        const {ctx, profile} = normalizeRegisterArgs(arg0, arg1);
        const rig = resolveRigApi(ctx);
        rig.registerProfile(normalizeProfile(profile));
    }

    function registerMany(arg0, arg1) {
        const {ctx, profile} = normalizeRegisterArgs(arg0, arg1);
        const rig = resolveRigApi(ctx);
        if (Array.isArray(profile)) {
            const normalized = profile.map(normalizeProfile);
            rig.registerMany(normalized);
            return normalized.length;
        }
        if (profile && typeof profile === "object" && profile.id) {
            const normalized = normalizeProfile(profile);
            rig.registerMany([normalized]);
            return 1;
        }
        if (profile && typeof profile === "object") {
            const normalized = Object.create(null);
            for (const key of Object.keys(profile)) {
                const raw = profile[key];
                if (raw && typeof raw === "object" && !raw.id && !raw.profileId) {
                    normalized[key] = normalizeProfile(Object.assign({id: key}, raw));
                } else {
                    normalized[key] = normalizeProfile(raw);
                }
            }
            return rig.registerMany(normalized);
        }
        return 0;
    }

    function bind(arg0, arg1, arg2) {
        const {ctx, profileId, spatial} = normalizeBindArgs(arg0, arg1, arg2);
        if (!profileId) throw new Error("[RIG] profileId is required");
        const rig = resolveRigApi(ctx);
        return rig.bindToSpatial(globalThis, String(profileId), spatial);
    }

    function bindProfile(arg0, arg1, arg2) {
        const {ctx, profileId, spatial} = normalizeBindArgs(arg0, arg1, arg2);
        const profile = normalizeProfile(profileId);
        registerProfile(ctx, profile);
        return bind(ctx, profile.id, spatial);
    }

    function listBones(arg0, arg1) {
        const {ctx, spatial} = normalizeListArgs(arg0, arg1);
        const rig = resolveRigApi(ctx);
        return rig.listBones(spatial);
    }

    return Object.freeze({
        api,
        profile: normalizeProfile,
        registerProfile,
        registerMany,
        bind,
        bindProfile,
        listBones
    });
}

create.META = {
    moduleId: "rig",
    version: "0.2.0",
    description: "Rig utilities for profile normalization, registration, and binding",
    engineMin: "0.2.0"
};

module.exports = create;
module.exports.META = create.META;
