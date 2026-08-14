local M = {}
local json = require("@builtin/json")
local luaRuntime = require("@builtin/lua_runtime")
local Error = luaRuntime.Error
local LuaConstruct = luaRuntime.LuaConstruct
local LuaClass = luaRuntime.LuaClass
local LuaNumber = luaRuntime.LuaNumber
local LuaNumberIsFinite = luaRuntime.LuaNumberIsFinite
local LuaArraySetLength = luaRuntime.LuaArraySetLength
local LuaNumberToFixed = luaRuntime.LuaNumberToFixed
SkyClock = require("./SkyClock.lua")
CelestialModel = require("./CelestialModel.lua")
LightRig = require("./LightRig.lua")
SkyDome = require("./SkyDome.lua")
FogController = require("./FogController.lua")
function req(self, v, msg)
    if v == nil then
        error(
            LuaConstruct(Error, msg),
            0
        )
    end
    return v
end
function isFn(self, f)
    return KTypeOf(f) == "function"
end
function mapGet(self, m, key)
    if not m then
        return nil
    end
    if KTypeOf(m) == "table" and KTypeOf(m.has) == "function" and KTypeOf(m.get) == "function" then
        local lua_m_has_result_0
        if m:has(key) then
            lua_m_has_result_0 = m:get(key)
        else
            lua_m_has_result_0 = nil
        end
        return lua_m_has_result_0
    end
    if KTypeOf(m) == "table" then
        local lua_KFunction_call_result_1
        if KFunction:call(KObject.prototype.hasOwnProperty, m, key) then
            lua_KFunction_call_result_1 = m[key]
        else
            lua_KFunction_call_result_1 = nil
        end
        return lua_KFunction_call_result_1
    end
    return nil
end
function tryGet(self, ctx, key)
    if not ctx then
        return nil
    end
    if KTypeOf(ctx.get) == "function" then
        local v = ctx:get(key)
        if v ~= nil then
            return v
        end
    end
    if KTypeOf(ctx.state) == "function" then
        local st = ctx:state()
        local v = mapGet(_G, st, key)
        if v ~= nil then
            return v
        end
    end
    if ctx.stateDomain then
        local v = mapGet(_G, ctx.stateDomain, key)
        if v ~= nil then
            return v
        end
    end
    return nil
end
SkySystem = LuaClass()
SkySystem.name = "SkySystem"
function SkySystem.prototype.lua_constructor(self, engineApi)
    self.engine = req(_G, engineApi, "[sky] engineApi is required")
    self.render = nil
    self.clock = LuaConstruct(SkyClock)
    self.celestial = LuaConstruct(CelestialModel)
    self.lights = LuaConstruct(LightRig)
    self.skydome = LuaConstruct(SkyDome)
    self._cfgRef = nil
    self._cfgPath = "INIT"
    self._didInitTime = false
    self._eventsWired = false
    self._unsubs = {}
    self._enabled = true
    self._dbgAcc = 0
    self._dbgEvery = 1
    self._dbgLastPrimary = nil
end
function SkySystem.prototype.init(self, ctx)
    if not isFn(_G, self.engine.render) then
        error(
            LuaConstruct(Error, "[sky] engineApi.render() is required (pass ctx.engine.api(), not ctx.engine)"),
            0
        )
    end
    self.render = req(
        _G,
        self.engine:render(),
        "[sky] engine.render() returned null"
    )
    local cfg = self:readCfgStrict(ctx)
    self:applyCfg(cfg)
    self:assertRenderContract()
    if isFn(_G, self.lights.init) then
        self.lights:init(self.engine, self.render)
    end
    if isFn(_G, self.skydome.init) then
        self.skydome:init(self.render)
    end
    if not self._didInitTime then
        if cfg.startTime01 == nil then
            self.clock:setTime01(0.25)
        end
        self._didInitTime = true
    end
    self:applyFrame(0)
    self:wireEventsOnce()
end
function SkySystem.prototype.update(self, ctx, tpf)
    local cfg = self:readCfgStrict(ctx)
    self:applyCfg(cfg)
    local dt = self:getDt(tpf)
    if not self._enabled or not self.clock.enabled then
        return
    end
    self.clock:step(dt)
    self:applyFrame(dt)
    if cfg.debug and cfg.debug.skyEvery ~= nil then
        local v = LuaNumber(cfg.debug.skyEvery)
        if LuaNumberIsFinite(v) and v >= 0 then
            self._dbgEvery = v
        end
    end
    self._dbgAcc = self._dbgAcc + dt
    if self._dbgEvery > 0 and self._dbgAcc >= self._dbgEvery then
        self._dbgAcc = 0
        local cel = self.celestial:evaluate(self.clock.time01)
        local lua_temp_2
        if ENGINE and ENGINE.log then
            lua_temp_2 = ENGINE.log
        else
            lua_temp_2 = nil
        end
        local log = lua_temp_2
        if log and log.debug then
        end
    end
