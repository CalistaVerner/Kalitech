local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaClass = luaRuntime.LuaClass
local LuaNumberToFixed = luaRuntime.LuaNumberToFixed
local LuaArrayJoin = luaRuntime.LuaArrayJoin
function req(self, v, msg)
    if v == nil then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
    return v
end
function clamp(self, x, a, b)
    x = LuaNumber(x)
    if not LuaNumberIsFinite(x) then
        return a
    end
    local lua_temp_1
    if x < a then
        lua_temp_1 = a
    else
        local lua_temp_0
        if x > b then
            lua_temp_0 = b
        else
            lua_temp_0 = x
        end
        lua_temp_1 = lua_temp_0
    end
    return lua_temp_1
end
function lerp(self, a, b, t)
    return a + (b - a) * t
end
FogController = LuaClass()
FogController.name = "FogController"
function FogController.prototype.lua_constructor(self)
    self.day = {
        r = 0.7,
        g = 0.78,
        b = 0.9,
        density = 0.006,
        distance = 260
    }
    self.night = {
        r = 0.06,
        g = 0.08,
        b = 0.12,
        density = 0.01,
        distance = 140
    }
    self._cfgRef = nil
    self._render = nil
    self._lastKey = ""
end
function FogController.prototype.init(self, render)
    self._render = req(_G, render, "[sky][fog] render is required")
end
function FogController.prototype.destroy(self)
end
function FogController.prototype.applyCfg(self, cfg)
    if cfg == self._cfgRef then
        return
    end
    self._cfgRef = cfg
    if cfg.fogDay then
        local f = cfg.fogDay
        if f.r ~= nil then
            self.day.r = LuaNumber(f.r)
        end
        if f.g ~= nil then
            self.day.g = LuaNumber(f.g)
        end
        if f.b ~= nil then
            self.day.b = LuaNumber(f.b)
        end
        if f.density ~= nil then
            self.day.density = LuaNumber(f.density)
        end
        if f.distance ~= nil then
            self.day.distance = LuaNumber(f.distance)
        end
    end
    if cfg.fogNight then
        local f = cfg.fogNight
        if f.r ~= nil then
            self.night.r = LuaNumber(f.r)
        end
        if f.g ~= nil then
            self.night.g = LuaNumber(f.g)
        end
        if f.b ~= nil then
            self.night.b = LuaNumber(f.b)
        end
        if f.density ~= nil then
            self.night.density = LuaNumber(f.density)
        end
        if f.distance ~= nil then
            self.night.distance = LuaNumber(f.distance)
        end
    end
end
function FogController.prototype.update(self, render, cel)
    render = req(_G, render, "[sky][fog] render is required")
    req(
        _G,
        cel and KTypeOf(cel.dayFactor) == "number",
        "[sky][fog] cel.dayFactor is required"
    )
    local df = clamp(_G, cel.dayFactor, 0, 1)
    local r = lerp(_G, self.night.r, self.day.r, df)
    local g = lerp(_G, self.night.g, self.day.g, df)
    local b = lerp(_G, self.night.b, self.day.b, df)
    local density = clamp(
        _G,
        lerp(_G, self.night.density, self.day.density, df),
        0,
        0.03
    )
    local distance = math.max(
        25,
        lerp(_G, self.night.distance, self.day.distance, df)
    )
    local key = LuaArrayJoin(
        {
            LuaNumberToFixed(r, 4),
            LuaNumberToFixed(g, 4),
            LuaNumberToFixed(b, 4),
            LuaNumberToFixed(density, 6),
            LuaNumberToFixed(distance, 2)
        },
        "|"
    )
    if key == self._lastKey then
        return
    end
    self._lastKey = key
    render:fogCfg({color = {r = r, g = g, b = b}, density = density, distance = distance})
end
M = FogController

return M
