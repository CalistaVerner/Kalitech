local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaClass = luaRuntime.LuaClass
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaNumberToFixed = luaRuntime.LuaNumberToFixed
SkyMath = require("./SkyMath.lua")
function req(self, v, msg)
    if v == nil then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
    return v
end
function clamp01(self, x)
    return math.max(
        0,
        math.min(1, x)
    )
end
function lerpColor(self, a, b, t)
    return {
        r = SkyMath:lerp(a.r, b.r, t),
        g = SkyMath:lerp(a.g, b.g, t),
        b = SkyMath:lerp(a.b, b.b, t)
    }
end
SkyDome = LuaClass()
SkyDome.name = "SkyDome"
function SkyDome.prototype.lua_constructor(self)
    self.zenithDay = {r = 0.08, g = 0.14, b = 0.3}
    self.horizonDay = {r = 0.65, g = 0.72, b = 0.82}
    self.zenithNight = {r = 0.01, g = 0.02, b = 0.06}
    self.horizonNight = {r = 0.03, g = 0.04, b = 0.08}
    self.hazeDay = 0.6
    self.hazeNight = 0.28
    self.sunDisk = 45
    self.moonDisk = 120
    self.exposureDay = 1.1
    self.exposureNight = 0.55
    self.twilightWarmth = 0.22
    self.twilightHazeBoost = 0.1
    self.twilightExposureBoost = 0.1
    self.texA = nil
    self.texB = nil
    self.texBlendDay = 0.55
    self.texBlendNight = 0.35
    self.texExposureDay = 1.8
    self.texExposureNight = 0.65
    self.crossfade = {enabled = true, start = 0.1, ["end"] = 0.35}
    self._lastTexA = ""
    self._lastTexB = ""
    self._lastKey = ""
end
function SkyDome.prototype.applyCfg(self, cfg)
    if not cfg then
        return
    end
    if cfg.skyDomeTexA ~= nil then
        self.texA = tostring(cfg.skyDomeTexA)
    end
    if cfg.skyDomeTexB ~= nil then
        self.texB = tostring(cfg.skyDomeTexB)
    end
    if cfg.skyDomeTexDay ~= nil then
        self.texA = tostring(cfg.skyDomeTexDay)
    end
    if cfg.skyDomeTexNight ~= nil then
        self.texB = tostring(cfg.skyDomeTexNight)
    end
    if cfg.skyDome then
        local d = cfg.skyDome
        if d.zenithColor then
            local c = d.zenithColor
            if c.r ~= nil then
                self.zenithDay.r = LuaNumber(c.r)
            end
            if c.g ~= nil then
                self.zenithDay.g = LuaNumber(c.g)
            end
            if c.b ~= nil then
                self.zenithDay.b = LuaNumber(c.b)
            end
        end
        if d.horizonColor then
            local c = d.horizonColor
            if c.r ~= nil then
                self.horizonDay.r = LuaNumber(c.r)
            end
            if c.g ~= nil then
                self.horizonDay.g = LuaNumber(c.g)
            end
            if c.b ~= nil then
                self.horizonDay.b = LuaNumber(c.b)
            end
        end
        if d.zenithDay then
            local c = d.zenithDay
            if c.r ~= nil then
                self.zenithDay.r = LuaNumber(c.r)
            end
            if c.g ~= nil then
                self.zenithDay.g = LuaNumber(c.g)
            end
            if c.b ~= nil then
                self.zenithDay.b = LuaNumber(c.b)
            end
        end
        if d.horizonDay then
            local c = d.horizonDay
            if c.r ~= nil then
                self.horizonDay.r = LuaNumber(c.r)
            end
            if c.g ~= nil then
                self.horizonDay.g = LuaNumber(c.g)
            end
            if c.b ~= nil then
                self.horizonDay.b = LuaNumber(c.b)
            end
        end
        if d.zenithNight then
            local c = d.zenithNight
            if c.r ~= nil then
                self.zenithNight.r = LuaNumber(c.r)
            end
            if c.g ~= nil then
                self.zenithNight.g = LuaNumber(c.g)
            end
            if c.b ~= nil then
                self.zenithNight.b = LuaNumber(c.b)
            end
        end
        if d.horizonNight then
            local c = d.horizonNight
            if c.r ~= nil then
                self.horizonNight.r = LuaNumber(c.r)
            end
            if c.g ~= nil then
                self.horizonNight.g = LuaNumber(c.g)
            end
            if c.b ~= nil then
                self.horizonNight.b = LuaNumber(c.b)
            end
        end
        if d.hazeDay ~= nil then
            self.hazeDay = LuaNumber(d.hazeDay)
        end
        if d.hazeNight ~= nil then
            self.hazeNight = LuaNumber(d.hazeNight)
        end
        if d.sunDisk ~= nil then
            self.sunDisk = LuaNumber(d.sunDisk)
        end
        if d.moonDisk ~= nil then
            self.moonDisk = LuaNumber(d.moonDisk)
        end
        if d.exposureDay ~= nil then
            self.exposureDay = LuaNumber(d.exposureDay)
        end
        if d.exposureNight ~= nil then
            self.exposureNight = LuaNumber(d.exposureNight)
        end
        if d.twilightWarmth ~= nil then
            self.twilightWarmth = LuaNumber(d.twilightWarmth)
        end
        if d.twilightHazeBoost ~= nil then
            self.twilightHazeBoost = LuaNumber(d.twilightHazeBoost)
        end
        if d.twilightExposureBoost ~= nil then
            self.twilightExposureBoost = LuaNumber(d.twilightExposureBoost)
        end
        if d.texBlendDay ~= nil then
            self.texBlendDay = LuaNumber(d.texBlendDay)
        end
        if d.texBlendNight ~= nil then
            self.texBlendNight = LuaNumber(d.texBlendNight)
        end
        if d.texExposureDay ~= nil then
            self.texExposureDay = LuaNumber(d.texExposureDay)
        end
        if d.texExposureNight ~= nil then
            self.texExposureNight = LuaNumber(d.texExposureNight)
        end
        if d.crossfade then
            local x = d.crossfade
            if x.enabled ~= nil then
                self.crossfade.enabled = not not x.enabled
            end
            if x.start ~= nil then
                self.crossfade.start = LuaNumber(x.start)
            end
            if x["end"] ~= nil then
                self.crossfade["end"] = LuaNumber(x["end"])
            end
        end
    end