end
function SkySystem.prototype.destroy(self)
    do
        local i = 0
        while i < #self._unsubs do
            local lua_self_3 = self._unsubs
            lua_self_3[i + 1](lua_self_3)
            i = i + 1
        end
    end
    LuaArraySetLength(self._unsubs, 0)
    if isFn(_G, self.lights.destroy) then
        self.lights:destroy()
    end
    if isFn(_G, self.skydome.destroy) then
        self.skydome:destroy()
    end
    local lua_temp_4
    if ENGINE and ENGINE.log then
        lua_temp_4 = ENGINE.log
    else
        lua_temp_4 = console
    end
    lua_temp_4:info("[sky] destroy")
end
function SkySystem.prototype.applyFrame(self, dt)
    local cel = self.celestial:evaluate(self.clock.time01)
    if cel.primary ~= self._dbgLastPrimary then
        self._dbgLastPrimary = cel.primary
        local lua_temp_5
        if ENGINE and ENGINE.log then
            lua_temp_5 = ENGINE.log
        else
            lua_temp_5 = nil
        end
        local log = lua_temp_5
        if log and log.debug then
            log:debug((((("[sky] PRIMARY -> " .. cel.primary) .. " time01=") .. tostring(LuaNumberToFixed(cel.time01, 4))) .. " dayFactor=") .. LuaNumberToFixed(cel.dayFactor, 4))
        end
    end
    if isFn(_G, self.lights.update) then
        self.lights:update(self.engine, self.render, cel, dt)
    end
    if isFn(_G, self.skydome.update) then
        self.skydome:update(self.render, cel)
    end
end
function SkySystem.prototype.applyCfg(self, cfg)
    if cfg == self._cfgRef then
        return
    end
    self._cfgRef = cfg
    self.clock:applyCfg(cfg)
    self.celestial:applyCfg(cfg)
    if isFn(_G, self.lights.applyCfg) then
        self.lights:applyCfg(cfg)
    end
    if isFn(_G, self.skydome.applyCfg) then
        self.skydome:applyCfg(cfg)
    end
    local lua_temp_6
    if ENGINE and ENGINE.log then
        lua_temp_6 = ENGINE.log
    else
        lua_temp_6 = nil
    end
    local log = lua_temp_6
    if log and log.debug then
        log:debug("[sky][cfg] applied path=" .. self._cfgPath)
    end
end
function SkySystem.prototype.readCfgStrict(self, ctx)
    if not ctx then
        self._cfgPath = "ctx:null"
        error(
            LuaConstruct(Error, "[sky][cfg] ctx is null"),
            0
        )
    end
    local keys = {"config"}
    do
        local i = 0
        while i < #keys do
            local k = keys[i + 1]
            local v = tryGet(_G, ctx, k)
            if v ~= nil then
                self._cfgPath = "domain:" .. k
                return v
            end
            i = i + 1
        end
    end
    self._cfgPath = "NOT_FOUND"
    error(
        LuaConstruct(Error, "[sky][cfg] config not found in ctx (expected fields/domains). cfgPath=NOT_FOUND"),
        0
    )
end
function SkySystem.prototype.getDt(self, tpf)
    local p = LuaNumber(tpf)
    if LuaNumberIsFinite(p) and p > 0 then
        return p
    end
    return 1 / 60
