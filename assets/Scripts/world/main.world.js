// FILE: Scripts/world/main.world.js
// Author: Calista Verner

module.exports.world = {
    name: "main",
    mode: "game", // "editor" later

    systems: [
        { id: "camera", order: 15, config: { mode: "fly", speed: 90, accel: 18, drag: 6.5, smoothing: 0.15 } },

        { id: "jsSystem", order: 18, config: { module: "Scripts/systems/sky.js",
                dayLengthSec: 10,
                skybox: "Textures/Sky/skyBox.dds",
                azimuthDeg: 35,
                shadows: { mapSize: 2048, splits: 3, lambda: 0.65 },
                fog: { color: { r: 0.70, g: 0.78, b: 0.90 }, densityDay: 1.10, densityNight: 1.35, distance: 250 }
            }},

        { id: "jsSystem", order: 20, config: { module: "Scripts/systems/scene.system.js" } },
        { id: "jsSystem", order: 50, config: { module: "Scripts/systems/spawn.system.js" } },
        { id: "jsSystem", order: 60, config: { module: "Scripts/systems/ai.system.js" } }
    ],
    entities: []
};