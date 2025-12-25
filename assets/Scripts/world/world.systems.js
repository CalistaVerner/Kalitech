// FILE: Scripts/world/world.systems.js
// Author: Calista Verner

"use strict";

// Single authoritative array of world systems
const worldSystems = [

    // --- Camera ---
    {
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
    },

    // --- Sky ---
    {
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
    },

    // --- Scene ---
    {
        id: "jsSystem",
        order: 20,
        stableId: "sys.scene",
        config: {
            module: "Scripts/systems/scene.system.js"
        }
    },

    // --- Spawn ---
    {
        id: "jsSystem",
        order: 50,
        stableId: "player",
        config: {
            module: "Scripts/systems/player.system.js"
        }
    },

    // --- AI ---
    {
        id: "jsSystem",
        order: 60,
        stableId: "sys.ai",
        config: {
            module: "Scripts/systems/ai.system.js"
        }
    }
];

// ✅ Single export (canonical)
exports.worldSystems = worldSystems;