end
function SkySystem.prototype.assertRenderContract(self)
    local r = req(_G, self.render, "[sky] render is required")
    req(_G, r.ensureScene, "[sky] render.ensureScene() is required")
    req(_G, r.sunCfg, "[sky] render.sunCfg(cfg) is required")
    req(_G, r.moonCfg, "[sky] render.moonCfg(cfg) is required (AAA)")
    req(_G, r.ambientCfg, "[sky] render.ambientCfg(cfg) is required")
    req(_G, r.fogCfg, "[sky] render.fogCfg(cfg) is required")
    req(_G, r.skyDomeCfg, "[sky] render.skyDomeCfg(cfg) is required (AAA)")
    req(_G, r.skyDomeTexA, "[sky] render.skyDomeTexA(asset) is required (AAA)")
    req(_G, r.skyDomeTexB, "[sky] render.skyDomeTexB(asset) is required (AAA)")
    if not isFn(_G, r.sunShadowsCfg) then
        error(
            LuaConstruct(Error, "[sky] render.sunShadowsCfg(cfg) is required (AAA). Use the strict table-based API."),
            0
        )
    end
    req(_G, r.setPrimaryDirectional, "[sky] render.setPrimaryDirectional('sun'|'moon') is required (AAA)")
    r:ensureScene()
    local lua_temp_7
    if ENGINE and ENGINE.log then
        lua_temp_7 = ENGINE.log
    else
        lua_temp_7 = nil
    end
    local log = lua_temp_7
    if log and log.debug then
        log:debug("[sky] render contract OK (AAA SkyDome + sun/moon + primary + shadows)")
    end
end
function SkySystem.prototype.wireEventsOnce(self)
    if self._eventsWired then
        return
    end
    self._eventsWired = true
    local lua_temp_8
    if ENGINE and ENGINE.log then
        lua_temp_8 = ENGINE.log
    else
        lua_temp_8 = nil
    end
    local log = lua_temp_8
    local lua_temp_9
    if KTypeOf(_G) ~= "nil" then
        lua_temp_9 = ENGINE.events
    else
        lua_temp_9 = nil
    end
    local ev = lua_temp_9
    if not ev then
        if log and log.debug then
            log:debug("[sky] ENGINE.events not present")
        end
        return
    end
    if not isFn(_G, ev.on) then
        error(
            LuaConstruct(Error, "[sky] ENGINE.events.on(name, fn) is required"),
            0
        )
    end
    local function on(lua_, name, fn)
        local ret = ev:on(name, fn)
        if isFn(_G, ret) then
            local lua_self__unsubs_10 = self._unsubs
            lua_self__unsubs_10[#lua_self__unsubs_10 + 1] = ret
        elseif isFn(_G, ev.off) then
            local lua_self__unsubs_11 = self._unsubs
            lua_self__unsubs_11[#lua_self__unsubs_11 + 1] = function() return ev:off(name, fn) end
        end
    end
    on(
        _G,
        "sky:setTime",
        function(lua_, p)
            if not p then
                return
            end
            if p.time01 ~= nil then
                self.clock:setTime01(LuaNumber(p.time01))
            elseif p.timeSec ~= nil then
                self.clock:setTimeSec(LuaNumber(p.timeSec))
            end
            if p.dayLengthSec ~= nil then
                local dls = LuaNumber(p.dayLengthSec)
                if LuaNumberIsFinite(dls) and dls > 1 then
                    self.clock.dayLengthSec = dls
                end
            end
            if log and log.debug then
                log:debug("[sky][event] sky:setTime " .. json:encode(p))
            end
            self:applyFrame(0)
        end
    )
    on(
        _G,
        "sky:setEnabled",
        function(lua_, p)
            if not p then
                return
            end
            if p.enabled == true then
                self:setEnabled(true)
            end
            if p.enabled == false then
                self:setEnabled(false)
            end
            if log and log.debug then
                log:debug("[sky][event] sky:setEnabled enabled=" .. tostring(self._enabled))
            end
        end
    )
    on(
        _G,
        "sky:setSpeed",
        function(lua_, p)
            if not p then
                return
            end
            local dls = LuaNumber(p.dayLengthSec)
            if LuaNumberIsFinite(dls) and dls > 1 then
                self.clock.dayLengthSec = dls
            end
            if log and log.debug then
                log:debug("[sky][event] sky:setSpeed dayLengthSec=" .. tostring(self.clock.dayLengthSec))
            end
        end
    )
    if log and log.debug then
        log:debug("[sky] ENGINE.events wired")
    end
end
function SkySystem.prototype.setEnabled(self, v)
    self._enabled = not not v
    if isFn(_G, self.lights.setEnabled) then
        self.lights:setEnabled(self._enabled)
    end
    local lua_temp_12
    if ENGINE and ENGINE.log then
        lua_temp_12 = ENGINE.log
    else
        lua_temp_12 = nil
    end
    local log = lua_temp_12
    if log and log.debug then
        log:debug("[sky] setEnabled=" .. tostring(self._enabled))
    end
end
M = SkySystem

return M
