"use strict";

const worldSystems = [
    {
        id: "jsSystem",
        order: 18,
        stableId: "sys.sky",
        config: {
            module: "Scripts/environment/sky/index.js",

            enabled: true,
            dayLengthSec: 1200,   // 20 minutes default
            startTime01: 0.25,    // morning
            azimuthDeg: 35,

            skybox: "Textures/Sky/skyBox.dds",

            // Sun (Kelvin pipeline)
            sunDayIntensity: 1.35,
            sunNightIntensity: 0.0,
            sunKelvinNoon: 6500,
            sunKelvinHorizon: 2200,
            baseSun: {r: 1.0, g: 1.0, b: 1.0},

            // Moon
            moonIntensity: 0.14,
            moonColor: {r: 0.45, g: 0.55, b: 0.85},

            // Primary switch
            daySwitch: 0.10,  // if you want just one knob
            // OR explicit hysteresis:
            // dayOn: 0.12,
            // dayOff: 0.08,

            // Ambient
            minAmbient: 0.20,
            ambientDay: {r: 0.25, g: 0.28, b: 0.35, intensity: 0.55},
            ambientNight: {r: 0.10, g: 0.12, b: 0.18, intensity: 0.12},

            // Shadows (dynamic day/night)
            shadows: {
                enabled: true,
                mapSizeDay: 16384,
                mapSizeNight: 8192,
                splits: 4,
                lambda: 0.65,
                intensityDay: 0.65,
                intensityNight: 0.35
            },

            // Sun rays / god rays (if render supports render.sunRaysCfg)
            sunRays: {
                enabled: true,
                strengthDay: 0.85,
                strengthNight: 0.0,
                dayResponse: 1.0
            },

            fog: {
                color: { r: 0.70, g: 0.78, b: 0.90 },
                densityDay: 1.10,
                densityNight: 1.35,
                distance: 250
            },

            debug: {
                skyEvery: 1.0
            }
        }
    },

    {
        id: "jsSystem",
        order: 20,
        stableId: "sys.scene",
        config: {module: "Scripts/environment/world/world.js"}
    },

    {
        id: "jsSystem",
        order: 50,
        stableId: "player",
        config: {
            module: "Scripts/player/index.js",

            spawn: {pos: {x: 129, y: 3, z: -300}, radius: 0.35, height: 1.8, mass: 80},
            camera: {type: "first"},
            ui: {layerName: "player.debug", anchor: "tl"},

            shoot: {
                speed: 24,
                spawnOffset: 0.25,
                events: {fire: "game.shoot.fire", hit: "game.shoot.hit"}
            },

            events: {enabled: true}
        }
    }
];

exports.worldSystems = worldSystems;