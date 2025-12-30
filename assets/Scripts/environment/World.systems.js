// FILE: Scripts/world/World.systems.js
// Author: Calista Verner

"use strict";

const worldSystems = [


    {
        id: "jsSystem",
        order: 18,
        stableId: "sys.sky",
        config: {
            module: "@env/sky",
            dayLengthSec: 10,
            skybox: "Textures/Sky/skyBox.dds",
            azimuthDeg: 35,
            shadows: { mapSize: 16384, splits: 4, lambda: 0.65 },
            fog: {
                color: { r: 0.70, g: 0.78, b: 0.90 },
                densityDay: 1.10,
                densityNight: 1.35,
                distance: 250
            }
        }
    },
    {
        id: "jsSystem",
        order: 20,
        stableId: "sys.scene",
        config: {
            module: "@env/world"
        }
    },
    {
        id: "jsSystem",
        order: 50,
        stableId: "player",
        config: {
            module: "Scripts/player"
        }
    }
];
exports.worldSystems = worldSystems;