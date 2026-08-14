local M = {}
worldSystems = {{id = "luaSystem", order = 18, stableId = "sys.sky", config = {
    module = "Scripts/environment/sky/index.lua",
    enabled = true,
    dayLengthSec = 1200,
    startTime01 = 0.25,
    azimuthDeg = 35,
    skyCycle = {dayLengthSec = 1800, timeScale = 1, time01 = 0.22, paused = false},
    skyDomeTexDay = "Textures/Sky/qwantani_afternoon_puresky_1k.hdr",
    skyDomeTexNight = "Textures/Sky/qwantani_afternoon_puresky_1k.hdr",
    sun = {azimuthDeg = 35, dayIntensity = 1.35, nightIntensity = 0.02, sunsetWarmth = 0.4},
    moon = {phaseOffset01 = 0.5, nightIntensity = 0.2, dayIntensity = 0.01},
    skyDome = {
        zenithDay = {r = 0.08, g = 0.14, b = 0.3},
        horizonDay = {r = 0.65, g = 0.72, b = 0.82},
        zenithNight = {r = 0.01, g = 0.02, b = 0.06},
        horizonNight = {r = 0.03, g = 0.04, b = 0.08},
        hazeDay = 0.62,
        hazeNight = 0.25,
        exposureDay = 1.15,
        exposureNight = 0.55,
        twilightWarmth = 0.22,
        twilightHazeBoost = 0.12,
        twilightExposureBoost = 0.1,
        crossfade = {enabled = true, start = 0.1, ["end"] = 0.35},
        texBlendDay = 0.6,
        texBlendNight = 0.35,
        texExposureDay = 1.8,
        texExposureNight = 0.65,
        sunDisk = 45,
        moonDisk = 120
    },
    sunDayIntensity = 1.35,
    sunNightIntensity = 0,
    sunKelvinNoon = 6500,
    sunKelvinHorizon = 2200,
    baseSun = {r = 1, g = 1, b = 1},
    moonIntensity = 0.14,
    moonColor = {r = 0.45, g = 0.55, b = 0.85},
    daySwitch = 0.1,
    minAmbient = 0.2,
    ambientDay = {r = 0.25, g = 0.28, b = 0.35, intensity = 0.55},
    ambientNight = {r = 0.1, g = 0.12, b = 0.18, intensity = 0.12},
    shadows = {
        enabled = true,
        mapSizeDay = 16384,
        mapSizeNight = 8192,
        splits = 4,
        lambda = 0.85,
        intensityDay = 0.65,
        intensityNight = 0.35,
        softnessDay = 0.35,
        softnessNight = 0.2,
        pcfSamples = 16,
        pcss = true,
        lightRadiusDay = 0.9,
        lightRadiusNight = 0.35
    },
    sunRays = {enabled = true, strengthDay = 0.85, strengthNight = 0, dayResponse = 1},
    fog = {color = {r = 0.7, g = 0.78, b = 0.9}, densityDay = 1.1, densityNight = 1.35, distance = 250},
    post = {
        enabled = false,
        exposureDay = 1.05,
        exposureNight = 0.25,
        exposureCurve = 1.25,
        whitePointDay = 11.2,
        whitePointNight = 6.5,
        shoulderDay = 0.22,
        shoulderNight = 0.12,
        toeDay = 0.08,
        toeNight = 0.18,
        saturationDay = 1.05,
        saturationNight = 0.85
    },
    debug = {skyEvery = 1}
}}, {id = "luaSystem", order = 20, stableId = "sys.scene", config = {module = "Scripts/environment/world/world.lua"}}, {id = "luaSystem", order = 20, stableId = "sys.towers", config = {module = "Scripts/environment/towers.lua"}}, {
    id = "luaSystem",
    order = 50,
    stableId = "player",
    config = {
        module = "Scripts/player/index.lua",
        spawn = {pos = {x = 129, y = 3, z = -300}, radius = 0.35, height = 1.8, mass = 80},
        camera = {type = "first", volumeZones = {enabled = true, zones = {{
            id = "corridor_tight",
            priority = 100,
            blend = 1.25,
            shape = {aabb = {min = {10, 0, -20}, max = {40, 6, 10}}},
            overrides = {
                zoomMin = 1.8,
                zoomMax = 4.2,
                pivotOffset = {0.15, 1.55, 0},
                verticalLift = 0.08,
                shoulderX = 0.08,
                collisionEnabled = true,
                camRadius = 0.28,
                surfacePadding = 0.1,
                floorPadding = 0.22,
                minPitch = -0.85,
                maxPitch = 0.55
            }
        }, {
            id = "open_field",
            priority = 10,
            blend = 3,
            shape = {aabb = {min = {-9999, 0, -9999}, max = {9999, 9999, 9999}}},
            overrides = {
                zoomMin = 2,
                zoomMax = 18,
                pivotOffset = {0.35, 1.45, 0},
                verticalLift = 0.15,
                shoulderX = 0.25
            }
        }}}},
        shoot = {speed = 24, spawnOffset = 0.25, events = {fire = "game.shoot.fire", hit = "game.shoot.hit"}},
        events = {enabled = true}
    },
    ui = {
        layerName = "debug-ui",
        htmlPath = "ui/debug.hud.html",
        anchor = "tl",
        marginLeft = 10,
        marginTop = 10,
        w = 280,
        padX = 12,
        padY = 8,
        fontTitle = 18,
        fontLine = 14,
        lineGap = 4,
        enableTemplateTokens = true
    }
}}
M.worldSystems = worldSystems

return M
