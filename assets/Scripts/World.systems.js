"use strict";

const worldSystems = [{
    id: "jsSystem", order: 18, stableId: "sys.sky", config: {
        module: "Scripts/environment/sky/index.js",

        enabled: true,
        dayLengthSec: 1200,
        startTime01: 0.25,
        azimuthDeg: 35,

        skyCycle: {dayLengthSec: 1800, timeScale: 1.0, time01: 0.22, paused: false},
        skyDomeTexDay: "Textures/Sky/qwantani_afternoon_puresky_1k.hdr",
        skyDomeTexNight: "Textures/Sky/qwantani_afternoon_puresky_1k.hdr",

        sun: {azimuthDeg: 35, dayIntensity: 1.35, nightIntensity: 0.02, sunsetWarmth: 0.40},
        moon: {phaseOffset01: 0.5, nightIntensity: 0.20, dayIntensity: 0.01},

        skyDome: {
            zenithDay: {r: 0.08, g: 0.14, b: 0.30}, horizonDay: {r: 0.65, g: 0.72, b: 0.82},

            zenithNight: {r: 0.01, g: 0.02, b: 0.06}, horizonNight: {r: 0.03, g: 0.04, b: 0.08},

            hazeDay: 0.62, hazeNight: 0.25,

            exposureDay: 1.15, exposureNight: 0.55,

            twilightWarmth: 0.22, twilightHazeBoost: 0.12, twilightExposureBoost: 0.10,

            crossfade: {enabled: true, start: 0.10, end: 0.35},

            texBlendDay: 0.60, texBlendNight: 0.35,

            texExposureDay: 1.80, texExposureNight: 0.65,

            sunDisk: 45.0, moonDisk: 120.0
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
        daySwitch: 0.10, // OR explicit hysteresis:
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
            lambda: 0.85,
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
            enabled: true, strengthDay: 0.85, strengthNight: 0.0, dayResponse: 1.0
        },

        // ----------------------------------------------------------------
        // Fog
        // ----------------------------------------------------------------
        fog: {
            color: {r: 0.70, g: 0.78, b: 0.90}, densityDay: 1.10, densityNight: 1.35, distance: 250
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
        id: "jsSystem", order: 20, stableId: "sys.scene", config: {module: "Scripts/environment/world/world.js"}
    },


    {
        id: "jsSystem", order: 20, stableId: "sys.towers", config: {module: "Scripts/environment/towers.js"}
    },

    {
        id: "jsSystem", order: 50, stableId: "player", config: {
            module: "Scripts/player/index.js",

            controllers: [
                { id: "player.events",   module: "./controllers/PlayerEventsController.js",   export: "PlayerEventsController",   order: 10 },
                { id: "player.gameplay", module: "./controllers/PlayerGameplayController.js", export: "PlayerGameplayController", order: 20, deps: ["player.events"] },
                { id: "player.camera",   module: "./controllers/PlayerCameraController.js",   export: "PlayerCameraController",   order: 30, deps: ["player.gameplay"] },
                { id: "player.ui",       module: "./controllers/PlayerUIController.js",       export: "PlayerUIController",       order: 40, deps: ["player.events", "player.gameplay"] }
            ],
            spawn: {pos: {x: 129, y: 3, z: -300}, radius: 0.35, height: 1.8, mass: 80}, camera: {
                type: "first", volumeZones: {
                    enabled: true, zones: [{
                        id: "corridor_tight", priority: 100, blend: 1.25, shape: {
                            aabb: {
                                min: [10, 0, -20], max: [40, 6, 10]
                            }
                        }, overrides: {
                            zoomMin: 1.8, zoomMax: 4.2,

                            pivotOffset: [0.15, 1.55, 0.00], verticalLift: 0.08, shoulderX: 0.08,

                            collisionEnabled: true, camRadius: 0.28, surfacePadding: 0.10, floorPadding: 0.22,

                            minPitch: -0.85, maxPitch: 0.55
                        }
                    },

                        {
                            id: "open_field", priority: 10, blend: 3.0, shape: {
                                aabb: {
                                    min: [-9999, 0, -9999], max: [9999, 9999, 9999]
                                }
                            }, overrides: {
                                zoomMin: 2.0,
                                zoomMax: 18.0,
                                pivotOffset: [0.35, 1.45, 0.0],
                                verticalLift: 0.15,
                                shoulderX: 0.25
                            }
                        }]
                }
            }, ui: {layerName: "player.debug", anchor: "tl"},

            shoot: {
                speed: 24, spawnOffset: 0.25, events: {fire: "game.shoot.fire", hit: "game.shoot.hit"}
            },

            events: {enabled: true}
        }
    }];

exports.worldSystems = worldSystems;