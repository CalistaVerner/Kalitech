local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
SkyMath = require("./SkyMath.lua")
CelestialModel = LuaClass()
CelestialModel.name = "CelestialModel"
function CelestialModel.prototype.lua_constructor(self)
    self.azimuthDeg = 35
    self.sunDayIntensity = 1.35
    self.sunNightIntensity = 0
    self.sunKelvinNoon = 6500
    self.sunKelvinHorizon = 2200
    self.baseSun = {r = 1, g = 1, b = 1}
    self.moonIntensity = 0.14
    self.moonColor = {r = 0.45, g = 0.55, b = 0.85}
    self.daySwitch = 0.1
    self.dayOn = 0.12
    self.dayOff = 0.08
    self._primary = "sun"
    self.dayCurve = {horizonOffset = 0.06, horizonScale = 0.55, dayStart = 0.08, dayEnd = 0.38}
end
function CelestialModel.prototype.applyCfg(self, cfg)
    if not cfg then
        return
    end
    local az = LuaNumber(cfg.azimuthDeg)
    if LuaNumberIsFinite(az) then
        self.azimuthDeg = az
    end
    if cfg.sunDayIntensity ~= nil then
        local v = LuaNumber(cfg.sunDayIntensity)
        if LuaNumberIsFinite(v) then
            self.sunDayIntensity = v
        end
    end
    if cfg.sunNightIntensity ~= nil then
        local v = LuaNumber(cfg.sunNightIntensity)
        if LuaNumberIsFinite(v) then
            self.sunNightIntensity = v
        end
    end
    if cfg.sunKelvinNoon ~= nil then
        local v = LuaNumber(cfg.sunKelvinNoon)
        if LuaNumberIsFinite(v) then
            self.sunKelvinNoon = SkyMath:clamp(v, 1000, 40000)
        end
    end
    if cfg.sunKelvinHorizon ~= nil then
        local v = LuaNumber(cfg.sunKelvinHorizon)
        if LuaNumberIsFinite(v) then
            self.sunKelvinHorizon = SkyMath:clamp(v, 1000, 40000)
        end
    end
    if cfg.baseSun then
        local c = cfg.baseSun
        if c.r ~= nil then
            self.baseSun.r = LuaNumber(c.r)
        end
        if c.g ~= nil then
            self.baseSun.g = LuaNumber(c.g)
        end
        if c.b ~= nil then
            self.baseSun.b = LuaNumber(c.b)
        end
    end
    if cfg.moonIntensity ~= nil then
        local v = LuaNumber(cfg.moonIntensity)
        if LuaNumberIsFinite(v) then
            self.moonIntensity = v
        end
    end
    if cfg.moonColor then
        local c = cfg.moonColor
        if c.r ~= nil then
            self.moonColor.r = LuaNumber(c.r)
        end
        if c.g ~= nil then
            self.moonColor.g = LuaNumber(c.g)
        end
        if c.b ~= nil then
            self.moonColor.b = LuaNumber(c.b)
        end
    end
    if cfg.daySwitch ~= nil then
        local v = LuaNumber(cfg.daySwitch)
        if LuaNumberIsFinite(v) then
            self.daySwitch = SkyMath:clamp(v, 0.01, 0.99)
        end
    end
    if cfg.dayOn ~= nil then
        local v = LuaNumber(cfg.dayOn)
        if LuaNumberIsFinite(v) then
            self.dayOn = SkyMath:clamp(v, 0.01, 0.99)
        end
    end
    if cfg.dayOff ~= nil then
        local v = LuaNumber(cfg.dayOff)
        if LuaNumberIsFinite(v) then
            self.dayOff = SkyMath:clamp(v, 0.01, 0.99)
        end
    end
    if cfg.dayOn == nil and cfg.dayOff == nil then
        local band = 0.02
        self.dayOn = SkyMath:clamp(self.daySwitch + band, 0.01, 0.99)
        self.dayOff = SkyMath:clamp(self.daySwitch - band, 0.01, 0.99)
    end
    if cfg.dayCurve then
        local dc = cfg.dayCurve
        if dc.horizonOffset ~= nil then
            local v = LuaNumber(dc.horizonOffset)
            if LuaNumberIsFinite(v) then
                self.dayCurve.horizonOffset = v
            end
        end
        if dc.horizonScale ~= nil then
            local v = LuaNumber(dc.horizonScale)
            if LuaNumberIsFinite(v) and v > 0.01 then
                self.dayCurve.horizonScale = v
            end
        end
        if dc.dayStart ~= nil then
            local v = LuaNumber(dc.dayStart)
            if LuaNumberIsFinite(v) then
                self.dayCurve.dayStart = SkyMath:clamp(v, 0, 1)
            end
        end
        if dc.dayEnd ~= nil then
            local v = LuaNumber(dc.dayEnd)
            if LuaNumberIsFinite(v) then
                self.dayCurve.dayEnd = SkyMath:clamp(v, 0, 1)
            end
        end
    end
end
function CelestialModel.prototype.evaluate(self, time01)
    local phase = SkyMath:wrap(
        LuaNumber(time01),
        0,
        1
    )
    local alt = math.sin(phase * math.pi * 2 - math.pi * 0.5)
    local altitude = SkyMath:lerp(-0.25, 1.05, (alt + 1) * 0.5) * (math.pi / 2) - math.pi / 2 * 0.15
    local azimuth = phase * math.pi * 2 + SkyMath:degToRad(self.azimuthDeg)
    local sunPosDir = SkyMath:dirFromAltAz(altitude, azimuth)
    local sunRayDir = {x = -sunPosDir.x, y = -sunPosDir.y, z = -sunPosDir.z}
    local above = SkyMath:clamp((sunPosDir.y + self.dayCurve.horizonOffset) / self.dayCurve.horizonScale, 0, 1)
    local ds = self.dayCurve.dayStart
    local de = math.max(ds + 0.000001, self.dayCurve.dayEnd)
    local dayFactor = SkyMath:smoothstep(ds, de, above)
    local nightFactor = 1 - dayFactor
    local noonBoost = SkyMath:smoothstep(0.25, 1, above)
    local sunIntensity = math.max(
        0,
        SkyMath:lerp(self.sunNightIntensity, self.sunDayIntensity, dayFactor) * SkyMath:lerp(0.55, 1, noonBoost)
    )
    local kelT = SkyMath:smoothstep(0, 0.65, above)
    local kelvin = SkyMath:lerp(self.sunKelvinHorizon, self.sunKelvinNoon, kelT)
    local bb = SkyMath:kelvinToRgb01(kelvin)
    local sunColor = {r = bb.r * self.baseSun.r, g = bb.g * self.baseSun.g, b = bb.b * self.baseSun.b}
    local moonRayDir = {x = -sunRayDir.x, y = -sunRayDir.y, z = -sunRayDir.z}
    if self._primary == "sun" then
        if dayFactor < self.dayOff then
            self._primary = "moon"
        end
    else
        if dayFactor > self.dayOn then
            self._primary = "sun"
        end
    end
    local isDay = self._primary == "sun"
    local isNight = not isDay
    return {
        time01 = phase,
        dayFactor = dayFactor,
        nightFactor = nightFactor,
        isDay = isDay,
        isNight = isNight,
        primary = self._primary,
        sun = {rayDir = sunRayDir, color = sunColor, intensity = sunIntensity},
        moon = {
            rayDir = moonRayDir,
            color = {r = self.moonColor.r, g = self.moonColor.g, b = self.moonColor.b},
            intensity = self.moonIntensity * SkyMath:smoothstep(0.15, 0.95, nightFactor)
        }
    }
end
M = CelestialModel

return M
