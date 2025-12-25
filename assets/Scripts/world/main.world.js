// FILE: Scripts/world/main.world.js
// Author: Calista Verner
//
// Main world descriptor (data-first).
// Platform-ready: meta + schemaVersion + presets + validation.

const WORLD_SCHEMA_VERSION = 1;

// --- Official module contract meta ---
exports.meta = {
    id: "kalitech.world.main",
    version: "1.0.0",
    apiMin: "0.1.0",
    name: "Main World Descriptor"
};

// --- Helpers (local, no external deps) ---
function deepFreeze(obj) {
    if (!obj || typeof obj !== "object") return obj;
    Object.freeze(obj);
    for (const k of Object.keys(obj)) deepFreeze(obj[k]);
    return obj;
}

function clone(obj) {
    return obj ? JSON.parse(JSON.stringify(obj)) : obj;
}

function validateWorld(world) {
    const errors = [];

    if (!world || typeof world !== "object") {
        errors.push("world must be an object");
        return errors;
    }

    if (!world.name || typeof world.name !== "string") errors.push("world.name must be string");
    if (!world.mode || (world.mode !== "game" && world.mode !== "editor")) errors.push('world.mode must be "game" or "editor"');
    if (!Array.isArray(world.systems)) errors.push("world.systems must be array");
    if (!Array.isArray(world.entities)) errors.push("world.entities must be array");

    // systems validation
    const ids = new Set();
    for (const s of (world.systems || [])) {
        if (!s || typeof s !== "object") { errors.push("system entry must be object"); continue; }

        const stableId = String(s.stableId ?? "").trim();
        if (!stableId) errors.push("system.stableId is required (string) for platform-grade hot reload");

        if (stableId) {
            if (ids.has(stableId)) errors.push(`duplicate system.stableId: ${stableId}`);
            ids.add(stableId);
        }

        if (typeof s.order !== "number") errors.push(`system.order must be number (stableId=${stableId || "?"})`);
        if (!s.id || typeof s.id !== "string") errors.push(`system.id must be string (stableId=${stableId || "?"})`);
        if (s.config != null && typeof s.config !== "object") errors.push(`system.config must be object (stableId=${stableId || "?"})`);

        if (s.id === "jsSystem") {
            const mod = s.config?.module;
            if (!mod || typeof mod !== "string") errors.push(`jsSystem requires config.module string (stableId=${stableId || "?"})`);
        }
    }

    return errors;
}

// --- Base blocks (shared across presets) ---
const baseCamera = {
    id: "camera",
    order: 15,
    stableId: "sys.camera",
    config: {
        mode: "fly",
        speed: 90,
        accel: 18,
        drag: 6.5,
        smoothing: 0.15
    }
};

const baseSky = {
    id: "jsSystem",
    order: 18,
    stableId: "sys.sky",
    config: {
        module: "@core/sky",
        dayLengthSec: 10,
        skybox: "Textures/Sky/skyBox.dds",
        azimuthDeg: 35,
        shadows: { mapSize: 2048, splits: 3, lambda: 0.65 },
        fog: {
            color: { r: 0.70, g: 0.78, b: 0.90 },
            densityDay: 1.10,
            densityNight: 1.35,
            distance: 250
        }
    }
};

const baseScene = {
    id: "jsSystem",
    order: 20,
    stableId: "sys.scene",
    config: { module: "Scripts/systems/scene.system.js" }
};

const baseSpawn = {
    id: "jsSystem",
    order: 50,
    stableId: "sys.spawn",
    config: { module: "Scripts/systems/spawn.system.js" }
};

const baseAI = {
    id: "jsSystem",
    order: 60,
    stableId: "sys.ai",
    config: { module: "Scripts/systems/ai.system.js" }
};

// --- Editor-only systems ---
const editorGrid = {
    id: "jsSystem",
    order: 12,
    stableId: "sys.editor.grid",
    config: {
        module: "@core/editor/editor.grid.system",
        enabled: true,
        size: 200,
        step: 1.0,
        majorStep: 10,
        y: 0.01,
        opacity: 0.35
    }
};

const editorPick = {
    id: "jsSystem",
    order: 13,
    stableId: "sys.editor.pick",
    config: {
        module: "@core/editor/editor.pick.system",
        enabled: true,
        mode: "click",      // "click" | "hover"
        button: "LMB",
        maxDistance: 10000,
        debugLog: true
    }
};

// --- Presets: game vs editor (future growth, no copy-paste) ---
const presets = {
    game() {
        return {
            name: "main",
            mode: "game",
            schemaVersion: WORLD_SCHEMA_VERSION,

            systems: [
                baseCamera,
                baseSky,
                baseScene,
                baseSpawn,
                baseAI
            ],

            entities: []
        };
    },

    editor() {
        const w = presets.game();
        w.mode = "editor";
        w.name = "main_editor";

        // Editor systems first (before camera/scene)
        w.systems.unshift(editorPick);
        w.systems.unshift(editorGrid);

        return w;
    }
};

// --- Export world (legacy) + create() (platform-style) ---
function buildWorld(mode) {
    const m = (mode === "editor") ? "editor" : "game";
    const w = presets[m]();

    const frozen = deepFreeze(clone(w));

    const errs = validateWorld(frozen);
    if (errs.length) {
        throw new Error("[world] Invalid world descriptor:\n- " + errs.join("\n- "));
    }

    return frozen;
}

// Legacy export used by Scripts/main.js currently
exports.world = buildWorld("editor");

// New preferred API: exports.create({mode}) -> returns descriptor
exports.create = function (opts) {
    const mode = opts?.mode || "game";
    return buildWorld(mode);
};

// Optional: expose validator for tooling
exports.validate = function (world) {
    return validateWorld(world);
};