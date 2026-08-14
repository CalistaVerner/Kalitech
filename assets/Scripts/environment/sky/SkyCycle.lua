local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
SkyMath = require("./SkyMath.lua")
SkyCycle = LuaClass()
SkyCycle.name = "SkyCycle"
function SkyCycle.prototype.lua_constructor(self)
    self.time01 = 0.25
    self.dayLengthSec = 1200
    self.timeScale = 1
    self.paused = false
    self._accum = 0
end
function SkyCycle.prototype.applyCfg(self, cfg)
    if not cfg then
        return
    end
    if cfg.time01 ~= nil then
        local t = LuaNumber(cfg.time01)
        if LuaNumberIsFinite(t) then
            self.time01 = SkyMath:wrap(t, 0, 1)
        end
    end
    if cfg.dayLengthSec ~= nil then
        local d = LuaNumber(cfg.dayLengthSec)
        if LuaNumberIsFinite(d) then
            self.dayLengthSec = math.max(1, d)
        end
    end
    if cfg.timeScale ~= nil then
        local s = LuaNumber(cfg.timeScale)
        if LuaNumberIsFinite(s) then
            self.timeScale = s
        end
    end
    if cfg.paused ~= nil then
        self.paused = not not cfg.paused
    end
end
function SkyCycle.prototype.tick(self, tpf)
    local dt = math.max(
        0,
        LuaNumber(tpf) or 0
    )
    if self.paused then
        return self.time01
    end
    local len = math.max(1, self.dayLengthSec)
    local speed = self.timeScale
    self._accum = self._accum + dt * speed
    local add01 = self._accum / len
    if add01 ~= 0 then
        self.time01 = SkyMath:wrap(self.time01 + add01, 0, 1)
        self._accum = 0
    end
    return self.time01
end
M = SkyCycle

return M