end
function SkyDome.prototype.update(self, render, celEval)
    req(_G, render, "[skydome] render is required")
    req(_G, render.skyDomeCfg, "[skydome] render.skyDomeCfg(cfg) is required")
    req(_G, render.skyDomeTexA, "[skydome] render.skyDomeTexA(asset) is required")
    req(_G, render.skyDomeTexB, "[skydome] render.skyDomeTexB(asset) is required")
    req(
        _G,
        celEval and KTypeOf(celEval.dayFactor) == "number",
        "[skydome] celEval.dayFactor is required"
    )
    local df = clamp01(_G, celEval.dayFactor)
    local lua_clamp01_2 = clamp01
    local lua_G_1 = _G
    local lua_temp_0
    if celEval.twilight ~= nil then
        lua_temp_0 = celEval.twilight
    else
        lua_temp_0 = 0
    end
    local twilight = lua_clamp01_2(lua_G_1, lua_temp_0)
    local s = req(_G, celEval and celEval.sun, "[skydome] celEval.sun is required")
    local m = req(_G, celEval and celEval.moon, "[skydome] celEval.moon is required")
    local a = req(_G, self.texA, "[skydome] skyDomeTexDay/skyDomeTexA is required (day texture)")
    local b = req(_G, self.texB, "[skydome] skyDomeTexNight/skyDomeTexB is required (night texture)")
    if a ~= self._lastTexA then
        render:skyDomeTexA(a)
        self._lastTexA = a
    end
    if b ~= self._lastTexB then
        render:skyDomeTexB(b)
        self._lastTexB = b
    end
    local dayBlend = df
    if self.crossfade.enabled then
        local s0 = LuaNumber(self.crossfade.start)
        local s1 = LuaNumber(self.crossfade["end"])
        local lua_Number_isFinite_result_3
        if LuaNumberIsFinite(s0) then
            lua_Number_isFinite_result_3 = s0
        else
            lua_Number_isFinite_result_3 = 0.1
        end
        local e0 = lua_Number_isFinite_result_3
        local lua_Number_isFinite_result_4
        if LuaNumberIsFinite(s1) then
            lua_Number_isFinite_result_4 = s1
        else
            lua_Number_isFinite_result_4 = 0.35
        end
        local e1 = lua_Number_isFinite_result_4
        dayBlend = SkyMath:smoothstep(
            math.min(e0, e1),
            math.max(e0, e1),
            dayBlend
        )
    end
    local hazeBase = SkyMath:lerp(self.hazeNight, self.hazeDay, dayBlend)
    local exposureBase = SkyMath:lerp(self.exposureNight, self.exposureDay, dayBlend)
    local haze = clamp01(_G, hazeBase + twilight * self.twilightHazeBoost)
    local exposure = math.max(0.05, exposureBase + twilight * self.twilightExposureBoost)
    local texBlend = clamp01(
        _G,
        SkyMath:lerp(self.texBlendNight, self.texBlendDay, dayBlend)
    )
    local texExposure = math.max(
        0.001,
        SkyMath:lerp(self.texExposureNight, self.texExposureDay, dayBlend)
    )
    local skyBlend = 1 - dayBlend
    local zen = lerpColor(_G, self.zenithNight, self.zenithDay, dayBlend)
    local hor = lerpColor(_G, self.horizonNight, self.horizonDay, dayBlend)
    local warm = clamp01(_G, twilight * self.twilightWarmth)
    local horWarm = {
        r = SkyMath:lerp(hor.r, 1.05, warm),
        g = SkyMath:lerp(hor.g, 0.62, warm),
        b = SkyMath:lerp(hor.b, 0.3, warm)
    }
    local key = table.concat({
        LuaNumberToFixed(dayBlend, 4),
        LuaNumberToFixed(twilight, 4),
        LuaNumberToFixed(haze, 4),
        LuaNumberToFixed(exposure, 4),
        LuaNumberToFixed(texBlend, 4),
        LuaNumberToFixed(texExposure, 4),
        LuaNumberToFixed(skyBlend, 4),
        LuaNumberToFixed(self.sunDisk, 2),
        LuaNumberToFixed(self.moonDisk, 2),
        LuaNumberToFixed(s.rayDir.x, 4),
        LuaNumberToFixed(s.rayDir.y, 4),
        LuaNumberToFixed(s.rayDir.z, 4),
        LuaNumberToFixed(m.rayDir.x, 4),
        LuaNumberToFixed(m.rayDir.y, 4),
        LuaNumberToFixed(m.rayDir.z, 4),
        LuaNumberToFixed(s.color.r, 4),
        LuaNumberToFixed(s.color.g, 4),
        LuaNumberToFixed(s.color.b, 4),
        LuaNumberToFixed(LuaNumber(s.intensity), 4),
        LuaNumberToFixed(m.color.r, 4),
        LuaNumberToFixed(m.color.g, 4),
        LuaNumberToFixed(m.color.b, 4),
        LuaNumberToFixed(LuaNumber(m.intensity), 4),
        LuaNumberToFixed(zen.r, 4),
        LuaNumberToFixed(zen.g, 4),
        LuaNumberToFixed(zen.b, 4),
        LuaNumberToFixed(horWarm.r, 4),
        LuaNumberToFixed(horWarm.g, 4),
        LuaNumberToFixed(horWarm.b, 4)
    }, "|")
    if key == self._lastKey then
        return
    end
    self._lastKey = key
    render:skyDomeCfg({
        sunDir = {s.rayDir.x, s.rayDir.y, s.rayDir.z},
        moonDir = {m.rayDir.x, m.rayDir.y, m.rayDir.z},
        sunColor = {s.color.r, s.color.g, s.color.b},
        sunIntensity = LuaNumber(s.intensity),
        moonColor = {m.color.r, m.color.g, m.color.b},
        moonIntensity = LuaNumber(m.intensity),
        zenithColor = {zen.r, zen.g, zen.b},
        horizonColor = {horWarm.r, horWarm.g, horWarm.b},
        haze = haze,
        sunDisk = self.sunDisk,
        moonDisk = self.moonDisk,
        exposure = exposure,
        texBlend = texBlend,
        texExposure = texExposure,
        skyBlend = skyBlend
    })
end
M = SkyDome

return M
