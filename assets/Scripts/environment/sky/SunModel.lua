local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
SkyMath = require("./SkyMath.lua")
SunModel = LuaClass()
SunModel.name = "SunModel"
function SunModel.prototype.lua_constructor(self)
    self.azimuthDeg = 35
    self.nightIntensity = 0.02
    self.dayIntensity = 1.35
    self.sunsetWarmth = 0.35
    self.baseSun = {r = 1, g = 0.98, b = 0.9}
    self.dayStart = 0.02
    self.dayFull = 0.25
    self.twilightStart = -0.08
    self.twilightEnd = 0.1
end
function SunModel.prototype.applyCfg(self, cfg)
    if not cfg then
        return
    end
    local az = LuaNumber(cfg.azimuthDeg)
    if LuaNumberIsFinite(az) then
        self.azimuthDeg = az
    end
    local ni = LuaNumber(cfg.nightIntensity)
    if LuaNumberIsFinite(ni) then
        self.nightIntensity = ni
    end
    local di = LuaNumber(cfg.dayIntensity)
    if LuaNumberIsFinite(di) then
        self.dayIntensity = di
    end
    local sw = LuaNumber(cfg.sunsetWarmth)
    if LuaNumberIsFinite(sw) then
        self.sunsetWarmth = sw
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
    if cfg.dayStart ~= nil then
        local v = LuaNumber(cfg.dayStart)
        if LuaNumberIsFinite(v) then
            self.dayStart = v
        end
    end
    if cfg.dayFull ~= nil then
        local v = LuaNumber(cfg.dayFull)
        if LuaNumberIsFinite(v) then
            self.dayFull = v
        end
    end
    if cfg.twilightStart ~= nil then
        local v = LuaNumber(cfg.twilightStart)
        if LuaNumberIsFinite(v) then
            self.twilightStart = v
        end
    end
    if cfg.twilightEnd ~= nil then
        local v = LuaNumber(cfg.twilightEnd)
        if LuaNumberIsFinite(v) then
            self.twilightEnd = v
        end
    end
end
function SunModel.prototype.evaluate(self, time01)
    local phase = SkyMath:wrap(
        LuaNumber(time01),
        0,
        1
    )
    local altSin = math.sin(phase * math.pi * 2 - math.pi * 0.5)
    local altitude = SkyMath:lerp(-0.25, 1.05, (altSin + 1) * 0.5) * (math.pi / 2) - math.pi / 2 * 0.15
    local azimuth = phase * math.pi * 2 + SkyMath:degToRad(self.azimuthDeg)
    local sunPosDir = SkyMath:dirFromAltAz(altitude, azimuth)
    local above = SkyMath:clamp((sunPosDir.y + 0.02) / 0.45, 0, 1)
    local ds0 = self.dayStart
    local ds1 = self.dayFull
    local dayFactor = SkyMath:smoothstep(
        math.min(ds0, ds1),
        math.max(ds0, ds1),
        above
    )
    local tw0 = self.twilightStart
    local tw1 = self.twilightEnd
    local twilight = SkyMath:smoothstep(
        math.min(tw0, tw1),
        math.max(tw0, tw1),
        sunPosDir.y
    )
    local noonBoost = SkyMath:smoothstep(0.25, 1, above)
    local intensity = math.max(
        0,
        SkyMath:lerp(self.nightIntensity, self.dayIntensity, dayFactor) * SkyMath:lerp(0.55, 1, noonBoost)
    )
    local horizonWarm = SkyMath:smoothstep(0, 0.18, 1 - above) * dayFactor
    local warm = self.sunsetWarmth * horizonWarm
    local r = SkyMath:lerp(self.baseSun.r, 1.15, warm)
    local g = SkyMath:lerp(self.baseSun.g, 0.92, warm)
    local b = SkyMath:lerp(self.baseSun.b, 0.65, warm)
    local rayDir = {x = -sunPosDir.x, y = -sunPosDir.y, z = -sunPosDir.z}
    return {
        dayFactor = dayFactor,
        twilight = twilight,
        above = above,
        altitude = altitude,
        isDay = dayFactor > 0.08,
        sunPosDir = sunPosDir,
        rayDir = rayDir,
        color = {r = r, g = g, b = b},
        intensity = intensity
    }
end
M = SunModel

return M
