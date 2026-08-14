local M = {}
local luaRuntime = require("@builtin/lua_runtime")
local LuaClass = luaRuntime.LuaClass
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaNumberToFixed = luaRuntime.LuaNumberToFixed
local LuaDefineProperty = luaRuntime.LuaDefineProperty
SkyMath = require("./SkyMath.lua")
SkyClock = LuaClass()
SkyClock.name = "SkyClock"
function SkyClock.prototype.lua_constructor(self)
    self.enabled = true
    self.dayLengthSec = 1200
    self.t = 0
    self._startApplied = false
    self._dbgPhase = -1
end
function SkyClock.prototype.applyCfg(self, cfg)
    if not cfg then
        return
    end
    if cfg.enabled == true then
        self.enabled = true
    end
    if cfg.enabled == false then
        self.enabled = false
    end
    local dls = LuaNumber(cfg.dayLengthSec)
    if LuaNumberIsFinite(dls) and dls > 1 then
        self.dayLengthSec = dls
    end
    if not self._startApplied and cfg.startTime01 ~= nil then
        local t01 = SkyMath:wrap(
            LuaNumber(cfg.startTime01),
            0,
            1
        )
        self.t = self.dayLengthSec * t01
        self._startApplied = true
    end
    if ENGINE and ENGINE.log and ENGINE.log.debug then
        ENGINE.log:debug((((("[sky][clock] applyCfg enabled=" .. tostring(self.enabled)) .. " dayLengthSec=") .. tostring(self.dayLengthSec)) .. " startApplied=") .. tostring(self._startApplied))
    end
end
function SkyClock.prototype.step(self, dt)
    if not self.enabled then
        return
    end
    local lua_Number_isFinite_result_0
    if LuaNumberIsFinite(LuaNumber(dt)) then
        lua_Number_isFinite_result_0 = LuaNumber(dt)
    else
        lua_Number_isFinite_result_0 = 0
    end
    local d = lua_Number_isFinite_result_0
    self.t = self.t + d
    if self.dayLengthSec > 0 then
        while self.t > self.dayLengthSec do
            self.t = self.t - self.dayLengthSec
        end
        while self.t < 0 do
            self.t = self.t + self.dayLengthSec
        end
    end
    local phase = math.floor(self.time01 * 8)
    if phase ~= self._dbgPhase then
        self._dbgPhase = phase
        if ENGINE and ENGINE.log and ENGINE.log.debug then
            ENGINE.log:debug((("[sky][clock] phase=" .. tostring(phase)) .. " time01=") .. LuaNumberToFixed(self.time01, 4))
        end
    end
end
function SkyClock.prototype.setTime01(self, time01)
    local t01 = SkyMath:wrap(
        LuaNumber(time01),
        0,
        1
    )
    self.t = self.dayLengthSec * t01
    if ENGINE and ENGINE.log and ENGINE.log.debug then
        ENGINE.log:debug((("[sky][clock] setTime01=" .. tostring(LuaNumberToFixed(t01, 4))) .. " tSec=") .. LuaNumberToFixed(self.t, 3))
    end
end
function SkyClock.prototype.setTimeSec(self, timeSec)
    local timeValue = LuaNumber(timeSec)
    if not LuaNumberIsFinite(timeValue) then
        return
    end
    self.t = timeValue
    if self.dayLengthSec > 0 then
        while self.t > self.dayLengthSec do
            self.t = self.t - self.dayLengthSec
        end
        while self.t < 0 do
            self.t = self.t + self.dayLengthSec
        end
    end
    if ENGINE and ENGINE.log and ENGINE.log.debug then
        ENGINE.log:debug((("[sky][clock] setTimeSec=" .. LuaNumberToFixed(timeValue, 3)) .. " time01=") .. LuaNumberToFixed(self.time01, 4))
    end
end
LuaDefineProperty(
    SkyClock.prototype,
    "time01",
    {get = function(self)
        local lua_temp_1
        if self.dayLengthSec > 0 then
            lua_temp_1 = self.t / self.dayLengthSec
        else
            lua_temp_1 = 0
        end
        return lua_temp_1
    end},
    true
)
M = SkyClock

return M
