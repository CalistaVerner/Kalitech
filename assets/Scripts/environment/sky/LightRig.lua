local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaClass = luaRuntime.LuaClass
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
    x = LuaNumber(x)
    if not LuaNumberIsFinite(x) then
        return 0
    end
    return x < 0 and 0 or (x > 1 and 1 or x)
end
function lerp(self, a, b, t)
    return a + (b - a) * t
end
LightRig = LuaClass()
LightRig.name = "LightRig"
function LightRig.prototype.lua_constructor(self)
    self._enabled = true
    self.ambientDay = {r = 0.22, g = 0.25, b = 0.3, intensity = 1}
    self.ambientNight = {r = 0.04, g = 0.06, b = 0.1, intensity = 0.75}
    self.shadows = {
        mapSize = 2048,
        splits = 3,
        lambda = 0.65,
        intensity = 0.65,
        snap = true
    }
    self._render = nil
    self._lastPrimary = nil
    self._lastShadowKey = ""
    self._cfgRef = nil
end
function LightRig.prototype.init(self, engine, render)
    req(_G, engine, "[sky][light] engine is required")
    self._render = req(_G, render, "[sky][light] render is required")
end
function LightRig.prototype.destroy(self)
end
function LightRig.prototype.setEnabled(self, v)
    self._enabled = not not v
end
function LightRig.prototype.applyCfg(self, cfg)
    if cfg == self._cfgRef then
        return
    end
    self._cfgRef = cfg
    if cfg.ambientDay then
        local a = cfg.ambientDay
        if a.r ~= nil then
            self.ambientDay.r = LuaNumber(a.r)
        end
        if a.g ~= nil then
            self.ambientDay.g = LuaNumber(a.g)
        end
        if a.b ~= nil then
            self.ambientDay.b = LuaNumber(a.b)
        end
        if a.intensity ~= nil then
            self.ambientDay.intensity = LuaNumber(a.intensity)
        end
    end
    if cfg.ambientNight then
        local a = cfg.ambientNight
        if a.r ~= nil then
            self.ambientNight.r = LuaNumber(a.r)
        end
        if a.g ~= nil then
            self.ambientNight.g = LuaNumber(a.g)
        end
        if a.b ~= nil then
            self.ambientNight.b = LuaNumber(a.b)
        end
        if a.intensity ~= nil then
            self.ambientNight.intensity = LuaNumber(a.intensity)
        end
    end
    if cfg.shadows then
        local s = cfg.shadows
        if s.mapSize ~= nil then
            self.shadows.mapSize = bit32.bor(s.mapSize, 0)
        end
        if s.splits ~= nil then
            self.shadows.splits = bit32.bor(s.splits, 0)
        end
        if s.lambda ~= nil then
            self.shadows.lambda = LuaNumber(s.lambda)
        end
        if s.intensity ~= nil then
            self.shadows.intensity = LuaNumber(s.intensity)
        end
        if s.snap ~= nil then
            self.shadows.snap = not not s.snap
        end
    end
end
function LightRig.prototype.update(self, engine, render, cel, dt)
    if not self._enabled then
        return
    end
    req(_G, render, "[sky][light] render is required")
    req(_G, cel, "[sky][light] cel is required")
    req(_G, cel.sun and cel.sun.rayDir, "[sky][light] cel.sun.rayDir is required")
    req(_G, cel.moon and cel.moon.rayDir, "[sky][light] cel.moon.rayDir is required")
    local primary = tostring(cel.primary)
    if primary ~= self._lastPrimary then
        self._lastPrimary = primary
        render:setPrimaryDirectional(primary)
    end
    render:sunCfg({
        dir = {cel.sun.rayDir.x, cel.sun.rayDir.y, cel.sun.rayDir.z},
        color = {cel.sun.color.r, cel.sun.color.g, cel.sun.color.b},
        intensity = LuaNumber(cel.sun.intensity)
    })
    render:moonCfg({
        dir = {cel.moon.rayDir.x, cel.moon.rayDir.y, cel.moon.rayDir.z},
        color = {cel.moon.color.r, cel.moon.color.g, cel.moon.color.b},
        intensity = LuaNumber(cel.moon.intensity)
    })
    local df = clamp01(_G, cel.dayFactor)
    local ar = lerp(_G, self.ambientNight.r, self.ambientDay.r, df)
    local ag = lerp(_G, self.ambientNight.g, self.ambientDay.g, df)
    local ab = lerp(_G, self.ambientNight.b, self.ambientDay.b, df)
    local ai = math.max(
        0,
        lerp(_G, self.ambientNight.intensity, self.ambientDay.intensity, df)
    )
    render:ambientCfg({color = {r = ar, g = ag, b = ab}, intensity = ai})
    local s = self.shadows
    local key = table.concat(
        {
            bit32.bor(s.mapSize, 0),
            bit32.bor(s.splits, 0),
            LuaNumberToFixed(s.lambda, 4),
            LuaNumberToFixed(s.intensity, 4),
            s.snap and 1 or 0
        },
        "|"
    )
    if key ~= self._lastShadowKey then
        self._lastShadowKey = key
        render:sunShadowsCfg({shadows = {
            mapSize = 4096,
            splits = 4,
            lambda = 0.72,
            intensity = 0.75,
            snap = true,
            snapFirstCascades = 2,
            extentsPadding = 1.02,
            pipeline = {
                {type = "hysteresis", cfg = {hysteresis = 12, smoothing = 0.12}},
                {type = "basis"},
                {type = "onlySplit", cfg = {split = 0, inner = {type = "stableFit", cfg = {xyPadding = 1.06, forceSquare = true}}}},
                {type = "onlySplit", cfg = {minSplit = 1, maxSplit = 7, inner = {type = "tightFit", cfg = {
                    xyPadding = 1.02,
                    nearTierTexels = 512,
                    nearShrinkHysteresisTiers = 3,
                    nearGrowHysteresisTiers = 1,
                    maxNearGrowPerUpdate = 0.15
                }}}},
                {type = "poissonPcf", cfg = {
                    enabled = true,
                    samples = 16,
                    baseRadiusTexels = 1.35,
                    split0 = 0.95,
                    split1 = 1.1,
                    split2 = 1.35,
                    split3 = 1.65,
                    rotateKernel = true,
                    rotateEveryFrames = 12,
                    rotateOnlyOnCameraEvent = true,
                    camMoveEventThreshold = 0.03,
                    camRotateEventThresholdDeg = 0.2
                }},
                {type = "temporalGate", cfg = {minRotateDeg = 0.25, minMoveTexels = 1.25, teleportMoveTexels = 24}},
                {type = "texelSnap", cfg = {enabled = true, snapFirstCascades = 2}}
            }
        }})
    end
end
M = LightRig

return M
