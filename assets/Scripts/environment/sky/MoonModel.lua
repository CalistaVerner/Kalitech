local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
SkyMath = require("./SkyMath.lua")
MoonModel = LuaClass()
MoonModel.name = "MoonModel"
function MoonModel.prototype.lua_constructor(self)
    self.phaseOffset01 = 0.5
    self.nightIntensity = 0.18
    self.dayIntensity = 0.01
    self.baseMoon = {r = 0.45, g = 0.55, b = 0.85}
    self.azimuthDeg = -20
end
function MoonModel.prototype.applyCfg(self, cfg)
    if not cfg then
        return
    end
    if cfg.phaseOffset01 ~= nil then
        local v = LuaNumber(cfg.phaseOffset01)
        if LuaNumberIsFinite(v) then
            self.phaseOffset01 = v
        end
    end
    if cfg.nightIntensity ~= nil then
        local v = LuaNumber(cfg.nightIntensity)
        if LuaNumberIsFinite(v) then
            self.nightIntensity = math.max(0, v)
        end
    end
    if cfg.dayIntensity ~= nil then
        local v = LuaNumber(cfg.dayIntensity)
        if LuaNumberIsFinite(v) then
            self.dayIntensity = math.max(0, v)
        end
    end
    if cfg.azimuthDeg ~= nil then
        local v = LuaNumber(cfg.azimuthDeg)
        if LuaNumberIsFinite(v) then
            self.azimuthDeg = v
        end
    end
    if cfg.baseMoon then
        local c = cfg.baseMoon
        if c.r ~= nil then
            self.baseMoon.r = LuaNumber(c.r)
        end
        if c.g ~= nil then
            self.baseMoon.g = LuaNumber(c.g)
        end
        if c.b ~= nil then
            self.baseMoon.b = LuaNumber(c.b)
        end
    end
end
function MoonModel.prototype.evaluate(self, time01, sunEval)
    local phase = SkyMath:wrap(
        (LuaNumber(time01) or 0) + self.phaseOffset01,
        0,
        1
    )
    local altSin = math.sin(phase * math.pi * 2 - math.pi * 0.5)
    local altitude = SkyMath:lerp(-0.2, 0.95, (altSin + 1) * 0.5) * (math.pi / 2) - math.pi / 2 * 0.1
    local azimuth = phase * math.pi * 2 + SkyMath:degToRad(self.azimuthDeg)
    local moonPosDir = SkyMath:dirFromAltAz(altitude, azimuth)
    local lua_sunEval_0
    if sunEval then
        lua_sunEval_0 = sunEval.dayFactor or 0
    else
        lua_sunEval_0 = 0
    end
    local dayFactor = lua_sunEval_0
    local nightFactor = 1 - SkyMath:clamp(dayFactor, 0, 1)
    local intensity = SkyMath:lerp(self.dayIntensity, self.nightIntensity, nightFactor)
    local lua_sunEval_1
    if sunEval then
        lua_sunEval_1 = sunEval.twilight or 0
    else
        lua_sunEval_1 = 0
    end
    local tw = lua_sunEval_1
    local twTint = SkyMath:clamp(1 - tw, 0, 1)
    local r = SkyMath:lerp(self.baseMoon.r, 0.55, 0.2 * twTint)
    local g = SkyMath:lerp(self.baseMoon.g, 0.6, 0.2 * twTint)
    local b = SkyMath:lerp(self.baseMoon.b, 0.92, 0.35 * twTint)
    local rayDir = {x = -moonPosDir.x, y = -moonPosDir.y, z = -moonPosDir.z}
    return {moonPosDir = moonPosDir, rayDir = rayDir, color = {r = r, g = g, b = b}, intensity = intensity}
end
M = MoonModel

return M
