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

            // ----------------------------------------------------------------
            // SkyDome (AAA) — replaces SkyBox
            //  - equirect pano textures (optional)
            //  - procedural gradient + sun/moon discs always work
            // ----------------------------------------------------------------

            // Optional: texture set (SkyBox-style switching)
            // If you want only one texture -> use skyDomeTex only.
            skyDomeTex: "Textures/Sky/sunrise_1k.hdr",
            skyDomeTexDay: "Textures/Sky/sunrise_1k.hdr",
            skyDomeTexSunset: "Textures/Sky/sunrise_1k.hdr",
            skyDomeTexNight: "Textures/Sky/sunrise_1k.hdr",

            // If you don't want textures at all, remove the keys above
            // and set texBlendDay/Night to 0 in skyDome below.

            skyDome: {
                texBlendDay: 0.55,
                texBlendNight: 0.35,

                texExposureDay: 1.8,
                texExposureNight: 0.65,

                exposureDay: 1.05,
                exposureNight: 0.55,

                hazeDay: 0.60,
                hazeNight: 0.28,

                zenithColor: {r: 0.08, g: 0.14, b: 0.30},
                horizonColor: {r: 0.65, g: 0.72, b: 0.82}
            },

            // ----------------------------------------------------------------
            // Sun (Kelvin pipeline)
            // ----------------------------------------------------------------
            sunDayIntensity: 1.35,
            sunNightIntensity: 0.0,
            sunKelvinNoon: 6500,
            sunKelvinHorizon: 2200,
            baseSun: {r: 1.0, g: 1.0, b: 1.0},

            // ----------------------------------------------------------------
            // Moon
            // ----------------------------------------------------------------
            moonIntensity: 0.14,
            moonColor: {r: 0.45, g: 0.55, b: 0.85},

            // ----------------------------------------------------------------
            // Primary switch
            // ----------------------------------------------------------------
            daySwitch: 0.10,
            // OR explicit hysteresis:
            // dayOn: 0.12,
            // dayOff: 0.08,

            // ----------------------------------------------------------------
            // Ambient
            // ----------------------------------------------------------------
            minAmbient: 0.20,
            ambientDay: {r: 0.25, g: 0.28, b: 0.35, intensity: 0.55},
            ambientNight: {r: 0.10, g: 0.12, b: 0.18, intensity: 0.12},

            // ----------------------------------------------------------------
            // Shadows (dynamic day/night)
            // ----------------------------------------------------------------
            shadows: {
                enabled: true,
                mapSizeDay: 16384,
                mapSizeNight: 8192,
                splits: 4,
                lambda: 0.65,
                intensityDay: 0.65,
                intensityNight: 0.35,

                // softness / penumbra knobs
                softnessDay: 0.35,     // 0..1
                softnessNight: 0.20,   // 0..1
                pcfSamples: 16,        // 1/4/9/16/25...
                pcss: true,            // if your shadow impl supports PCSS
                lightRadiusDay: 0.9,   // PCSS disk size
                lightRadiusNight: 0.35
            },

            // ----------------------------------------------------------------
            // Sun rays / god rays (if render supports render.sunRaysCfg)
            // ----------------------------------------------------------------
            sunRays: {
                enabled: true,
                strengthDay: 0.85,
                strengthNight: 0.0,
                dayResponse: 1.0
            },

            // ----------------------------------------------------------------
            // Fog
            // ----------------------------------------------------------------
            fog: {
                color: { r: 0.70, g: 0.78, b: 0.90 },
                densityDay: 1.10,
                densityNight: 1.35,
                distance: 250
            },

            // ----------------------------------------------------------------
            // Post (if enabled, LightRig should drive render.postCfg per dayFactor)
            // ----------------------------------------------------------------
            post: {
                enabled: false,

                exposureDay: 1.05,
                exposureNight: 0.25,
                exposureCurve: 1.25,

                whitePointDay: 11.2,
                whitePointNight: 6.5,
                shoulderDay: 0.22,
                shoulderNight: 0.12,
                toeDay: 0.08,
                toeNight: 0.18,
                saturationDay: 1.05,
                saturationNight: 0.85
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